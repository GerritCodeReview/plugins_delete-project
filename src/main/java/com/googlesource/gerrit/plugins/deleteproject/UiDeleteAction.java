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

package com.googlesource.gerrit.plugins.deleteproject;

import com.google.gerrit.extensions.annotations.RequiresCapability;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.extensions.webui.UiAction;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.config.AllProjectsNameProvider;
import com.google.gerrit.server.project.ProjectResource;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.googlesource.gerrit.plugins.deleteproject.cache.CacheDeleteHandler;
import com.googlesource.gerrit.plugins.deleteproject.database.DatabaseDeleteHandler;
import com.googlesource.gerrit.plugins.deleteproject.fs.FilesystemDeleteHandler;

@RequiresCapability(DeleteProjectCapability.DELETE_PROJECT)
class UiDeleteAction implements UiAction<ProjectResource>,
    RestModifyView<ProjectResource, UiDeleteAction.Input> {

  private Provider<CurrentUser> cu;
  private final DatabaseDeleteHandler databaseDeleteHandler;
  private final FilesystemDeleteHandler filesystemDeleteHandler;
  private final CacheDeleteHandler cacheDeleteHandler;

  @Inject
  UiDeleteAction(Provider<CurrentUser> cu,
      DatabaseDeleteHandler databaseDeleteHandler,
      FilesystemDeleteHandler filesystemDeleteHandler,
      CacheDeleteHandler cacheDeleteHandler) {
    this.cu = cu;
    this.databaseDeleteHandler = databaseDeleteHandler;
    this.filesystemDeleteHandler = filesystemDeleteHandler;
    this.cacheDeleteHandler = cacheDeleteHandler;
  }

  @Override
  public UiAction.Description getDescription(ProjectResource rsrc) {
    return new UiAction.Description()
        .setLabel("Delete Project")
        .setTitle(isAllProjects(rsrc)
            ? String.format("No deletion of %s project",
                AllProjectsNameProvider.DEFAULT)
            : String.format("Deleting project %s", rsrc.getName()))
        .setEnabled(!isAllProjects(rsrc))
        .setVisible(cu.get() instanceof IdentifiedUser);
  }

  @Override
  public String apply(ProjectResource rsrc, Input input)
      throws Exception {
    Project project = rsrc.getControl().getProject();
    databaseDeleteHandler.delete(project);
    filesystemDeleteHandler.delete(project, input.preserve);
    cacheDeleteHandler.delete(project);
    return String.format("The project \"%s\" was successfully deleted",
        rsrc.getName());
  }

  private boolean isAllProjects(ProjectResource rsrc) {
    return rsrc.getName().endsWith(AllProjectsNameProvider.DEFAULT);
  }

  static class Input {
    boolean preserve;
  }
}
