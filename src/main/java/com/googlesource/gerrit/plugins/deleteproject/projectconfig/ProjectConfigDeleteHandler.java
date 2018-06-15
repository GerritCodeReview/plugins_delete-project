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
import com.google.gerrit.server.project.ListChildProjects;
import com.google.gerrit.server.project.ProjectResource;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.googlesource.gerrit.plugins.deleteproject.CannotDeleteProjectException;
import com.googlesource.gerrit.plugins.deleteproject.ProtectedProjects;
import java.util.List;

public class ProjectConfigDeleteHandler {

  private final ProtectedProjects protectedProjects;
  private final Provider<ListChildProjects> listChildProjectsProvider;

  @Inject
  public ProjectConfigDeleteHandler(
      ProtectedProjects protectedProjects, Provider<ListChildProjects> listChildProjectsProvider) {
    this.protectedProjects = protectedProjects;
    this.listChildProjectsProvider = listChildProjectsProvider;
  }

  public void assertCanDelete(ProjectResource rsrc) throws CannotDeleteProjectException {
    assertIsNotProtected(rsrc);
    assertHasNoChildProjects(rsrc);
  }

  private void assertIsNotProtected(ProjectResource rsrc) throws CannotDeleteProjectException {
    if (protectedProjects.isProtected(rsrc)) {
      throw new CannotDeleteProjectException(
          "Cannot delete project because it is protected against deletion");
    }
  }

  private void assertHasNoChildProjects(ProjectResource rsrc) throws CannotDeleteProjectException {
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
  }
}
