// Copyright (C) 2013 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.googlesource.gerrit.plugins.deleteproject.fs;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;

import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.RepositoryCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gerrit.extensions.annotations.PluginName;
import com.google.gerrit.extensions.api.changes.NotifyHandling;
import com.google.gerrit.extensions.events.ProjectDeletedListener;
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.config.PluginConfigFactory;
import com.google.gerrit.server.config.SitePaths;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.project.ProjectResource;
import com.google.inject.Inject;

import com.googlesource.gerrit.plugins.deleteproject.CannotDeleteProjectException;

public class FilesystemDeleteHandler {
  private static final Logger log = LoggerFactory
      .getLogger(FilesystemDeleteHandler.class);

  private final Path gitDir;
  private final GitRepositoryManager repoManager;
  private final DynamicSet<ProjectDeletedListener> deletedListener;
  private final PluginConfigFactory cfgFactory;
  private final String pluginName;

  @Inject
  public FilesystemDeleteHandler(GitRepositoryManager repoManager,
      SitePaths site,
      @GerritServerConfig Config cfg,
      DynamicSet<ProjectDeletedListener> deletedListener,
      PluginConfigFactory cfgFactory,
      @PluginName String pluginName) {
    gitDir = site.resolve(cfg.getString("gerrit", null, "basePath"));
    this.repoManager = repoManager;
    this.deletedListener = deletedListener;
    this.cfgFactory = cfgFactory;
    this.pluginName = pluginName;
  }

  public void delete(Project project, boolean preserveGitRepository)
      throws IOException, RepositoryNotFoundException {
    // Remove from the jgit cache
    Repository repository =
        repoManager.openRepository(project.getNameKey());
    File repoFile = repository.getDirectory();
    cleanCache(repository);
    if (!preserveGitRepository) {
      deleteGitRepository(project.getNameKey(), repoFile);
    }
  }

  public void assertCanDelete(ProjectResource rsrc, boolean preserveGitRepository)
      throws CannotDeleteProjectException {
    if (!preserveGitRepository
        && !cfgFactory.getFromGerritConfig(pluginName).getBoolean(
            "allowDeletionOfReposWithTags", true)) {
      assertHasNoTags(rsrc);
    }
  }

  private void assertHasNoTags(ProjectResource rsrc)
      throws CannotDeleteProjectException {
    try (Repository repo = repoManager.openRepository(rsrc.getNameKey())) {
      if (!repo.getRefDatabase().getRefs(Constants.R_TAGS).isEmpty()) {
        throw new CannotDeleteProjectException(String.format(
            "Project %s has tags", rsrc.getName()));
      }
    } catch (IOException e) {
      throw new CannotDeleteProjectException(e);
    }
  }

  private void deleteGitRepository(final Project.NameKey project,
      final File repoFile) throws IOException {
    // Delete the repository from disk
    Path trash = moveToTrash(repoFile.toPath(), project);
    try {
      recursiveDelete(trash);
    } catch (IOException e) {
      // Only log if delete failed - repo already moved to trash.
      // Otherwise, listeners are never called.
      log.warn("Error trying to delete " + trash, e);
    }

    // Delete parent folders if they are (now) empty
    recursiveDeleteParent(repoFile.getParentFile(), gitDir.toFile());

    // Send an event that the repository was deleted
    ProjectDeletedListener.Event event = new ProjectDeletedListener.Event() {
      @Override
      public String getProjectName() {
        return project.get();
      }

      @Override
      public NotifyHandling getNotify() {
        return NotifyHandling.NONE;
      }
    };
    for (ProjectDeletedListener l : deletedListener) {
      try {
        l.onProjectDeleted(event);
      } catch (RuntimeException e) {
        log.warn("Failure in ProjectDeletedListener", e);
      }
    }
  }

  private Path moveToTrash(Path directory, Project.NameKey nameKey)
      throws IOException {
    Path trashRepo = gitDir.resolve(nameKey.get() + "."
        + System.currentTimeMillis() + ".deleted");
    return Files.move(directory, trashRepo, StandardCopyOption.ATOMIC_MOVE);
  }

  private void cleanCache(final Repository repository) {
    repository.close();
    RepositoryCache.close(repository);
  }

  /**
   * Recursively delete the specified file and all of its contents.
   *
   * @throws IOException
   */
  private void recursiveDelete(Path file) throws IOException {
    Files.walkFileTree(file, new SimpleFileVisitor<Path>() {
      @Override
      public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
          throws IOException {
        Files.delete(file);
        return FileVisitResult.CONTINUE;
      }

      @Override
      public FileVisitResult postVisitDirectory(Path dir, IOException e)
          throws IOException {
        if (e != null) {
          throw e;
        }
        Files.delete(dir);
        return FileVisitResult.CONTINUE;
      }
    });
  }

  /**
   * Recursively delete the specified file and its parent files until we hit the
   * file {@code Until} or the parent file is populated. This is used when we
   * have a tree structure such as a/b/c/d.git and a/b/e.git - if we delete
   * a/b/c/d.git, we no longer need a/b/c/.
   */
  private void recursiveDeleteParent(File file, File until) {
    if (file.equals(until)) {
      return;
    }
    if (file.listFiles().length == 0) {
      File parent = file.getParentFile();
      if (file.delete()) {
        recursiveDeleteParent(parent, until);
      }
    }
  }
}
