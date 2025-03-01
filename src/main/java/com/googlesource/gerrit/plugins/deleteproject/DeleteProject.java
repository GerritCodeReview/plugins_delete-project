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

import com.google.gerrit.common.Nullable;
import com.google.gerrit.entities.Project;
import com.google.gerrit.extensions.registration.DynamicItem;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.config.GerritInstanceId;
import com.google.gerrit.server.events.EventDispatcher;
import com.google.gerrit.server.project.ProjectResource;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import com.googlesource.gerrit.plugins.deleteproject.DeleteProject.Input;
import com.googlesource.gerrit.plugins.deleteproject.cache.CacheDeleteHandler;
import com.googlesource.gerrit.plugins.deleteproject.database.DatabaseDeleteHandler;
import com.googlesource.gerrit.plugins.deleteproject.fs.FilesystemDeleteHandler;
import java.io.IOException;
import org.eclipse.jgit.errors.RepositoryNotFoundException;

@Singleton
class DeleteProject implements RestModifyView<ProjectResource, Input> {
  static class Input {
    boolean preserve;
    boolean force;
  }

  protected final DeletePreconditions preConditions;

  private final DatabaseDeleteHandler dbHandler;
  private final FilesystemDeleteHandler fsHandler;
  private final CacheDeleteHandler cacheHandler;
  private final Provider<CurrentUser> userProvider;
  private final DeleteLog deleteLog;
  private final Configuration cfg;
  private final HideProject hideProject;
  private final DynamicItem<EventDispatcher> dispatcher;
  private final String instanceId;

  @Inject
  DeleteProject(
      DatabaseDeleteHandler dbHandler,
      FilesystemDeleteHandler fsHandler,
      CacheDeleteHandler cacheHandler,
      Provider<CurrentUser> userProvider,
      DeleteLog deleteLog,
      DeletePreconditions preConditions,
      Configuration cfg,
      HideProject hideProject,
      DynamicItem<EventDispatcher> dispatcher,
      @Nullable @GerritInstanceId String instanceId) {
    this.dbHandler = dbHandler;
    this.fsHandler = fsHandler;
    this.cacheHandler = cacheHandler;
    this.userProvider = userProvider;
    this.deleteLog = deleteLog;
    this.preConditions = preConditions;
    this.cfg = cfg;
    this.hideProject = hideProject;
    this.dispatcher = dispatcher;
    this.instanceId = instanceId;
  }

  @Override
  public Response<?> apply(ProjectResource rsrc, Input input) throws IOException, RestApiException {
    preConditions.assertDeletePermission(rsrc);
    preConditions.assertCanBeDeleted(rsrc, input);

    doDelete(rsrc, input);
    return Response.none();
  }

  public void doDelete(ProjectResource rsrc, Input input) throws IOException, RestApiException {
    Project project = rsrc.getProjectState().getProject();
    boolean preserve = input != null && input.preserve;
    Exception ex = null;
    try {
      if (!preserve || !cfg.projectOnPreserveHidden()) {
        dbHandler.delete(project);
        try {
          fsHandler.delete(project.nameKey(), preserve);
        } catch (RepositoryNotFoundException e) {
          throw new ResourceNotFoundException(project.getName(), e);
        }
        cacheHandler.delete(project);
      } else {
        hideProject.apply(rsrc);
      }

      ProjectDeletedEvent event = new ProjectDeletedEvent();
      event.projectName = project.getName();
      event.instanceId = instanceId;

      /**
       * EventBroker checks if user has the permission to access the project. But because this
       * project is already deleted, check will always fail. That's why this event will be delivered
       * only to the unrestricted listeners. Unrestricted events listeners are implementing {@link
       * com.google.gerrit.server.events.EventListener} which allows to listen to events without
       * user visibility restrictions. For example these events are going to be delivered to
       * multi-site {@link com.googlesource.gerrit.plugins.multisite.event.EventHandler}
       */
      dispatcher.get().postEvent(project.nameKey(), event);
    } catch (Exception e) {
      ex = e;
      throw e;
    } finally {
      deleteLog.onDelete((IdentifiedUser) userProvider.get(), project.nameKey(), input, ex);
    }
  }
}
