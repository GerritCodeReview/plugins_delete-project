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
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.Project;
import com.google.gerrit.entities.ProjectWatchKey;
import com.google.gerrit.server.StarredChangesWriter;
import com.google.gerrit.server.UserInitiated;
import com.google.gerrit.server.account.AccountState;
import com.google.gerrit.server.account.AccountsUpdate;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.index.change.ChangeIndex;
import com.google.gerrit.server.index.change.ChangeIndexCollection;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.notedb.ChangeNotes.Factory.ChangeNotesResult;
import com.google.gerrit.server.project.NoSuchChangeException;
import com.google.gerrit.server.query.account.InternalAccountQuery;
import com.google.inject.Inject;
import com.google.inject.Provider;
import java.io.IOException;
import java.util.List;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.Repository;

public class DatabaseDeleteHandler {
  private static final FluentLogger log = FluentLogger.forEnclosingClass();

  private final StarredChangesWriter starredChangesWriter;
  private final ChangeIndexCollection indexes;
  private final Provider<InternalAccountQuery> accountQueryProvider;
  private final Provider<AccountsUpdate> accountsUpdateProvider;
  private final ChangeNotes.Factory schemaFactoryNoteDb;
  private final GitRepositoryManager repoManager;

  @Inject
  public DatabaseDeleteHandler(
      StarredChangesWriter starredChangesWriter,
      ChangeIndexCollection indexes,
      ChangeNotes.Factory schemaFactoryNoteDb,
      GitRepositoryManager repoManager,
      Provider<InternalAccountQuery> accountQueryProvider,
      @UserInitiated Provider<AccountsUpdate> accountsUpdateProvider) {
    this.starredChangesWriter = starredChangesWriter;
    this.indexes = indexes;
    this.accountQueryProvider = accountQueryProvider;
    this.accountsUpdateProvider = accountsUpdateProvider;
    this.schemaFactoryNoteDb = schemaFactoryNoteDb;
    this.repoManager = repoManager;
  }

  public void delete(Project project) throws IOException {
    deleteChangesFromIndex(project);
    unstarChanges(getChangesListFromNoteDb(project));
    deleteProjectWatches(project);
  }

  private List<Change.Id> getChangesListFromNoteDb(Project project) throws IOException {
    Project.NameKey projectKey = project.nameKey();
    try (Repository repo = repoManager.openRepository(projectKey)) {
      List<Change.Id> changeIds =
          schemaFactoryNoteDb.scan(repo, projectKey).map(ChangeNotesResult::id).collect(toList());
      log.atFine().log(
          "Number of changes in noteDb related to project %s are %d",
          projectKey.get(), changeIds.size());
      return changeIds;
    }
  }

  private void deleteChangesFromIndex(Project project) {
    for (ChangeIndex i : indexes.getWriteIndexes()) {
      i.deleteAllForProject(project.nameKey());
    }
  }

  private void unstarChanges(List<Change.Id> changeIds) {
    for (Change.Id id : changeIds) {
      try {
        starredChangesWriter.unstarAllForChangeDeletion(id);
      } catch (NoSuchChangeException | IOException e) {
        // we can ignore the exception during delete
      }
    }
  }

  private void deleteProjectWatches(Project project) {
    for (AccountState a : accountQueryProvider.get().byWatchedProject(project.nameKey())) {
      Account.Id accountId = a.account().id();
      for (ProjectWatchKey watchKey : a.projectWatches().keySet()) {
        if (project.nameKey().equals(watchKey.project())) {
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
                a.userName().orElse("[unknown]"), project.getName());
          }
        }
      }
    }
  }
}
