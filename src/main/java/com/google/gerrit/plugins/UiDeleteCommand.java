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

package com.google.gerrit.plugins;

import java.util.EnumSet;

import com.google.gerrit.common.data.GlobalCapability;
import com.google.gerrit.extensions.annotations.RequiresCapability;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.extensions.webui.UiCommand;
import com.google.gerrit.extensions.webui.UiCommandResult;
import com.google.gerrit.plugins.cache.CacheDeleteHandler;
import com.google.gerrit.plugins.database.DatabaseDeleteHandler;
import com.google.gerrit.plugins.fs.FilesystemDeleteHandler;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.account.CapabilityControl;
import com.google.gerrit.server.config.AllProjectsNameProvider;
import com.google.gerrit.server.project.ProjectResource;
import com.google.inject.Inject;
import com.google.inject.Provider;

@RequiresCapability(GlobalCapability.KILL_TASK)
class UiDeleteCommand implements UiCommand<ProjectResource>,
    RestModifyView<ProjectResource, UiDeleteCommand.Input> {

  private Provider<CurrentUser> cu;
  private final DatabaseDeleteHandler databaseDeleteHandler;
  private final FilesystemDeleteHandler filesystemDeleteHandler;
  private final CacheDeleteHandler cacheDeleteHandler;

  @Inject
  UiDeleteCommand(Provider<CurrentUser> cu,
      DatabaseDeleteHandler databaseDeleteHandler,
      FilesystemDeleteHandler filesystemDeleteHandler,
      CacheDeleteHandler cacheDeleteHandler) {
    this.cu = cu;
    this.databaseDeleteHandler = databaseDeleteHandler;
    this.filesystemDeleteHandler = filesystemDeleteHandler;
    this.cacheDeleteHandler = cacheDeleteHandler;
  }

  @Override
  public EnumSet<Place> getPlaces() {
    return EnumSet.of(UiCommand.Place.PROJECT_INFO_ACTION_PANEL);
  }

  @Override
  public boolean isVisible(ProjectResource proj) {
    return signedInAndHasCapability();
  }

  @Override
  public boolean isEnabled(ProjectResource proj) {
    return !isAllProjects(proj);
  }

  @Override
  public String getLabel(ProjectResource proj) {
    return "Delete";
  }

  @Override
  public String getConfirmationMessage(ProjectResource proj) {
    return String.format("Are you sure you want to delete the project %s?"
        + " This is an operation which permanently deletes data."
        + " This cannot be undone!", proj.getName());
  }

  @Override
  public String getTitle(ProjectResource proj) {
    if (isAllProjects(proj)) {
      return String.format("No deletion of %s project",
          AllProjectsNameProvider.DEFAULT);
    }
    return String.format("Deleting project %s", proj.getName());
  }

  @Override
  public UiCommandResult apply(ProjectResource proj, Input input)
      throws Exception {
    final Project project = proj.getControl().getProject();
    databaseDeleteHandler.delete(project);
    filesystemDeleteHandler.delete(project);
    cacheDeleteHandler.delete(project);
    return new UiCommandResult(String.format(
        "The project %s was successfully deleted", proj.getName()), "redirect",
        "/admin/projects");
  }

  // Check @RequiresCapability annotation manually.
  // Later we may extend RestAPI Servlet and check it in central
  // place, like it is the case with SSH commands.
  private boolean signedInAndHasCapability() {
    CurrentUser user = cu.get();
    if (!(user instanceof IdentifiedUser)) {
      return false;
    }
    RequiresCapability rc =
        this.getClass().getAnnotation(RequiresCapability.class);
    if (rc == null) {
      return false;
    }
    CapabilityControl ctl = user.getCapabilities();
    if (!ctl.canPerform(rc.value()) && !ctl.canAdministrateServer()) {
      return false;
    }
    return true;
  }

  private boolean isAllProjects(ProjectResource proj) {
    return proj.getName().endsWith(AllProjectsNameProvider.DEFAULT);
  }

  static class Input {
  }
}
