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

import com.google.common.collect.Lists;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.extensions.registration.DynamicItem;
import com.google.gerrit.reviewdb.client.Branch;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.StarredChangesUtil;
import com.google.gerrit.server.change.AccountPatchReviewStore;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.MergeOpRepoManager;
import com.google.gerrit.server.git.SubmoduleOp;
import com.google.gerrit.server.project.NoSuchChangeException;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.query.change.InternalChangeQuery;
import com.google.gerrit.server.schema.DisabledChangesReviewDbWrapper;
import com.google.gwtorm.jdbc.JdbcSchema;
import com.google.gwtorm.server.OrmException;
import com.google.gwtorm.server.ResultSet;
import com.google.inject.Inject;
import com.google.inject.Provider;

import com.googlesource.gerrit.plugins.deleteproject.CannotDeleteProjectException;

import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;

import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class DatabaseDeleteHandler {
  private final ReviewDb db;
  private final Provider<InternalChangeQuery> queryProvider;
  private final GitRepositoryManager repoManager;
  private final SubmoduleOp.Factory subOpFactory;
  private final Provider<MergeOpRepoManager> ormProvider;
  private final StarredChangesUtil starredChangesUtil;
  private final DynamicItem<AccountPatchReviewStore> accountPatchReviewStore;

  @Inject
  public DatabaseDeleteHandler(ReviewDb db,
      Provider<InternalChangeQuery> queryProvider,
      GitRepositoryManager repoManager,
      SubmoduleOp.Factory subOpFactory,
      Provider<MergeOpRepoManager> ormProvider,
      StarredChangesUtil starredChangesUtil,
      DynamicItem<AccountPatchReviewStore> accountPatchReviewStore) {
    this.db = unwrap(db);
    this.queryProvider = queryProvider;
    this.repoManager = repoManager;
    this.subOpFactory = subOpFactory;
    this.ormProvider = ormProvider;
    this.starredChangesUtil = starredChangesUtil;
    this.accountPatchReviewStore = accountPatchReviewStore;
  }

  public Collection<String> getWarnings(Project project) throws OrmException {
    Collection<String> ret = Lists.newArrayList();

    // Warn against open changes
    List<ChangeData> openChanges =
        queryProvider.get().byProjectOpen(project.getNameKey());
    if (openChanges.iterator().hasNext()) {
      ret.add(project.getName() + " has open changes");
    }

    return ret;
  }

  public void delete(Project project) throws OrmException {
    // TODO(davido): Why not to use 1.7 features?
    // http://docs.oracle.com/javase/specs/jls/se7/html/jls-14.html#jls-14.20.3.2
    Connection conn = ((JdbcSchema) db).getConnection();
    try {
      conn.setAutoCommit(false);
      try {
        atomicDelete(project);
        conn.commit();
      } finally {
        conn.setAutoCommit(true);
      }
    } catch (SQLException e) {
      try {
        conn.rollback();
      } catch (SQLException ex) {
        throw new OrmException(ex);
      }
      throw new OrmException(e);
    }
  }

  private static ReviewDb unwrap(ReviewDb db) {
    if (db instanceof DisabledChangesReviewDbWrapper) {
      db = ((DisabledChangesReviewDbWrapper) db).unsafeGetDelegate();
    }
    return db;
  }

  private final void deleteChanges(List<ChangeData> changeData)
      throws OrmException {
    for (ChangeData cd : changeData) {
      Change.Id id = cd.getId();
      ResultSet<PatchSet> patchSets = null;
      patchSets = db.patchSets().byChange(id);
      if (patchSets != null) {
        deleteFromPatchSets(patchSets);
      }

      // In the future, use schemaVersion to decide what to delete.
      db.patchComments().delete(db.patchComments().byChange(id));
      db.patchSetApprovals().delete(db.patchSetApprovals().byChange(id));
      try {
        starredChangesUtil.unstarAll(cd.project(), id);
      } catch (NoSuchChangeException e) {
        // we can ignore the exception during delete
      }
      db.changeMessages().delete(db.changeMessages().byChange(id));
      db.changes().delete(Collections.singleton(cd.change()));
    }
  }

  private final void deleteFromPatchSets(final ResultSet<PatchSet> patchSets)
      throws OrmException {
    for (PatchSet patchSet : patchSets) {
      accountPatchReviewStore.get().clearReviewed(patchSet.getId());
      db.patchSets().delete(Collections.singleton(patchSet));
    }
  }

  public void assertCanDelete(Project project)
      throws CannotDeleteProjectException {

    Project.NameKey proj = project.getNameKey();
    try (Repository repo = repoManager.openRepository(proj);
         MergeOpRepoManager orm = ormProvider.get()) {
      SubmoduleOp sub = subOpFactory.create(orm);
      for (Ref ref : repo.getRefDatabase().getRefs(
          RefNames.REFS_HEADS).values()) {
        Branch.NameKey b = new Branch.NameKey(proj, ref.getName());
        if (!sub.superProjectSubscriptionsForSubmoduleBranch(b).isEmpty()) {
          throw new CannotDeleteProjectException(
              "Project is subscribed by other projects.");
        }
      }
    } catch (RepositoryNotFoundException e) {
      // we're trying to delete the repository,
      // so this exception should not stop us
    } catch (IOException e) {
      throw new CannotDeleteProjectException(
          "Project is subscribed by other projects.");
    }
  }

  public void atomicDelete(Project project) throws OrmException {
    List<ChangeData> changes =
        queryProvider.get().byProject(project.getNameKey());
    deleteChanges(changes);

    db.accountProjectWatches().delete(
        db.accountProjectWatches().byProject(project.getNameKey()));
  }
}
