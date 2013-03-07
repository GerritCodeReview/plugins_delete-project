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

import java.sql.Connection;
import java.util.Collection;
import java.util.Collections;

import com.google.common.collect.Lists;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gwtorm.jdbc.JdbcSchema;
import com.google.gwtorm.server.OrmException;
import com.google.gwtorm.server.ResultSet;
import com.google.inject.Inject;

public class Schema73DatabaseDeleteHandler implements DatabaseDeleteHandler {
  private final ReviewDb db;

  @Inject
  public Schema73DatabaseDeleteHandler(ReviewDb db) {
    this.db = db;
  }

  @Override
  public void assertCanDelete(Project project) throws Exception {
    final Connection conn = ((JdbcSchema) db).getConnection();
    final String projectName = project.getName();

    // TODO(mch): This is an ugly hack, ideally we could do it with SubmoduleSubscriptionAccess
    if (conn.createStatement().executeQuery("SELECT * FROM submodule_subscriptions WHERE "
        + "super_project_project_name = '" + projectName + "'").first()) {
      throw new Exception("Cannot delete project " + projectName +
          ", it has subscribed submodules." );
    }
  }

  @Override
  public Collection<String> getWarnings(Project project) throws OrmException {
    Collection<String> ret = Lists.newArrayList();

    // Warn against open changes
    ResultSet<Change> openChanges = db.changes().byProjectOpenAll(project.getNameKey());
    if (openChanges.iterator().hasNext()) {
      ret.add(project.getName() + " has open changes");
    }

    return ret;
  }

  @Override
  public void delete(Project project) throws Exception {
    Connection conn = ((JdbcSchema) db).getConnection();
    conn.setAutoCommit(false);
    try {
      atomicDelete(project);
      conn.commit();
    } catch (Exception e) {
      conn.rollback();
      throw e;
    } finally {
      conn.setAutoCommit(true);
    }
  }

  public void atomicDelete(Project project) throws OrmException {
    ResultSet<Change> changes = null;
    changes = db.changes().byProject(project.getNameKey());
    deleteChanges(changes);

    db.accountProjectWatches()
    .delete(
        db.accountProjectWatches().byProject(
            project.getNameKey()));
  }

  private final void deleteChanges(final ResultSet<Change> changes)
      throws OrmException {
    for (Change change : changes) {
      Change.Id id = change.getId();
      ResultSet<PatchSet> patchSets = null;
      patchSets = db.patchSets().byChange(id);
      if (patchSets != null) {
        deleteFromPatchSets(patchSets, change);
      }

      // In the future, use schemaVersion to decide what to delete.
      db.patchComments().delete(db.patchComments().byChange(id));
      db.patchSetApprovals().delete(db.patchSetApprovals().byChange(id));
      db.changeMessages().delete(db.changeMessages().byChange(id));
      db.starredChanges().delete(db.starredChanges().byChange(id));
      db.trackingIds().delete(db.trackingIds().byChange(id));
      db.changes().delete(Collections.singleton(change));
    }
  }

  private final void deleteFromPatchSets(final ResultSet<PatchSet> patchSets,
      final Change change) throws OrmException {
    for (PatchSet patchSet : patchSets) {
      db.patchSetAncestors().delete(
          db.patchSetAncestors().byPatchSet(patchSet.getId()));

      db.accountPatchReviews().delete(
          db.accountPatchReviews().byPatchSet(patchSet.getId()));

      db.patchSets().delete(Collections.singleton(patchSet));
    }
  }
}
