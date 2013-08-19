// Copyright (C) 2012 The Android Open Source Project
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

import com.googlesource.gerrit.plugins.deleteproject.database.DatabaseDeleteHandler;
import com.googlesource.gerrit.plugins.deleteproject.database.Schema73DatabaseDeleteHandler;
import com.googlesource.gerrit.plugins.deleteproject.database.Schema77DatabaseDeleteHandler;
import com.google.gerrit.server.schema.SchemaVersion;
import com.google.gerrit.sshd.PluginCommandModule;

public class DeleteProjectCommandModule extends PluginCommandModule {
  @Override
  protected void configureCommands() {
    int schemaVersion = SchemaVersion.guessVersion(SchemaVersion.C);

    //Injection of version dependent database handlers
    Class<? extends DatabaseDeleteHandler> databaseDeleteHandlerClass = null;
    switch (schemaVersion) {
      case 73:
      case 74:
      case 75:
      case 76:
        databaseDeleteHandlerClass = Schema73DatabaseDeleteHandler.class;
        break;
      case 77:
      case 78:
      case 79:
        databaseDeleteHandlerClass = Schema77DatabaseDeleteHandler.class;
        break;
      default:
        throw new RuntimeException("This version of the delete-project plugin is not "
            + "compatible with your current schema version (Version: "
            + schemaVersion+ "). Please update the plugin.");
    }
    assert databaseDeleteHandlerClass != null: "No database handler set";

    // Actual binding
    bind(DatabaseDeleteHandler.class).to(databaseDeleteHandlerClass);
    command("delete").to(DeleteCommand.class);
  }
}
