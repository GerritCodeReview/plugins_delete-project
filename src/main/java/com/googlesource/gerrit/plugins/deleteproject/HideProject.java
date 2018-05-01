// Copyright (C) 2015 The Android Open Source Project
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

import com.google.gerrit.common.data.AccessSection;
import com.google.gerrit.extensions.client.ProjectState;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.TopLevelResource;
import com.google.gerrit.extensions.restapi.UnprocessableEntityException;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.git.MetaDataUpdate;
import com.google.gerrit.server.git.ProjectConfig;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.project.CreateProject;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.project.ProjectResource;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.errors.RepositoryNotFoundException;

@Singleton
class HideProject {

  private final MetaDataUpdate.Server metaDataUpdateFactory;
  private final ProjectCache projectCache;
  private final CreateProject.Factory createProjectFactory;
  private final Configuration cfg;

  @Inject
  HideProject(
      MetaDataUpdate.Server metaDataUpdateFactory,
      ProjectCache projectCache,
      CreateProject.Factory createProjectFactory,
      Configuration cfg) {
    this.metaDataUpdateFactory = metaDataUpdateFactory;
    this.projectCache = projectCache;
    this.createProjectFactory = createProjectFactory;
    this.cfg = cfg;
  }

  public void apply(ProjectResource rsrc)
      throws ResourceNotFoundException, ResourceConflictException, IOException {
    try {
      MetaDataUpdate md = metaDataUpdateFactory.create(rsrc.getNameKey());

      ProjectConfig projectConfig = ProjectConfig.read(md);
      Project p = projectConfig.getProject();
      p.setState(ProjectState.HIDDEN);

      for (AccessSection as : projectConfig.getAccessSections()) {
        projectConfig.remove(as);
      }

      String parentForDeletedProjects = cfg.getDeletedProjectsParent();
      createProjectIfMissing(parentForDeletedProjects);
      p.setParentName(parentForDeletedProjects);

      md.setMessage("Hide project\n");
      projectConfig.commit(md);
      projectCache.evict(projectConfig.getProject());
    } catch (RepositoryNotFoundException e) {
      throw new ResourceNotFoundException();
    } catch (ConfigInvalidException e) {
      throw new ResourceConflictException(e.getMessage());
    }
  }

  private void createProjectIfMissing(String projectName)
      throws ResourceConflictException, IOException {
    if (projectCache.get(new Project.NameKey(projectName)) == null) {
      try {
        createProjectFactory.create(projectName).apply(TopLevelResource.INSTANCE, null);
      } catch (BadRequestException
          | UnprocessableEntityException
          | ResourceNotFoundException
          | ConfigInvalidException
          | PermissionBackendException e) {
        throw new ResourceConflictException(
            String.format("Failed to create project %s", projectName));
      }
    }
  }
}
