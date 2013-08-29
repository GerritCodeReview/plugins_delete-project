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

import com.google.gerrit.extensions.webui.UiAction;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.config.AllProjectsNameProvider;
import com.google.gerrit.server.project.ProjectResource;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.googlesource.gerrit.plugins.deleteproject.cache.CacheDeleteHandler;
import com.googlesource.gerrit.plugins.deleteproject.database.DatabaseDeleteHandler;
import com.googlesource.gerrit.plugins.deleteproject.fs.FilesystemDeleteHandler;

public class DeleteAction extends DeleteProject implements
    UiAction<ProjectResource> {
  private final Provider<CurrentUser> currentUser;

  @Inject
  DeleteAction(Provider<CurrentUser> currentUser,
      AllProjectsNameProvider allProjectsNameProvider,
      DatabaseDeleteHandler dbHandler,
      FilesystemDeleteHandler fsHandler,
      CacheDeleteHandler cacheHandler) {
    super(allProjectsNameProvider, dbHandler, fsHandler, cacheHandler);
    this.currentUser = currentUser;
  }

  @Override
  public UiAction.Description getDescription(ProjectResource rsrc) {
    return new UiAction.Description()
        .setLabel("Delete Project")
        .setTitle(isAllProjects(rsrc)
            ? String.format("No deletion of %s project",
                allProjectsName)
            : String.format("Deleting project %s", rsrc.getName()))
        .setEnabled(!isAllProjects(rsrc))
        .setVisible(currentUser.get() instanceof IdentifiedUser);
  }
}
