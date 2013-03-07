// Copyright (C) 2012 The Android Open Source Project
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

package com.google.gerrit.plugins;

import java.io.File;
import java.io.IOException;
import java.util.Collection;

import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.RepositoryCache;
import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.Option;

import com.google.gerrit.common.data.GlobalCapability;
import com.google.gerrit.extensions.annotations.RequiresCapability;
import com.google.gerrit.plugins.database.DatabaseDeleteHandler;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.config.AllProjectsNameProvider;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.config.SitePaths;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.project.ProjectControl;
import com.google.gerrit.sshd.SshCommand;
import com.google.inject.Inject;

@RequiresCapability(GlobalCapability.KILL_TASK)
public final class DeleteCommand extends SshCommand {
  @Argument(index = 0, required = true, metaVar = "NAME", usage = "project to delete")
  private ProjectControl projectControl;

  @Option(name = "--yes-really-delete", usage = "confirmation to delete the project")
  private boolean yesReallyDelete;

  @Option(name = "--force", usage = "delete the project even if it has open changes")
  private boolean force = false;

  private final File gitDir;

  private final ProjectCache projectCache;

  private final GitRepositoryManager repoManager;

  private final SitePaths site;

  private final DatabaseDeleteHandler databaseDeleteHandler;

  @Inject
  protected DeleteCommand(ProjectCache projectCache,
      GitRepositoryManager repoManager, SitePaths site,
      @GerritServerConfig Config cfg,
      DatabaseDeleteHandler databaseDeleteHandler) {
    gitDir = site.resolve(cfg.getString("gerrit", null, "basePath"));
    this.projectCache = projectCache;
    this.repoManager = repoManager;
    this.site = site;
    this.databaseDeleteHandler = databaseDeleteHandler;
  }

  @Override
  public void run() throws UnloggedFailure, Failure, Exception {
    final Project project = projectControl.getProject();
    // Don't let people delete All-Projects, that's stupid
    final String projectName = project.getName();
    if (projectName.endsWith(AllProjectsNameProvider.DEFAULT)) {
      throw new UnloggedFailure("Perhaps you meant to rm -fR " + site.site_path);
    }

    try {
      databaseDeleteHandler.assertCanDelete(project);
    } catch (Exception e) {
      throw new UnloggedFailure("Cannot delete project " + projectName + ": "
          + e.getMessage());
    }

    if (!yesReallyDelete) {
      stdout.print("Really delete " + project.getName() + "?\n");
      stdout.print("This is an operation which permanently deletes data. "
          + "This cannot be undone!\n");
      stdout.print("If you are sure you wish to delete this project, re-run\n"
          + "with the --yes-really-delete flag.\n");
      return;
    }

    if (!force) {
      Collection<String> warnings = databaseDeleteHandler.getWarnings(project);
      if (warnings != null && !warnings.isEmpty()) {
        StringBuilder msgBuilder = new StringBuilder();
        msgBuilder.append("There are warnings against deleting ");
        msgBuilder.append(projectName);
        msgBuilder.append(":\n");
        for (String warning: warnings) {
          msgBuilder.append(" * ");
          msgBuilder.append(warning);
          msgBuilder.append("\n");
        }
        msgBuilder.append("To really delete ");
        msgBuilder.append(projectName);
        msgBuilder.append(", re-run with the --force flag.");
        throw new UnloggedFailure(msgBuilder.toString());
      }
    }

    databaseDeleteHandler.delete(project);
    deleteFromDisk(project);

    // Clean up the cache
    projectCache.remove(project);
  }

  private void deleteFromDisk(Project project) throws IOException,
      RepositoryNotFoundException, UnloggedFailure {
    // Remove from the jgit cache
    final Repository repository = repoManager.openRepository(project
        .getNameKey());
    if (repository == null) {
      throw new UnloggedFailure("There was an error finding the project.");
    }

    repository.close();
    RepositoryCache.close(repository);

    // Delete the repository from disk
    File parentFile = repository.getDirectory().getParentFile();
    if (!recursiveDelete(repository.getDirectory())) {
      throw new UnloggedFailure("Error trying to delete "
          + repository.getDirectory().getAbsolutePath());
    }

    // Delete parent folders while they are (now) empty
    recursiveDeleteParent(parentFile, gitDir);
  }

  /**
   * Recursively delete the specified file and all of its contents.
   *
   * @return true on success, false if there was an error.
   */
  private boolean recursiveDelete(File file) {
    if (file.isDirectory()) {
      for (File f : file.listFiles()) {
        if (!recursiveDelete(f)) {
          return false;
        }
      }
    }
    return file.delete();
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
      file.delete();
      recursiveDeleteParent(parent, until);
    }
  }
}
