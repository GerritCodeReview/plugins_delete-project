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
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.config.SitePaths;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.project.ProjectControl;
import com.google.gerrit.sshd.SshCommand;
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

  @Inject
  private ReviewDb db;

  @Inject
  private ProjectCache projectCache;

  @Inject
  private GitRepositoryManager repoManager;

  @Inject
  protected DeleteCommand(final SitePaths site,
      @GerritServerConfig final Config cfg) {
    gitDir = site.resolve(cfg.getString("gerrit", null, "basePath"));
  }

  @Override
  public void run() throws UnloggedFailure, Failure, Exception {
    if (!yesReallyDelete) {
      stdout.print("Really delete " + project.getProject().getName() + "?\n");
      stdout.print("This is an operation which perminately deletes data. "
          + "This cannot be undone!\n");
      stdout.print("If you are sure you wish to delete this project, re-run\n"
          + "with the --yes-really-delete flag.\n");
      return;
    }

    if (!force
        && db.changes().byProjectOpenAll(project.getProject().getNameKey())
            .iterator().hasNext()) {
      stdout.print("There are open changes for this project. To really\n"
          + "delete it, re-run with the --force flag.\n");
      return;
    }

    // Remove from the jgit cache
    final Repository repository = repoManager.openRepository(project
        .getProject().getNameKey());
    if (repository == null) {
      stdout.print("There was an error finding the project.\n");
      return;
    }

    repository.close();
    RepositoryCache.close(repository);

    // Delete the repository from disk
    File parentFile = repository.getDirectory().getParentFile();
    if (!recursiveDelete(repository.getDirectory())) {
      stdout.print("Error trying to delete "
          + repository.getDirectory().getAbsolutePath() + "\n");
      return;
    }

    // Delete parent folders while they are (now) empty
    recursiveDeleteParent(parentFile, gitDir);

    // Clean up the cache
    projectCache.remove(project.getProject());

    // Delete anything in the database related to the project. Surround
    // everything with generic try/catch so this will continue to work as more
    // data is moved out of the database and into the repository.
    try {
      ResultSet<Change> changes = db.changes().byProject(
          project.getProject().getNameKey());
      for (Change change : changes) {
        Change.Id id = change.getId();

        try {
          db.patchComments().delete(db.patchComments().byChange(id));
        } catch (Exception e) {
        }

        try {
          db.patchSetApprovals().delete(db.patchSetApprovals().byChange(id));
        } catch (Exception e) {
        }

        try {
          ResultSet<PatchSet> patchSets = db.patchSets().byChange(id);
          for (PatchSet patchSet : patchSets) {
            try {
              db.patchSetAncestors().delete(
                  db.patchSetAncestors().byPatchSet(patchSet.getId()));
            } catch (Exception e) {
            }

            try {
              db.accountPatchReviews().delete(
                  db.accountPatchReviews().byPatchSet(patchSet.getId()));
            } catch (Exception e) {
            }

            try {
              db.patchSets().delete(Collections.singleton(patchSet));
            } catch (Exception e) {
            }
          }
        } catch (Exception e) {
        }

        try {
          db.changeMessages().delete(db.changeMessages().byChange(id));
        } catch (Exception e) {
        }

        try {
          db.starredChanges().delete(db.starredChanges().byChange(id));
        } catch (Exception e) {
        }

        try {
          db.trackingIds().delete(db.trackingIds().byChange(id));
        } catch (Exception e) {
        }

        try {
          db.changes().delete(Collections.singleton(change));
        } catch (Exception e) {
        }
      }
      try {
        db.accountProjectWatches().delete(
            db.accountProjectWatches().byProject(
                project.getProject().getNameKey()));
      } catch (Exception e) {
      }
    } catch (Exception e) {
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
   *
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
}
