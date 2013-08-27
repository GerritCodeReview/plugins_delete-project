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
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.project.ProjectResource;
import com.google.inject.Inject;
import com.googlesource.gerrit.plugins.deleteproject.DeleteProject.Input;
import com.googlesource.gerrit.plugins.deleteproject.cache.CacheDeleteHandler;
import com.googlesource.gerrit.plugins.deleteproject.database.DatabaseDeleteHandler;
import com.googlesource.gerrit.plugins.deleteproject.fs.FilesystemDeleteHandler;

@RequiresCapability(DeleteProjectCapability.DELETE_PROJECT)
class DeleteProject implements RestModifyView<ProjectResource, Input> {
  static class Input {
    boolean preserve;
  }

  private final DatabaseDeleteHandler databaseDeleteHandler;
  private final FilesystemDeleteHandler filesystemDeleteHandler;
  private final CacheDeleteHandler cacheDeleteHandler;

  @Inject
  DeleteProject(DatabaseDeleteHandler databaseDeleteHandler,
      FilesystemDeleteHandler filesystemDeleteHandler,
      CacheDeleteHandler cacheDeleteHandler) {
    this.databaseDeleteHandler = databaseDeleteHandler;
    this.filesystemDeleteHandler = filesystemDeleteHandler;
    this.cacheDeleteHandler = cacheDeleteHandler;
  }

  @Override
  public Object apply(ProjectResource rsrc, Input input)
      throws Exception {
    Project project = rsrc.getControl().getProject();
    databaseDeleteHandler.delete(project);
    filesystemDeleteHandler.delete(project,
        input == null ? false : input.preserve);
    cacheDeleteHandler.delete(project);
    return Response.none();
  }
}
