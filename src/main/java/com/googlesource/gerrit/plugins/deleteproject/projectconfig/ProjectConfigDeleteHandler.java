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

import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.config.AllProjectsName;
import com.google.gerrit.server.config.AllProjectsNameProvider;
import com.google.gerrit.server.config.SitePaths;
import com.google.gerrit.server.project.ProjectResource;
import com.google.inject.Inject;

import com.googlesource.gerrit.plugins.deleteproject.CannotDeleteProjectException;

public class ProjectConfigDeleteHandler {

  protected final AllProjectsName allProjectsName;

  private final SitePaths site;

  @Inject
  public ProjectConfigDeleteHandler(SitePaths site,
      AllProjectsNameProvider allProjectsNameProvider) {
    this.site = site;
    this.allProjectsName = allProjectsNameProvider.get();
 }

  public void assertCanDelete(ProjectResource rsrc)
      throws CannotDeleteProjectException {
    assertIsNotAllProjects(rsrc);
  }

  private void assertIsNotAllProjects(ProjectResource rsrc)
      throws CannotDeleteProjectException {
    Project project = rsrc.getControl().getProject();
    if (project.getNameKey().equals(allProjectsName)) {
      throw new CannotDeleteProjectException("Perhaps you meant to rm -fR "
          + site.site_path);
    }
  }
}
