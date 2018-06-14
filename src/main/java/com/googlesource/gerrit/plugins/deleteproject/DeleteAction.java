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

import com.google.gerrit.extensions.annotations.PluginName;
import com.google.gerrit.extensions.webui.UiAction;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.project.ProjectResource;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.googlesource.gerrit.plugins.deleteproject.cache.CacheDeleteHandler;
import com.googlesource.gerrit.plugins.deleteproject.database.DatabaseDeleteHandler;
import com.googlesource.gerrit.plugins.deleteproject.fs.FilesystemDeleteHandler;
import com.googlesource.gerrit.plugins.deleteproject.projectconfig.ProjectConfigDeleteHandler;

public class DeleteAction extends DeleteProject implements UiAction<ProjectResource> {
  private final ProtectedProjects protectedProjects;

  @Inject
  DeleteAction(
      ProtectedProjects protectedProjects,
      DatabaseDeleteHandler dbHandler,
      FilesystemDeleteHandler fsHandler,
      CacheDeleteHandler cacheHandler,
      ProjectConfigDeleteHandler pcHandler,
      Provider<CurrentUser> userProvider,
      @PluginName String pluginName,
      DeleteLog deleteLog,
      Configuration cfg,
      HideProject hideProject) {
    super(
        dbHandler,
        fsHandler,
        cacheHandler,
        pcHandler,
        userProvider,
        pluginName,
        deleteLog,
        cfg,
        hideProject);
    this.protectedProjects = protectedProjects;
  }

  @Override
  public UiAction.Description getDescription(ProjectResource rsrc) {
    return new UiAction.Description()
        .setLabel("Delete Project")
        .setTitle(
            protectedProjects.isProtected(rsrc)
                ? String.format("Not allowed to delete %s", rsrc.getName())
                : String.format("Delete project %s", rsrc.getName()))
        .setEnabled(!protectedProjects.isProtected(rsrc))
        .setVisible(canDelete(rsrc));
  }
}
