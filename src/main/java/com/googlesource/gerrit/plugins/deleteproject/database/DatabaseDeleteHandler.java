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

import static java.util.Collections.singleton;
import static java.util.stream.Collectors.toList;

import com.google.common.flogger.FluentLogger;
import com.google.gerrit.extensions.registration.DynamicItem;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.reviewdb.server.ReviewDbUtil;
import com.google.gerrit.server.StarredChangesUtil;
import com.google.gerrit.server.UserInitiated;
import com.google.gerrit.server.account.AccountState;
import com.google.gerrit.server.account.AccountsUpdate;
import com.google.gerrit.server.account.ProjectWatches.ProjectWatchKey;
import com.google.gerrit.server.change.AccountPatchReviewStore;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.index.change.ChangeIndexer;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.notedb.ChangeNotes.Factory.ChangeNotesResult;
import com.google.gerrit.server.notedb.NotesMigration;
import com.google.gerrit.server.project.NoSuchChangeException;
import com.google.gerrit.server.query.account.InternalAccountQuery;
import com.google.gwtorm.jdbc.JdbcSchema;
import com.google.gwtorm.server.OrmException;
import com.google.gwtorm.server.ResultSet;
import com.google.inject.Inject;
import com.google.inject.Provider;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.eclipse.jgit.errors.ConfigInvalidException;

public class DatabaseDeleteHandler {
  private static final FluentLogger log = FluentLogger.forEnclosingClass();

  private final Provider<ReviewDb> dbProvider;
  private final StarredChangesUtil starredChangesUtil;
  private final DynamicItem<AccountPatchReviewStore> accountPatchReviewStore;
  private final ChangeIndexer indexer;
  private final Provider<InternalAccountQuery> accountQueryProvider;
  private final Provider<AccountsUpdate> accountsUpdateProvider;
  private final ChangeNotes.Factory schemaFactoryNoteDb;
  private final GitRepositoryManager repoManager;
  private final NotesMigration migration;

  @Inject
  public DatabaseDeleteHandler(
      Provider<ReviewDb> dbProvider,
      StarredChangesUtil starredChangesUtil,
      DynamicItem<AccountPatchReviewStore> accountPatchReviewStore,
      ChangeIndexer indexer,
      ChangeNotes.Factory schemaFactoryNoteDb,
      NotesMigration migration,
      GitRepositoryManager repoManager,
      Provider<InternalAccountQuery> accountQueryProvider,
      @UserInitiated Provider<AccountsUpdate> accountsUpdateProvider) {
    this.dbProvider = dbProvider;
    this.starredChangesUtil = starredChangesUtil;
    this.accountPatchReviewStore = accountPatchReviewStore;
    this.indexer = indexer;
    this.accountQueryProvider = accountQueryProvider;
    this.accountsUpdateProvider = accountsUpdateProvider;
    this.schemaFactoryNoteDb = schemaFactoryNoteDb;
    this.repoManager = repoManager;
    this.migration = migration;
  }

  public void delete(Project project) throws OrmException, IOException {
    ReviewDb db = ReviewDbUtil.unwrapDb(dbProvider.get());
    if (isReviewDb()) {
      Connection conn = ((JdbcSchema) db).getConnection();
      try {
        conn.setAutoCommit(false);
        try {
          atomicDelete(db, project, getChangesList(project, conn));
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
    } else {
      atomicDelete(db, project, getChangesListFromNoteDb(project));
    }
  }

  private boolean isReviewDb() {
    return !migration.disableChangeReviewDb();
  }

  private List<Change.Id> getChangesList(Project project, Connection conn) throws SQLException {
    try (PreparedStatement changesForProject =
             conn.prepareStatement("SELECT change_id FROM changes WHERE dest_project_name = ?")) {
      changesForProject.setString(1, project.getName());
      try (java.sql.ResultSet resultSet = changesForProject.executeQuery()) {
        List<Change.Id> changeIds = new ArrayList<>();
        while (resultSet.next()) {
          changeIds.add(new Change.Id(resultSet.getInt(1)));
        }
        return changeIds;
      }
    } catch (SQLException e) {
      throw new SQLException("Unable to get list of changes for project " + project.getName(), e);
    }
  }

  private List<Change.Id> getChangesListFromNoteDb(Project project) throws IOException {
    Project.NameKey projectKey = project.getNameKey();
    List<Change.Id> changeIds =
        schemaFactoryNoteDb
            .scan(repoManager.openRepository(projectKey), dbProvider.get(), projectKey)
            .map(ChangeNotesResult::id)
            .collect(toList());
    log.atInfo().log(
        "Number of changes in noteDb related to project {} are {}",
        projectKey.get(),
        changeIds.size());
    return changeIds;
  }

  private void deleteChanges(ReviewDb db, Project.NameKey project, List<Change.Id> changeIds)
      throws OrmException {

    for (Change.Id id : changeIds) {
      try {
        starredChangesUtil.unstarAll(project, id);
      } catch (NoSuchChangeException e) {
        // we can ignore the exception during delete
      }
      if (isReviewDb()) {
        ResultSet<PatchSet> patchSets = db.patchSets().byChange(id);
        if (patchSets!=null) {
          deleteFromPatchSets(db, patchSets);
        }

        // In the future, use schemaVersion to decide what to delete.
        db.patchComments().delete(db.patchComments().byChange(id));
        db.patchSetApprovals().delete(db.patchSetApprovals().byChange(id));

        db.changeMessages().delete(db.changeMessages().byChange(id));
        db.changes().deleteKeys(Collections.singleton(id));
      }
      // Delete from the secondary index
      try {
        indexer.delete(id);
      } catch (IOException e) {
        log.atSevere().withCause(e).log("Failed to delete change %s from index", id);
      }
    }
  }

  private void deleteFromPatchSets(ReviewDb db, ResultSet<PatchSet> patchSets) throws OrmException {
    for (PatchSet patchSet : patchSets) {
      accountPatchReviewStore.get().clearReviewed(patchSet.getId());
      db.patchSets().delete(Collections.singleton(patchSet));
    }
  }

  public void atomicDelete(ReviewDb db, Project project, List<Change.Id> changeIds)
      throws OrmException {

    deleteChanges(db, project.getNameKey(), changeIds);

    for (AccountState a : accountQueryProvider.get().byWatchedProject(project.getNameKey())) {
      Account.Id accountId = a.getAccount().getId();
      for (ProjectWatchKey watchKey : a.getProjectWatches().keySet()) {
        if (project.getNameKey().equals(watchKey.project())) {
          try {
            accountsUpdateProvider
                .get()
                .update(
                    "Delete Project Watches via API",
                    accountId,
                    u -> u.deleteProjectWatches(singleton(watchKey)));
          } catch (IOException | ConfigInvalidException e) {
            log.atSevere().withCause(e).log(
                "Removing watch entry for user %s in project %s failed.",
                a.getUserName(), project.getName());
          }
        }
      }
    }
  }
}
