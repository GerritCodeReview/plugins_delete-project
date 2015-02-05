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

package com.googlesource.gerrit.plugins.deleteproject.database;

import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.query.change.InternalChangeQuery;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.googlesource.gerrit.plugins.deleteproject.CannotDeleteProjectException;

public class Schema77DatabaseDeleteHandler
    extends Schema73DatabaseDeleteHandler {
  private final ReviewDb db;

  @Inject
  public Schema77DatabaseDeleteHandler(ReviewDb db,
      Provider<InternalChangeQuery> queryProvider) {
    super(db, queryProvider);
    this.db = db;
  }

  @Override
  public void assertCanDelete(Project project)
      throws CannotDeleteProjectException, OrmException {
    if (db.submoduleSubscriptions().bySubmoduleProject(project.getNameKey())
        .iterator().hasNext()) {
      throw new CannotDeleteProjectException(
          "Project is subscribed by other projects.");
    }
  }

  public void atomicDelete(Project project) throws OrmException {
    super.atomicDelete(project);

    db.submoduleSubscriptions().delete(
        db.submoduleSubscriptions().bySuperProjectProject(
            project.getNameKey()));
  }
}
