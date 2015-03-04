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

import static com.google.gerrit.server.project.ProjectResource.PROJECT_KIND;
import static com.googlesource.gerrit.plugins.deleteproject.DeleteOwnProjectCapability.DELETE_OWN_PROJECT;
import static com.googlesource.gerrit.plugins.deleteproject.DeleteProjectCapability.DELETE_PROJECT;

import com.google.gerrit.extensions.annotations.Exports;
import com.google.gerrit.extensions.config.CapabilityDefinition;
import com.google.gerrit.extensions.events.LifecycleListener;
import com.google.gerrit.extensions.restapi.RestApiModule;
import com.google.gerrit.server.schema.SchemaVersion;
import com.google.inject.AbstractModule;
import com.google.inject.internal.UniqueAnnotations;

import com.googlesource.gerrit.plugins.deleteproject.cache.CacheDeleteHandler;
import com.googlesource.gerrit.plugins.deleteproject.database.DatabaseDeleteHandler;
import com.googlesource.gerrit.plugins.deleteproject.database.Schema73DatabaseDeleteHandler;
import com.googlesource.gerrit.plugins.deleteproject.database.Schema77DatabaseDeleteHandler;
import com.googlesource.gerrit.plugins.deleteproject.fs.DeleteTrashFolders;
import com.googlesource.gerrit.plugins.deleteproject.fs.FilesystemDeleteHandler;
import com.googlesource.gerrit.plugins.deleteproject.projectconfig.ProjectConfigDeleteHandler;

public class Module extends AbstractModule {

  @Override
  protected void configure() {
    bind(LifecycleListener.class)
        .annotatedWith(UniqueAnnotations.create())
        .to(DeleteLog.class);
    bind(LifecycleListener.class)
        .annotatedWith(UniqueAnnotations.create())
        .to(DeleteTrashFolders.class);
    bind(CacheDeleteHandler.class);
    bind(CapabilityDefinition.class)
        .annotatedWith(Exports.named(DELETE_PROJECT))
        .to(DeleteProjectCapability.class);
    bind(CapabilityDefinition.class)
        .annotatedWith(Exports.named(DELETE_OWN_PROJECT))
        .to(DeleteOwnProjectCapability.class);
    bind(DatabaseDeleteHandler.class)
        .to(registerDatabaseHandler());
    bind(FilesystemDeleteHandler.class);
    bind(ProjectConfigDeleteHandler.class);
    install(new RestApiModule() {
      @Override
      protected void configure() {
        delete(PROJECT_KIND)
            .to(DeleteProject.class);
        post(PROJECT_KIND, "delete")
            .to(DeleteAction.class);
      }
    });
  }

  private Class<? extends DatabaseDeleteHandler> registerDatabaseHandler() {
    Class<? extends DatabaseDeleteHandler> databaseDeleteHandlerClass = null;
    int schemaVersion = SchemaVersion.guessVersion(SchemaVersion.C);

    if (schemaVersion < 73) {
      throw new RuntimeException("The delete-project plugin is not "
          + "compatible with your current schema version (Version: "
          + schemaVersion + ").");
    } else if (schemaVersion < 77) {
      databaseDeleteHandlerClass = Schema73DatabaseDeleteHandler.class;
    } else {
      databaseDeleteHandlerClass = Schema77DatabaseDeleteHandler.class;
    }
    assert databaseDeleteHandlerClass != null: "No database handler set";
    return databaseDeleteHandlerClass;
  }
}
