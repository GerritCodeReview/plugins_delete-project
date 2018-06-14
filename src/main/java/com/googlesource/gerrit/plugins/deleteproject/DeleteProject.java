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

import static com.googlesource.gerrit.plugins.deleteproject.DeleteOwnProjectCapability.DELETE_OWN_PROJECT;
import static com.googlesource.gerrit.plugins.deleteproject.DeleteProjectCapability.DELETE_PROJECT;

import com.google.gerrit.extensions.annotations.PluginName;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.account.CapabilityControl;
import com.google.gerrit.server.config.AllProjectsName;
import com.google.gerrit.server.config.AllProjectsNameProvider;
import com.google.gerrit.server.config.AllUsersName;
import com.google.gerrit.server.config.AllUsersNameProvider;
import com.google.gerrit.server.project.ProjectResource;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.googlesource.gerrit.plugins.deleteproject.DeleteProject.Input;
import com.googlesource.gerrit.plugins.deleteproject.cache.CacheDeleteHandler;
import com.googlesource.gerrit.plugins.deleteproject.database.DatabaseDeleteHandler;
import com.googlesource.gerrit.plugins.deleteproject.fs.FilesystemDeleteHandler;
import com.googlesource.gerrit.plugins.deleteproject.projectconfig.ProjectConfigDeleteHandler;
import java.io.IOException;
import java.util.Collection;
import org.eclipse.jgit.errors.RepositoryNotFoundException;

class DeleteProject implements RestModifyView<ProjectResource, Input> {
  static class Input {
    boolean preserve;
    boolean force;
  }

  protected final AllProjectsName allProjectsName;
  protected final AllUsersName allUsersName;
  private final DatabaseDeleteHandler dbHandler;
  private final FilesystemDeleteHandler fsHandler;
  private final CacheDeleteHandler cacheHandler;
  private final ProjectConfigDeleteHandler pcHandler;
  private final Provider<CurrentUser> userProvider;
  private final String pluginName;
  private final DeleteLog deleteLog;
  private final Configuration cfg;
  private final HideProject hideProject;

  @Inject
  DeleteProject(
      AllProjectsNameProvider allProjectsNameProvider,
      AllUsersNameProvider allUsersNameProvider,
      DatabaseDeleteHandler dbHandler,
      FilesystemDeleteHandler fsHandler,
      CacheDeleteHandler cacheHandler,
      ProjectConfigDeleteHandler pcHandler,
      Provider<CurrentUser> userProvider,
      @PluginName String pluginName,
      DeleteLog deleteLog,
      Configuration cfg,
      HideProject hideProject) {
    this.allProjectsName = allProjectsNameProvider.get();
    this.allUsersName = allUsersNameProvider.get();
    this.dbHandler = dbHandler;
    this.fsHandler = fsHandler;
    this.cacheHandler = cacheHandler;
    this.pcHandler = pcHandler;
    this.userProvider = userProvider;
    this.pluginName = pluginName;
    this.deleteLog = deleteLog;
    this.cfg = cfg;
    this.hideProject = hideProject;
  }

  @Override
  public Object apply(ProjectResource rsrc, Input input)
      throws ResourceNotFoundException, ResourceConflictException, OrmException, IOException,
          AuthException {
    assertDeletePermission(rsrc);
    assertCanDelete(rsrc, input);

    if (input == null || !input.force) {
      Collection<String> warnings = getWarnings(rsrc);
      if (!warnings.isEmpty()) {
        throw new ResourceConflictException(
            String.format("Project %s has open changes", rsrc.getName()));
      }
    }

    doDelete(rsrc, input);
    return Response.none();
  }

  public void assertDeletePermission(ProjectResource rsrc) throws AuthException {
    if (!canDelete(rsrc)) {
      throw new AuthException("not allowed to delete project");
    }
  }

  protected boolean canDelete(ProjectResource rsrc) {
    CapabilityControl ctl = userProvider.get().getCapabilities();
    return ctl.canAdministrateServer()
        || ctl.canPerform(pluginName + "-" + DELETE_PROJECT)
        || (ctl.canPerform(pluginName + "-" + DELETE_OWN_PROJECT) && rsrc.getControl().isOwner());
  }

  public void assertCanDelete(ProjectResource rsrc, Input input) throws ResourceConflictException {
    try {
      pcHandler.assertCanDelete(rsrc);
      dbHandler.assertCanDelete(rsrc.getControl().getProject());
      fsHandler.assertCanDelete(rsrc, input == null ? false : input.preserve);
    } catch (CannotDeleteProjectException e) {
      throw new ResourceConflictException(e.getMessage());
    }
  }

  public Collection<String> getWarnings(ProjectResource rsrc) throws OrmException {
    return dbHandler.getWarnings(rsrc.getControl().getProject());
  }

  public void doDelete(ProjectResource rsrc, Input input)
      throws OrmException, IOException, ResourceNotFoundException, ResourceConflictException {
    Project project = rsrc.getControl().getProject();
    boolean preserve = input != null && input.preserve;
    Exception ex = null;
    try {
      if (!preserve || !cfg.projectOnPreserveHidden()) {
        dbHandler.delete(project);
        try {
          fsHandler.delete(project, preserve);
        } catch (RepositoryNotFoundException e) {
          throw new ResourceNotFoundException();
        }
        cacheHandler.delete(project);
      } else {
        hideProject.apply(rsrc);
      }
    } catch (Exception e) {
      ex = e;
      throw e;
    } finally {
      deleteLog.onDelete((IdentifiedUser) userProvider.get(), project.getNameKey(), input, ex);
    }
  }
}
