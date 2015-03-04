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

import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.server.project.ProjectControl;
import com.google.gerrit.server.project.ProjectResource;
import com.google.gerrit.sshd.CommandMetaData;
import com.google.gerrit.sshd.SshCommand;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;

import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.Option;

import java.io.IOException;
import java.util.Collection;

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

  private final DeleteProject deleteProject;

  @Inject
  protected DeleteCommand(DeleteProject deleteProject) {
    this.deleteProject = deleteProject;
  }

  @Override
  public void run() throws Failure {
    try {
      ProjectResource rsrc = new ProjectResource(projectControl);
      deleteProject.assertDeletePermission(rsrc);
      deleteProject.assertCanDelete(rsrc);

      String projectName = projectControl.getProject().getName();

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
        Collection<String> warnings = deleteProject.getWarnings(rsrc);
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

      DeleteProject.Input input = new DeleteProject.Input();
      input.force = force;
      input.preserve = preserveGitRepository;
      deleteProject.doDelete(rsrc, input);
    } catch (AuthException | ResourceNotFoundException
        | ResourceConflictException | OrmException | IOException e) {
      die(e);
    }
  }
}
