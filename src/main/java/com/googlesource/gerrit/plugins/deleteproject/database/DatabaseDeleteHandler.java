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
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.StarredChangesUtil;
import com.google.gerrit.server.UserInitiated;
import com.google.gerrit.server.account.AccountState;
import com.google.gerrit.server.account.AccountsUpdate;
import com.google.gerrit.server.account.ProjectWatches.ProjectWatchKey;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.index.change.ChangeIndexer;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.notedb.ChangeNotes.Factory.ChangeNotesResult;
import com.google.gerrit.server.project.NoSuchChangeException;
import com.google.gerrit.server.query.account.InternalAccountQuery;
import com.google.inject.Inject;
import com.google.inject.Provider;
import java.io.IOException;
import java.util.List;
import org.eclipse.jgit.errors.ConfigInvalidException;

public class DatabaseDeleteHandler {
  private static final FluentLogger log = FluentLogger.forEnclosingClass();

  private final StarredChangesUtil starredChangesUtil;
  private final ChangeIndexer indexer;
  private final Provider<InternalAccountQuery> accountQueryProvider;
  private final Provider<AccountsUpdate> accountsUpdateProvider;
  private final ChangeNotes.Factory schemaFactoryNoteDb;
  private final GitRepositoryManager repoManager;

  @Inject
  public DatabaseDeleteHandler(
      StarredChangesUtil starredChangesUtil,
      ChangeIndexer indexer,
      ChangeNotes.Factory schemaFactoryNoteDb,
      GitRepositoryManager repoManager,
      Provider<InternalAccountQuery> accountQueryProvider,
      @UserInitiated Provider<AccountsUpdate> accountsUpdateProvider) {
    this.starredChangesUtil = starredChangesUtil;
    this.indexer = indexer;
    this.accountQueryProvider = accountQueryProvider;
    this.accountsUpdateProvider = accountsUpdateProvider;
    this.schemaFactoryNoteDb = schemaFactoryNoteDb;
    this.repoManager = repoManager;
  }

  public void delete(Project project) throws IOException {
    atomicDelete(project, getChangesListFromNoteDb(project));
  }

  private List<Change.Id> getChangesListFromNoteDb(Project project) throws IOException {
    Project.NameKey projectKey = project.getNameKey();
    List<Change.Id> changeIds =
        schemaFactoryNoteDb
            .scan(repoManager.openRepository(projectKey), projectKey)
            .map(ChangeNotesResult::id)
            .collect(toList());
    log.atFine().log(
        "Number of changes in noteDb related to project %s are %d",
        projectKey.get(), changeIds.size());
    return changeIds;
  }

  private void deleteChanges(Project.NameKey project, List<Change.Id> changeIds) {

    for (Change.Id id : changeIds) {
      try {
        starredChangesUtil.unstarAll(project, id);
      } catch (NoSuchChangeException e) {
        // we can ignore the exception during delete
      }
      // Delete from the secondary index
      indexer.delete(id);
    }
  }

  public void atomicDelete(Project project, List<Change.Id> changeIds) {

    deleteChanges(project.getNameKey(), changeIds);

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
