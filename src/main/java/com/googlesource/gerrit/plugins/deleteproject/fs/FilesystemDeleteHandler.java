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

import com.google.common.annotations.VisibleForTesting;
import com.google.gerrit.extensions.api.changes.NotifyHandling;
import com.google.gerrit.extensions.events.ProjectDeletedListener;
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.project.ProjectResource;
import com.google.inject.Inject;
import com.googlesource.gerrit.plugins.deleteproject.CannotDeleteProjectException;
import com.googlesource.gerrit.plugins.deleteproject.Configuration;
import com.googlesource.gerrit.plugins.deleteproject.TimeMachine;
import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.RepositoryCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FilesystemDeleteHandler {
  private static final Logger log = LoggerFactory.getLogger(FilesystemDeleteHandler.class);

  private final GitRepositoryManager repoManager;
  private final DynamicSet<ProjectDeletedListener> deletedListener;
  private final Configuration config;

  @Inject
  public FilesystemDeleteHandler(
      GitRepositoryManager repoManager,
      DynamicSet<ProjectDeletedListener> deletedListener,
      Configuration config) {
    this.repoManager = repoManager;
    this.deletedListener = deletedListener;
    this.config = config;
  }

  public void delete(Project project, boolean preserveGitRepository)
      throws IOException, RepositoryNotFoundException {
    // Remove from the jgit cache
    Repository repository = repoManager.openRepository(project.getNameKey());
    File repoFile = repository.getDirectory();
    cleanCache(repository);
    if (!preserveGitRepository) {
      deleteGitRepository(project.getNameKey(), repoFile);
    }
  }

  public void assertCanDelete(ProjectResource rsrc, boolean preserveGitRepository)
      throws CannotDeleteProjectException {
    if (!preserveGitRepository && !config.deletionWithTagsAllowed()) {
      assertHasNoTags(rsrc);
    }
  }

  private void assertHasNoTags(ProjectResource rsrc) throws CannotDeleteProjectException {
    try (Repository repo = repoManager.openRepository(rsrc.getNameKey())) {
      if (!repo.getRefDatabase().getRefs(Constants.R_TAGS).isEmpty()) {
        throw new CannotDeleteProjectException(
            String.format("Project %s has tags", rsrc.getName()));
      }
    } catch (IOException e) {
      throw new CannotDeleteProjectException(e);
    }
  }

  private void deleteGitRepository(final Project.NameKey project, final File repoFile)
      throws IOException {
    // Delete the repository from disk
    Path basePath = getBasePath(repoFile.toPath(), project);
    Path renamedDirectory = renameProjectDirectory(repoFile.toPath(), basePath, project);

    if (config.shouldArchiveDeletedRepos()) {
      Path archive;
      try {
        Path relativePath = basePath.relativize(renamedDirectory);
        Path configArchiveRepo = config.getArchiveFolder();
        archive = configArchiveRepo.resolve(relativePath);
        if (relativePath.getNameCount() > 1) {
          Path subPath = relativePath.subpath(0, relativePath.getNameCount() - 1);
          File parentFolders = configArchiveRepo.resolve(subPath).toFile();
          if (!parentFolders.exists()) {
            boolean created = parentFolders.mkdirs();
            if (!created) {
              log.error(
                  "Error trying to create parent folder {}, ignoring the parent folder", subPath);
              archive = configArchiveRepo.resolve(renamedDirectory.getFileName());
            }
          }
        }
        recursiveArchive(renamedDirectory, archive);
      } catch (IOException e) {
        log.warn("Error trying to archive {}. Repo is now in trash", repoFile.toPath(), e);
      }
    }

    boolean ok = false;
    try {
      recursiveDelete(renamedDirectory);
      ok = true;
    } catch (IOException e) {
      // Only log if delete failed - repo already renamed based on timestamp.
      // Otherwise, listeners are never called.
      log.warn("Error trying to delete {}", renamedDirectory, e);
    }

    // Delete parent folders if they are (now) empty
    if (ok) {
      try {
        recursiveDeleteParent(repoFile.getParentFile(), basePath.toFile());
      } catch (IOException e) {
        log.warn("Couldn't delete (empty) parents of {}", repoFile, e);
      }
    }

    // Send an event that the repository was deleted
    ProjectDeletedListener.Event event =
        new ProjectDeletedListener.Event() {
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

  private Path getBasePath(Path repo, Project.NameKey project) {
    Path projectPath = Paths.get(project.get());
    return repo.getRoot()
        .resolve(repo.subpath(0, repo.getNameCount() - projectPath.getNameCount()));
  }

  private Path renameProjectDirectory(Path directory, Path basePath, Project.NameKey nameKey)
      throws IOException {
    Path trashRepo =
        basePath.resolve(nameKey.get() + "." + TimeMachine.now().toEpochMilli() + ".%deleted%.git");
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
  void recursiveDelete(Path file) throws IOException {
    Files.walkFileTree(
        file,
        new SimpleFileVisitor<Path>() {
          @Override
          public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
              throws IOException {
            Files.delete(file);
            return FileVisitResult.CONTINUE;
          }

          @Override
          public FileVisitResult postVisitDirectory(Path dir, IOException e) throws IOException {
            if (e != null) {
              throw e;
            }
            Files.delete(dir);
            return FileVisitResult.CONTINUE;
          }
        });
  }

  /**
   * Recursively archive the specified file and all of its contents.
   *
   * @throws IOException
   */
  @VisibleForTesting
  void recursiveArchive(Path file, Path archiveRepo) throws IOException {
    Files.walkFileTree(
        file,
        new SimpleFileVisitor<Path>() {
          @Override
          public FileVisitResult preVisitDirectory(Path subDir, BasicFileAttributes attrs)
              throws IOException {
            Path pathRelative = file.relativize(subDir);
            Files.copy(subDir, archiveRepo.resolve(pathRelative));
            return FileVisitResult.CONTINUE;
          }

          @Override
          public FileVisitResult visitFile(Path subFile, BasicFileAttributes attrs)
              throws IOException {
            Path pathRelative = file.relativize(subFile);
            Files.copy(subFile, archiveRepo.resolve(pathRelative));
            return FileVisitResult.CONTINUE;
          }
        });
  }

  /**
   * Recursively delete the specified file and its parent files until we hit the file {@code Until}
   * or the parent file is populated. This is used when we have a tree structure such as a/b/c/d.git
   * and a/b/e.git - if we delete a/b/c/d.git, we no longer need a/b/c/.
   */
  private void recursiveDeleteParent(File file, File until) throws IOException {
    if (file.equals(until)) {
      return;
    }
    if (file.listFiles().length == 0) {
      File parent = file.getParentFile();
      Files.delete(file.toPath());
      recursiveDeleteParent(parent, until);
    }
  }
}
