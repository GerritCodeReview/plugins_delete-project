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

package com.google.gerrit.plugins;

import java.io.File;
import java.sql.Connection;
import java.util.Collections;

import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.RepositoryCache;
import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.Option;

import com.google.gerrit.common.data.GlobalCapability;
import com.google.gerrit.extensions.annotations.RequiresCapability;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.config.AllProjectsNameProvider;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.config.SitePaths;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.project.ProjectControl;
import com.google.gerrit.server.schema.SchemaVersion;
import com.google.gerrit.server.schema.Schema_73;
import com.google.gerrit.sshd.SshCommand;
import com.google.gwtorm.jdbc.JdbcSchema;
import com.google.gwtorm.server.OrmException;
import com.google.gwtorm.server.ResultSet;
import com.google.inject.Inject;

@RequiresCapability(GlobalCapability.KILL_TASK)
public final class DeleteCommand extends SshCommand {

  @Argument(index = 0, required = true, metaVar = "NAME", usage = "project to delete")
  private ProjectControl project;

  @Option(name = "--yes-really-delete", usage = "confirmation to delete the project")
  private boolean yesReallyDelete;

  @Option(name = "--force", usage = "delete the project even if it has open changes")
  private boolean force = false;

  private final File gitDir;

  private final ReviewDb db;

  private final ProjectCache projectCache;

  private final GitRepositoryManager repoManager;

  private final SitePaths site;

  private int schemaVersion;

  @Inject
  protected DeleteCommand(ReviewDb db, ProjectCache projectCache,
      GitRepositoryManager repoManager, SitePaths site,
      @GerritServerConfig Config cfg) {
    gitDir = site.resolve(cfg.getString("gerrit", null, "basePath"));
    this.db = db;
    this.projectCache = projectCache;
    this.repoManager = repoManager;
    this.site = site;
  }

  @Override
  public void run() throws UnloggedFailure, Failure, Exception {
    Connection conn = ((JdbcSchema) db).getConnection();
    conn.setAutoCommit(false);
    schemaVersion = getSchemaVersion();
    if (schemaVersion == 0) {
      throw new UnloggedFailure("This version of the delete project plugin is not "
          + "compatible with your schema version. Please update the plugin.");
    }
    try {
      doDelete(conn);
      conn.commit();
    } catch (Exception e) {
      conn.rollback();
      throw e;
    } finally {
      conn.setAutoCommit(true);
    }
  }

  private void doDelete(Connection conn) throws UnloggedFailure, Failure, Exception {
    // Don't let people delete All-Projects, that's stupid
    final String projectName = project.getProject().getName();
    if (project.getProject().getName().endsWith(AllProjectsNameProvider.DEFAULT)) {
      throw new UnloggedFailure("Perhaps you meant to rm -fR " + site.site_path);
    }

    // TODO(mch): This is an ugly hack, ideally we could do it with SubmoduleSubscriptionAccess
    if (conn.createStatement().executeQuery("SELECT * FROM submodule_subscriptions WHERE "
        + "super_project_project_name = '" + projectName + "'").first()) {
      throw new UnloggedFailure("Cannot delete project " + projectName +
          ", it has subscribed submodules." );
    }

    if (!yesReallyDelete) {
      stdout.print("Really delete " + project.getProject().getName() + "?\n");
      stdout.print("This is an operation which permanently deletes data. "
          + "This cannot be undone!\n");
      stdout.print("If you are sure you wish to delete this project, re-run\n"
          + "with the --yes-really-delete flag.\n");
      return;
    }

    if (!force
        && db.changes().byProjectOpenAll(project.getProject().getNameKey())
            .iterator().hasNext()) {
      throw new UnloggedFailure("There are open changes for this project. To really\n"
          + "delete it, re-run with the --force flag.");
    }

    // Remove from the jgit cache
    final Repository repository = repoManager.openRepository(project
        .getProject().getNameKey());
    if (repository == null) {
      throw new UnloggedFailure("There was an error finding the project.");
    }

    repository.close();
    RepositoryCache.close(repository);

    // Delete the repository from disk
    File parentFile = repository.getDirectory().getParentFile();
    if (!recursiveDelete(repository.getDirectory())) {
      throw new UnloggedFailure("Error trying to delete "
          + repository.getDirectory().getAbsolutePath());
    }

    // Delete parent folders while they are (now) empty
    recursiveDeleteParent(parentFile, gitDir);

    // Clean up the cache
    projectCache.remove(project.getProject());

    ResultSet<Change> changes = null;
    try {
      changes = db.changes().byProject(project.getProject().getNameKey());
    } catch (OrmException ex) {
    }

    if (changes != null) {
      deleteFromDatabase(changes);
    }
  }

  private final void deleteFromDatabase(final ResultSet<Change> changes)
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
    db.accountProjectWatches()
        .delete(
            db.accountProjectWatches().byProject(
                project.getProject().getNameKey()));
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

  /**
   * Recursively delete the specified file and all of its contents.
   *
   * @return true on success, false if there was an error.
   */
  private boolean recursiveDelete(File file) {
    if (file.isDirectory()) {
      for (File f : file.listFiles()) {
        if (!recursiveDelete(f)) {
          return false;
        }
      }
    }
    return file.delete();
  }

  /**
   * Recursively delete the specified file and its parent files until we hit the
   * file {@code Until} or the parent file is populated. This is used when we
   * have a tree structure such as a/b/c/d.git and a/b/e.git - if we delete
   * a/b/c/d.git, we no longer need a/b/c/.
   */
  private void recursiveDeleteParent(File file, File until) {
    if (file.equals(until)) {
      return;
    }
    if (file.listFiles().length == 0) {
      File parent = file.getParentFile();
      file.delete();
      recursiveDeleteParent(parent, until);
    }
  }

  /**
   * Get the schema version we currently have installed. 0 indicates the schema
   * version is unsupported by the plugin.
   *
   * Please contribute new schema handling.
   *
   * @return int
   */
  private static int getSchemaVersion() {
    int ourSchema = 0;
    if (SchemaVersion.C == Schema_73.class) {
      // Gerrit 2.5
      ourSchema = 73;
    }
    return ourSchema;
  }
}
