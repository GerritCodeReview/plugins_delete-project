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

package com.google.gerrit.plugins.database;

import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.inject.Inject;

public class Schema77DatabaseDeleteHandler
    extends Schema73DatabaseDeleteHandler {
  private final ReviewDb db;

  @Inject
  public Schema77DatabaseDeleteHandler(ReviewDb db) {
    super(db);
    this.db = db;
  }

  @Override
  public void assertCanDelete(Project project) throws Exception {
    if (db.submoduleSubscriptions().bySuperProjectProject(project.getNameKey())
        .iterator().hasNext()) {
      throw new Exception("Project has subscribed submodules.");
    }

    if (db.submoduleSubscriptions().bySubmoduleProject(project.getNameKey())
        .iterator().hasNext()) {
      throw new Exception("Project is subscribed by other projects.");
    }
  }
}
