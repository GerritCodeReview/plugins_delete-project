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
import java.util.Collection;

import com.google.gerrit.extensions.annotations.RequiresCapability;
import com.google.gerrit.extensions.restapi.MethodNotAllowedException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.config.AllProjectsName;
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

  protected final AllProjectsName allProjectsName;
  private final DatabaseDeleteHandler dbHandler;
  private final FilesystemDeleteHandler fsHandler;
  private final CacheDeleteHandler cacheHandler;

  @Inject
  DeleteProject(AllProjectsNameProvider allProjectsNameProvider,
      DatabaseDeleteHandler dbHandler,
      FilesystemDeleteHandler fsHandler,
      CacheDeleteHandler cacheHandler) {
    this.allProjectsName = allProjectsNameProvider.get();
    this.dbHandler = dbHandler;
    this.fsHandler = fsHandler;
    this.cacheHandler = cacheHandler;
  }

  @Override
  public Object apply(ProjectResource rsrc, Input input)
      throws ResourceConflictException, OrmException, IOException,
      MethodNotAllowedException {
    if (isAllProjects(rsrc)) {
      throw new MethodNotAllowedException();
    }

    Project project = rsrc.getControl().getProject();
    try {
      dbHandler.assertCanDelete(project);
    } catch (CannotDeleteProjectException e) {
      throw new ResourceConflictException(e.getMessage());
    }

    if (input == null || !input.force) {
      Collection<String> warnings = dbHandler.getWarnings(project);
      if (!warnings.isEmpty()) {
        throw new ResourceConflictException(String.format(
            "Project %s has open changes", project.getName()));
      }
    }

    dbHandler.delete(project);
    fsHandler.delete(project,
        input == null ? false : input.preserve);
    cacheHandler.delete(project);
    return Response.none();
  }

  protected boolean isAllProjects(ProjectResource rsrc) {
    return (rsrc.getControl().getProject()
        .getNameKey().equals(allProjectsName));
  }
}
