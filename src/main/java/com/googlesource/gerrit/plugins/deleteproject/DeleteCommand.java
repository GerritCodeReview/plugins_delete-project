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

package com.googlesource.gerrit.plugins.deleteproject;

import static com.googlesource.gerrit.plugins.deleteproject.DeleteOwnProjectCapability.DELETE_OWN_PROJECT;
import static com.googlesource.gerrit.plugins.deleteproject.DeleteProjectCapability.DELETE_PROJECT;

import java.io.IOException;
import java.util.Collection;

import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.Option;

import com.google.gerrit.extensions.annotations.PluginName;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.account.CapabilityControl;
import com.google.gerrit.server.project.ProjectControl;
import com.google.gerrit.server.project.ProjectResource;
import com.google.gerrit.sshd.CommandMetaData;
import com.google.gerrit.sshd.SshCommand;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;

import com.googlesource.gerrit.plugins.deleteproject.cache.CacheDeleteHandler;
import com.googlesource.gerrit.plugins.deleteproject.database.DatabaseDeleteHandler;
import com.googlesource.gerrit.plugins.deleteproject.fs.FilesystemDeleteHandler;
import com.googlesource.gerrit.plugins.deleteproject.projectconfig.ProjectConfigDeleteHandler;

@CommandMetaData(name = "delete", description = "Delete specific project")
public final class DeleteCommand extends SshCommand {
  @Argument(index = 0, required = true, metaVar = "NAME", usage = "project to delete")
  private ProjectControl projectControl;

  @Option(name = "--yes-really-delete", usage = "confirmation to delete the project")
  private boolean yesReallyDelete;

  @Option(name = "--force", usage = "delete the project even if it has open changes")
  private boolean force = false;

  @Option(name = "--preserve-git-repository", usage = "don't delete git repository directory")
  private boolean preserveGitRepository = false;

  private final CacheDeleteHandler cacheDeleteHandler;
  private final DatabaseDeleteHandler databaseDeleteHandler;
  private final FilesystemDeleteHandler filesystemDeleteHandler;
  private final ProjectConfigDeleteHandler pcHandler;
  private final Provider<CurrentUser> userProvider;
  private final String pluginName;

  @Inject
  protected DeleteCommand(DatabaseDeleteHandler databaseDeleteHandler,
      FilesystemDeleteHandler filesystemDeleteHandler,
      CacheDeleteHandler cacheDeleteHandler,
      ProjectConfigDeleteHandler pcHandler,
      Provider<CurrentUser> userProvider,
      @PluginName String pluginName) {
    this.databaseDeleteHandler = databaseDeleteHandler;
    this.filesystemDeleteHandler = filesystemDeleteHandler;
    this.cacheDeleteHandler = cacheDeleteHandler;
    this.pcHandler = pcHandler;
    this.userProvider = userProvider;
    this.pluginName = pluginName;
  }

  @Override
  public void run() throws Failure {
    CapabilityControl ctl = userProvider.get().getCapabilities();
    if (!ctl.canAdministrateServer()
        && !ctl.canPerform(pluginName + "-" + DELETE_PROJECT)
        && !(ctl.canPerform(pluginName + "-" + DELETE_OWN_PROJECT)
            && projectControl.isOwner())) {
      throw new UnloggedFailure("not allowed to delete project");
    }

    final Project project = projectControl.getProject();
    final String projectName = project.getName();

    try {
      pcHandler.assertCanDelete(new ProjectResource(projectControl));
    } catch (CannotDeleteProjectException e) {
      throw new UnloggedFailure(e.getMessage());
    }

    try {
      databaseDeleteHandler.assertCanDelete(project);
    } catch (CannotDeleteProjectException e) {
      throw new UnloggedFailure("Cannot delete project " + projectName + ": "
          + e.getMessage());
    } catch (OrmException e) {
      die(e);
    }

    if (!yesReallyDelete) {
      StringBuilder msgBuilder = new StringBuilder();
      msgBuilder.append("Really delete ");
      msgBuilder.append(projectName);
      msgBuilder.append("?\n");
      msgBuilder.append("This is an operation which permanently deletes ");
      msgBuilder.append("data. This cannot be undone!\n");
      msgBuilder.append("If you are sure you wish to delete this project, ");
      msgBuilder.append("re-run\n");
      msgBuilder.append("with the --yes-really-delete flag.\n");
      throw new UnloggedFailure(msgBuilder.toString());
    }

    if (!force) {
      Collection<String> warnings = null;
      try {
        warnings = databaseDeleteHandler.getWarnings(project);
      } catch (OrmException e) {
        die(e);
      }
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

    try {
      databaseDeleteHandler.delete(project);
      filesystemDeleteHandler.delete(project, preserveGitRepository);
      cacheDeleteHandler.delete(project);
    } catch (OrmException e) {
      die(e);
    } catch (RepositoryNotFoundException e) {
      die(e);
    } catch (IOException e) {
      die(e);
    }
  }
}
