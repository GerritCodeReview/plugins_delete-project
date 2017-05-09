// Copyright (C) 2014 The Android Open Source Project
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

package com.googlesource.gerrit.plugins.deleteproject.projectconfig;

import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.collect.Iterables;
import com.google.gerrit.extensions.common.ProjectInfo;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.config.AllProjectsName;
import com.google.gerrit.server.config.AllProjectsNameProvider;
import com.google.gerrit.server.config.SitePaths;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.project.ListChildProjects;
import com.google.gerrit.server.project.ProjectResource;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.googlesource.gerrit.plugins.deleteproject.CannotDeleteProjectException;
import java.util.List;

public class ProjectConfigDeleteHandler {

  protected final AllProjectsName allProjectsName;

  private final SitePaths site;
  private final Provider<ListChildProjects> listChildProjectsProvider;

  @Inject
  public ProjectConfigDeleteHandler(
      SitePaths site,
      AllProjectsNameProvider allProjectsNameProvider,
      Provider<ListChildProjects> listChildProjectsProvider) {
    this.site = site;
    this.allProjectsName = allProjectsNameProvider.get();
    this.listChildProjectsProvider = listChildProjectsProvider;
  }

  public void assertCanDelete(ProjectResource rsrc) throws CannotDeleteProjectException {
    assertIsNotAllProjects(rsrc);
    assertHasNoChildProjects(rsrc);
  }

  private void assertIsNotAllProjects(ProjectResource rsrc) throws CannotDeleteProjectException {
    Project project = rsrc.getControl().getProject();
    if (project.getNameKey().equals(allProjectsName)) {
      throw new CannotDeleteProjectException("Perhaps you meant to rm -fR " + site.site_path);
    }
  }

  private void assertHasNoChildProjects(ProjectResource rsrc) throws CannotDeleteProjectException {
    try {
      List<ProjectInfo> children = listChildProjectsProvider.get().apply(rsrc);
      if (!children.isEmpty()) {
        String childrenString =
            Joiner.on(", ")
                .join(
                    Iterables.transform(
                        children,
                        new Function<ProjectInfo, String>() {
                          @Override
                          public String apply(ProjectInfo info) {
                            return info.name;
                          }
                        }));
        throw new CannotDeleteProjectException(
            "Cannot delete project because it has children: " + childrenString);
      }
    } catch (PermissionBackendException e) {
      throw new CannotDeleteProjectException(
          "Cannot delete project because of failure in permission backend.");
    }
  }
}
