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

import java.util.Collection;

import org.eclipse.jgit.lib.Config;
import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.Option;

import com.google.gerrit.common.data.GlobalCapability;
import com.google.gerrit.extensions.annotations.RequiresCapability;
import com.google.gerrit.plugins.cache.CacheDeleteHandler;
import com.google.gerrit.plugins.database.DatabaseDeleteHandler;
import com.google.gerrit.plugins.fs.FilesystemDeleteHandler;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.config.AllProjectsNameProvider;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.config.SitePaths;
import com.google.gerrit.server.project.ProjectControl;
import com.google.gerrit.sshd.SshCommand;
import com.google.inject.Inject;

// TODO(davido): replace this with plugin-own capability "deleteProject"
@RequiresCapability(GlobalCapability.KILL_TASK)
public final class DeleteCommand extends SshCommand {
  @Argument(index = 0, required = true, metaVar = "NAME", usage = "project to delete")
  private ProjectControl projectControl;

  @Option(name = "--yes-really-delete", usage = "confirmation to delete the project")
  private boolean yesReallyDelete;

  @Option(name = "--force", usage = "delete the project even if it has open changes")
  private boolean force = false;

  private final SitePaths site;

  private final DatabaseDeleteHandler databaseDeleteHandler;

  private final FilesystemDeleteHandler filesystemDeleteHandler;

  private final CacheDeleteHandler cacheDeleteHandler;

  @Inject
  protected DeleteCommand(SitePaths site,
      @GerritServerConfig Config cfg,
      DatabaseDeleteHandler databaseDeleteHandler,
      FilesystemDeleteHandler filesystemDeleteHandler,
      CacheDeleteHandler cacheDeleteHandler) {
    this.site = site;
    this.databaseDeleteHandler = databaseDeleteHandler;
    this.filesystemDeleteHandler = filesystemDeleteHandler;
    this.cacheDeleteHandler = cacheDeleteHandler;
  }

  @Override
  public void run() throws UnloggedFailure, Failure, Exception {
    final Project project = projectControl.getProject();
    final String projectName = project.getName();

    // Don't let people delete All-Projects, that's stupid
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
      StringBuilder msgBuilder = new StringBuilder();
      msgBuilder.append("Really delete ");
      msgBuilder.append(projectName);
      msgBuilder.append("?\n");
      msgBuilder.append("This is an operation which permanently deletes");
      msgBuilder.append("data. This cannot be undone!\n");
      msgBuilder.append("If you are sure you wish to delete this project, ");
      msgBuilder.append("re-run\n");
      msgBuilder.append("with the --yes-really-delete flag.\n");
      throw new UnloggedFailure(msgBuilder.toString());
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
    filesystemDeleteHandler.delete(project);
    cacheDeleteHandler.delete(project);
  }
}
