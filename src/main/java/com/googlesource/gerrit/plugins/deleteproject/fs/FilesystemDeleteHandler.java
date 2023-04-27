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

import com.google.gerrit.entities.Project;
import com.google.gerrit.extensions.events.ProjectDeletedListener;
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.inject.Inject;
import com.googlesource.gerrit.plugins.deleteproject.Configuration;
import java.io.IOException;
import java.util.Optional;
import org.eclipse.jgit.errors.RepositoryNotFoundException;

public class FilesystemDeleteHandler {
  private final RepositoryDelete repositoryDelete;
  private final DynamicSet<ProjectDeletedListener> deletedListeners;
  private final Configuration config;

  @Inject
  public FilesystemDeleteHandler(
      RepositoryDelete repositoryDelete,
      DynamicSet<ProjectDeletedListener> deletedListeners,
      Configuration config) {
    this.repositoryDelete = repositoryDelete;
    this.deletedListeners = deletedListeners;
    this.config = config;
  }

  public void delete(Project.NameKey project, boolean preserveGitRepository)
      throws IOException, RepositoryNotFoundException {
<<<<<<< HEAD   (ecd8f3 Merge branch 'stable-3.8' into stable-3.9)
    // Remove from the jgit cache
    Repository repository = repoManager.openRepository(project);
    cleanCache(repository);
    if (!preserveGitRepository) {
      Path repoPath = repository.getDirectory().toPath();
      String projectName = project.get();
      if (config.shouldArchiveDeletedRepos()) {
        archiveGitRepository(projectName, repoPath);
      } else {
        deleteGitRepository(projectName, repoPath);
      }
    }
  }

  private void cleanCache(Repository repository) {
    repository.close();
    RepositoryCache.close(repository);
  }

  private void archiveGitRepository(String projectName, Path repoPath) throws IOException {
    Path basePath = getBasePath(repoPath, projectName);
    Path renamedProjectDir = renameRepository(repoPath, basePath, projectName, "archived");
    try {
      Path archive = getArchivePath(renamedProjectDir, basePath);
      FileUtils.copyDirectory(renamedProjectDir.toFile(), archive.toFile());
      MoreFiles.deleteRecursively(renamedProjectDir, ALLOW_INSECURE);
    } catch (IOException e) {
      log.atWarning().withCause(e).log("Error trying to archive %s", renamedProjectDir);
    } finally {
      sendProjectDeletedEvent(projectName);
    }
  }

  private Path getArchivePath(Path renamedProjectDir, Path basePath) {
    Path configArchiveRepo = config.getArchiveFolder().toAbsolutePath();
    Path relativePath = basePath.relativize(renamedProjectDir);
    return configArchiveRepo.resolve(relativePath);
  }

  private void deleteGitRepository(String projectName, Path repoPath) throws IOException {
    // Delete the repository from disk
    Path basePath = getBasePath(repoPath, projectName);
    Path trash = renameRepository(repoPath, basePath, projectName, "deleted");
    try {
      MoreFiles.deleteRecursively(trash, ALLOW_INSECURE);
      recursivelyDeleteEmptyParents(repoPath.toFile().getParentFile(), basePath.toFile());
    } catch (IOException e) {
      // Only log if delete failed - repo already moved to trash.
      log.atWarning().withCause(e).log("Error trying to delete %s or its parents", trash);
    } finally {
      sendProjectDeletedEvent(projectName);
    }
  }

  private Path getBasePath(Path repo, String projectName) {
    Path projectPath = Paths.get(projectName);
    return repo.getRoot()
        .resolve(repo.subpath(0, repo.getNameCount() - projectPath.getNameCount()));
  }

  private Path renameRepository(Path directory, Path basePath, String projectName, String option)
      throws IOException {
    Path newRepo =
        basePath.resolve(
            projectName + "." + FORMAT.format(TimeMachine.now()) + ".%" + option + "%.git");
    return Files.move(directory, newRepo, StandardCopyOption.ATOMIC_MOVE);
  }

  /**
   * Recursively delete the specified file and its parent files until we hit the file {@code Until}
   * or the parent file is populated. This is used when we have a tree structure such as a/b/c/d.git
   * and a/b/e.git - if we delete a/b/c/d.git, we no longer need a/b/c/.
   */
  private void recursivelyDeleteEmptyParents(File file, File until) throws IOException {
    if (file.equals(until)) {
      return;
    }
    if (file.listFiles().length == 0) {
      File parent = file.getParentFile();
      Files.delete(file.toPath());
      recursivelyDeleteEmptyParents(parent, until);
    }
  }

  private void sendProjectDeletedEvent(String projectName) {
    if (!deletedListeners.iterator().hasNext()) {
      return;
    }
    ProjectDeletedListener.Event event =
        new ProjectDeletedListener.Event() {
          @Override
          public String getProjectName() {
            return projectName;
          }

          @Override
          public NotifyHandling getNotify() {
            return NotifyHandling.NONE;
          }
        };
    for (ProjectDeletedListener l : deletedListeners) {
      try {
        l.onProjectDeleted(event);
      } catch (RuntimeException e) {
        log.atWarning().withCause(e).log("Failure in ProjectDeletedListener");
      }
    }
=======
    repositoryDelete.execute(
        project,
        preserveGitRepository,
        config.shouldArchiveDeletedRepos(),
        Optional.ofNullable(config.getArchiveFolder()),
        deletedListeners);
>>>>>>> CHANGE (79674d Extract the repository deletion logic so it becomes reusable)
  }
}
