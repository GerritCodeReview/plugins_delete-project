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

import java.io.IOException;
import java.util.Collection;

import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.lib.Config;
import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.Option;

import com.google.gerrit.extensions.annotations.RequiresCapability;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.config.AllProjectsName;
import com.google.gerrit.server.config.AllProjectsNameProvider;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.config.SitePaths;
import com.google.gerrit.server.project.ProjectControl;
import com.google.gerrit.sshd.CommandMetaData;
import com.google.gerrit.sshd.SshCommand;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.googlesource.gerrit.plugins.deleteproject.cache.CacheDeleteHandler;
import com.googlesource.gerrit.plugins.deleteproject.database.CannotDeleteProjectException;
import com.googlesource.gerrit.plugins.deleteproject.database.DatabaseDeleteHandler;
import com.googlesource.gerrit.plugins.deleteproject.fs.FilesystemDeleteHandler;

@RequiresCapability(DeleteProjectCapability.DELETE_PROJECT)
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

  private final SitePaths site;
  private final AllProjectsName allProjectsName;
  private final CacheDeleteHandler cacheDeleteHandler;
  private final DatabaseDeleteHandler databaseDeleteHandler;
  private final FilesystemDeleteHandler filesystemDeleteHandler;

  @Inject
  protected DeleteCommand(SitePaths site,
      @GerritServerConfig Config cfg,
      AllProjectsNameProvider allProjectsNameProvider,
      DatabaseDeleteHandler databaseDeleteHandler,
      FilesystemDeleteHandler filesystemDeleteHandler,
      CacheDeleteHandler cacheDeleteHandler) {
    this.site = site;
    this.allProjectsName = allProjectsNameProvider.get();
    this.databaseDeleteHandler = databaseDeleteHandler;
    this.filesystemDeleteHandler = filesystemDeleteHandler;
    this.cacheDeleteHandler = cacheDeleteHandler;
  }

  @Override
  public void run() throws Failure {
    final Project project = projectControl.getProject();
    final String projectName = project.getName();

    // Don't let people delete All-Projects, that's stupid
    if (project.getNameKey().equals(allProjectsName)) {
      throw new UnloggedFailure("Perhaps you meant to rm -fR " + site.site_path);
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
      if (!warnings.isEmpty()) {
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
