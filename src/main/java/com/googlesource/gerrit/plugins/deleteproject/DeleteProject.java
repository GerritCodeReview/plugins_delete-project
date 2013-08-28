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

import java.io.IOException;
import java.sql.SQLException;
import java.util.Collection;

import com.google.gerrit.extensions.annotations.RequiresCapability;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.extensions.restapi.UnprocessableEntityException;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.config.AllProjectsNameProvider;
import com.google.gerrit.server.project.ProjectResource;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.googlesource.gerrit.plugins.deleteproject.DeleteProject.Input;
import com.googlesource.gerrit.plugins.deleteproject.cache.CacheDeleteHandler;
import com.googlesource.gerrit.plugins.deleteproject.database.CannotDeleteProjectException;
import com.googlesource.gerrit.plugins.deleteproject.database.DatabaseDeleteHandler;
import com.googlesource.gerrit.plugins.deleteproject.fs.FilesystemDeleteHandler;

@RequiresCapability(DeleteProjectCapability.DELETE_PROJECT)
class DeleteProject implements RestModifyView<ProjectResource, Input> {
  static class Input {
    boolean preserve;
    boolean force;
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
      throws UnprocessableEntityException, ResourceConflictException,
      OrmException, SQLException, IOException {
    Project project = rsrc.getControl().getProject();
    // Don't let people delete All-Projects, that's stupid
    if (project.getName().endsWith(AllProjectsNameProvider.DEFAULT)) {
      throw new UnprocessableEntityException(AllProjectsNameProvider.DEFAULT);
    }

    try {
      databaseDeleteHandler.assertCanDelete(project);
    } catch (CannotDeleteProjectException e) {
      throw new UnprocessableEntityException(e.getMessage());
    }

    if (input == null || !input.force) {
      Collection<String> warnings = databaseDeleteHandler.getWarnings(project);
      if (warnings != null && !warnings.isEmpty()) {
        throw new ResourceConflictException(String
            .format("Project %s has open changes", project.getName()));
      }
    }

    databaseDeleteHandler.delete(project);
    filesystemDeleteHandler.delete(project,
        input == null ? false : input.preserve);
    cacheDeleteHandler.delete(project);
    return Response.none();
  }
}
