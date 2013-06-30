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

import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.Option;

import com.google.gerrit.common.data.GlobalCapability;
import com.google.gerrit.extensions.annotations.RequiresCapability;
import com.google.gerrit.reviewdb.client.Project;
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

  private final DeleteProject.Factory deleteProjectFactory;

  @Inject
  public DeleteCommand(DeleteProject.Factory deleteProjectFactory) {
    this.deleteProjectFactory = deleteProjectFactory;
  }

  @Override
  public void run() throws UnloggedFailure, Failure, Exception {
    Project project = projectControl.getProject();
    DeleteProject deleteProject = deleteProjectFactory.create(project,
        yesReallyDelete, force);

    try {
      deleteProject.run();
    } catch (DeleteProjectFailedException e) {
      throw new UnloggedFailure(e.getMessage());
    }
  }
}
