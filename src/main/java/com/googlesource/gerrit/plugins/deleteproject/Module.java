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
import static com.googlesource.gerrit.plugins.deleteproject.DeleteProjectCapability.DELETE_PROJECT;

import com.google.gerrit.extensions.annotations.Exports;
import com.google.gerrit.extensions.config.CapabilityDefinition;
import com.google.gerrit.extensions.restapi.RestApiModule;
import com.google.gerrit.server.schema.SchemaVersion;
import com.google.inject.AbstractModule;
import com.googlesource.gerrit.plugins.deleteproject.cache.CacheDeleteHandler;
import com.googlesource.gerrit.plugins.deleteproject.database.DatabaseDeleteHandler;
import com.googlesource.gerrit.plugins.deleteproject.database.Schema73DatabaseDeleteHandler;
import com.googlesource.gerrit.plugins.deleteproject.database.Schema77DatabaseDeleteHandler;
import com.googlesource.gerrit.plugins.deleteproject.fs.FilesystemDeleteHandler;

public class Module extends AbstractModule {

  @Override
  protected void configure() {
    bind(CacheDeleteHandler.class);
    bind(CapabilityDefinition.class)
        .annotatedWith(Exports.named(DELETE_PROJECT))
        .to(DeleteProjectCapability.class);
    bind(DatabaseDeleteHandler.class)
        .to(SchemaVersion.guessVersion(SchemaVersion.C) < 77
            ? Schema73DatabaseDeleteHandler.class
            : Schema77DatabaseDeleteHandler.class);
    bind(FilesystemDeleteHandler.class);
    install(new RestApiModule() {
      @Override
      protected void configure() {
        delete(PROJECT_KIND)
            .to(DeleteProject.class);
        post(PROJECT_KIND, "delete")
            .to(DeleteProject.class);
      }
    });
  }
}
