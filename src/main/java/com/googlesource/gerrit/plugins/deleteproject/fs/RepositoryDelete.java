// Copyright (C) 2023 The Android Open Source Project
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

import static com.google.common.io.RecursiveDeleteOption.ALLOW_INSECURE;

import com.google.common.flogger.FluentLogger;
import com.google.common.io.MoreFiles;
import com.google.gerrit.common.UsedAt;
import com.google.gerrit.entities.Project;
import com.google.gerrit.extensions.api.changes.NotifyHandling;
import com.google.gerrit.extensions.events.ProjectDeletedListener;
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.googlesource.gerrit.plugins.deleteproject.TimeMachine;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import javax.inject.Inject;
import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.RepositoryCache;

/**
 * This class contains methods that remove a git repository from the filesystem and the jgit cache,
 * and optionally notify downstream listeners. It can therefore be reused by other plugins who need
 * to delete a git repository.
 */
public class RepositoryDelete {

  private final GitRepositoryManager repoManager;

  @Inject
  public RepositoryDelete(GitRepositoryManager repoManager) {
    this.repoManager = repoManager;
  }

  private static final FluentLogger log = FluentLogger.forEnclosingClass();
  private static final DateTimeFormatter FORMAT =
      DateTimeFormatter.ofPattern("yyyyMMddHHmmss").withZone(ZoneId.of("UTC"));

  /**
   * Removes a git repository from the filesystem and the jgit cache and optionally notifies
   * downstream listeners. You can choose if the git repo should either be deleted or archived.
   *
   * <p>In order to delete the git directory, the logic will first rename the directory, a two-step
   * process involving moving all the files in a different directory, and immediately deleting that
   * directory. This helps release any open file handlers, which would on NFS filesystems prevent
   * the directory from being empty (and therefore deletion would fail). For more details see <a
   * href="https://bugs.chromium.org/p/gerrit/issues/detail?id=16730">...</a>
   *
   * @param project - the git repo name that is eligible for deletion
   * @param preserveGitRepository - if true, just remove the repo from the git cache, but keep the
   *     repo on disk.
   * @param archiveDeletedRepos - if true, copy the repo to an archived path, and delete the
   *     original directory.
   * @param archivedFolder - only used when `archiveDeletedRepos` is true, provides the archived
   *     directory.
   * @param deletedListeners - a set of `ProjectDeletedListener`s - when provided these listeners
   *     will be notified when a directory is deleted. This is not used for archiving.
   * @throws RepositoryNotFoundException - if the repository does not exist
   * @throws IOException - if any of the underlying operations during repo deletion fails
   */
  public void execute(
      Project.NameKey project,
      boolean preserveGitRepository,
      boolean archiveDeletedRepos,
      Optional<Path> archivedFolder,
      DynamicSet<ProjectDeletedListener> deletedListeners)
      throws RepositoryNotFoundException, IOException {
    Repository repository = repoManager.openRepository(project);
    cleanCache(repository);
    if (!preserveGitRepository) {
      Path repoPath = repository.getDirectory().toPath();
      String projectName = project.get();
      if (archiveDeletedRepos) {
        archiveGitRepository(projectName, repoPath, archivedFolder, deletedListeners);
      } else {
        deleteGitRepository(projectName, repoPath, deletedListeners);
      }
    }
  }

  /**
   * Removes a git repository from the filesystem and the jgit cache. The git repo is neither
   * preserved (ie kept on disk) nor archived, and no downstream listeners are notified.
   *
   * @param project - the git repo name that is eligible for deletion
   * @throws RepositoryNotFoundException - if the repository does not exist
   * @throws IOException - if any of the underlying operations during repo deletion fails
   */
  @UsedAt(UsedAt.Project.PLUGIN_PULL_REPLICATION)
  public void execute(Project.NameKey project) throws RepositoryNotFoundException, IOException {
    execute(project, false, false, Optional.empty(), DynamicSet.emptySet());
  }

  private static void cleanCache(Repository repository) {
    repository.close();
    RepositoryCache.close(repository);
  }

  private static void archiveGitRepository(
      String projectName,
      Path repoPath,
      Optional<Path> archivedFolder,
      DynamicSet<ProjectDeletedListener> deletedListeners)
      throws IOException {
    Path basePath = getBasePath(repoPath, projectName);
    if (archivedFolder.isEmpty()) {
      throw new IllegalArgumentException(
          "An archive path must be provided for the " + basePath + " repo to be archived");
    }
    Path renamedProjectDir = renameRepository(repoPath, basePath, projectName, "archived");
    try {
      Path archive = getArchivePath(archivedFolder.get(), renamedProjectDir, basePath);
      FileUtils.copyDirectory(renamedProjectDir.toFile(), archive.toFile());
      MoreFiles.deleteRecursively(renamedProjectDir, ALLOW_INSECURE);
    } catch (IOException e) {
      log.atWarning().withCause(e).log("Error trying to archive %s", renamedProjectDir);
    } finally {
      sendProjectDeletedEvent(projectName, deletedListeners);
    }
  }

  private static Path getArchivePath(Path archivedFolder, Path renamedProjectDir, Path basePath) {
    Path configArchiveRepo = archivedFolder.toAbsolutePath();
    Path relativePath = basePath.relativize(renamedProjectDir);
    return configArchiveRepo.resolve(relativePath);
  }

  private static void deleteGitRepository(
      String projectName, Path repoPath, DynamicSet<ProjectDeletedListener> deletedListeners)
      throws IOException {
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
      sendProjectDeletedEvent(projectName, deletedListeners);
    }
  }

  private static Path getBasePath(Path repo, String projectName) {
    Path projectPath = Path.of(projectName);
    return repo.getRoot()
        .resolve(repo.subpath(0, repo.getNameCount() - projectPath.getNameCount()));
  }

  private static Path renameRepository(
      Path directory, Path basePath, String projectName, String option) throws IOException {
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
  private static void recursivelyDeleteEmptyParents(File file, File until) throws IOException {
    if (file.equals(until)) {
      return;
    }
    if (file.listFiles().length == 0) {
      File parent = file.getParentFile();
      Files.delete(file.toPath());
      recursivelyDeleteEmptyParents(parent, until);
    }
  }

  private static void sendProjectDeletedEvent(
      String projectName, DynamicSet<ProjectDeletedListener> deletedListeners) {
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
  }
}
