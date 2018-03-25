// Copyright (C) 2018 The Android Open Source Project
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

import static com.googlesource.gerrit.plugins.deleteproject.DeleteOwnProjectCapability.DELETE_OWN_PROJECT;
import static com.googlesource.gerrit.plugins.deleteproject.DeleteProjectCapability.DELETE_PROJECT;

import com.google.gerrit.extensions.annotations.PluginName;
import com.google.gerrit.extensions.common.ProjectInfo;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.reviewdb.client.Branch;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.account.CapabilityControl;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.MergeOpRepoManager;
import com.google.gerrit.server.git.SubmoduleOp;
import com.google.gerrit.server.project.ListChildProjects;
import com.google.gerrit.server.project.ProjectResource;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.query.change.InternalChangeQuery;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.googlesource.gerrit.plugins.deleteproject.DeleteProject.Input;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;

class DeletePreconditions {
  private final Configuration config;
  private final Provider<ListChildProjects> listChildProjectsProvider;
  private final Provider<MergeOpRepoManager> mergeOpProvider;
  private final String pluginName;
  private final Provider<InternalChangeQuery> queryProvider;
  private final GitRepositoryManager repoManager;
  private final SubmoduleOp.Factory subOpFactory;
  private final Provider<CurrentUser> userProvider;
  private final ProtectedProjects protectedProjects;

  @Inject
  public DeletePreconditions(
      Configuration config,
      Provider<ListChildProjects> listChildProjectsProvider,
      Provider<MergeOpRepoManager> mergeOpProvider,
      @PluginName String pluginName,
      Provider<InternalChangeQuery> queryProvider,
      GitRepositoryManager repoManager,
      SubmoduleOp.Factory subOpFactory,
      Provider<CurrentUser> userProvider,
      ProtectedProjects protectedProjects) {
    this.config = config;
    this.listChildProjectsProvider = listChildProjectsProvider;
    this.mergeOpProvider = mergeOpProvider;
    this.pluginName = pluginName;
    this.queryProvider = queryProvider;
    this.repoManager = repoManager;
    this.subOpFactory = subOpFactory;
    this.userProvider = userProvider;
    this.protectedProjects = protectedProjects;
  }

  void assertDeletePermission(ProjectResource rsrc) throws AuthException {
    if (!canDelete(rsrc)) {
      throw new AuthException("not allowed to delete project");
    }
  }

  boolean canDelete(ProjectResource rsrc) {
    CapabilityControl ctl = userProvider.get().getCapabilities();
    return ctl.canAdministrateServer()
        || ctl.canPerform(pluginName + "-" + DELETE_PROJECT)
        || (ctl.canPerform(pluginName + "-" + DELETE_OWN_PROJECT) && rsrc.getControl().isOwner());
  }

  void assertCanBeDeleted(ProjectResource rsrc, Input input) throws ResourceConflictException {
    try {
      protectedProjects.assertIsNotProtected(rsrc);
      assertHasNoChildProjects(rsrc);
      Project.NameKey projectNameKey = rsrc.getNameKey();
      assertIsNotSubmodule(projectNameKey);
      assertDeleteWithTags(projectNameKey, input == null || input.preserve);
      assertHasOpenChanges(projectNameKey, input == null || input.force);
    } catch (CannotDeleteProjectException e) {
      throw new ResourceConflictException(e.getMessage());
    }
  }

  public void assertHasOpenChanges(Project.NameKey projectNameKey, boolean force)
      throws CannotDeleteProjectException {
    if (!force) {
      try {
        List<ChangeData> openChanges = queryProvider.get().byProjectOpen(projectNameKey);
        if (!openChanges.isEmpty()) {
          throw new CannotDeleteProjectException(
              String.format("Project '%s' has open changes.", projectNameKey.get()));
        }
      } catch (OrmException e) {
        throw new CannotDeleteProjectException(
            String.format("Unable to verify if %s has open changes.", projectNameKey.get()));
      }
    }
  }

  private void assertHasNoChildProjects(ProjectResource rsrc) throws CannotDeleteProjectException {
    List<ProjectInfo> children = listChildProjectsProvider.get().apply(rsrc);
    if (!children.isEmpty()) {
      throw new CannotDeleteProjectException(
          "Cannot delete project because it has children: "
              + String.join(
                  ", ", children.stream().map(info -> info.name).collect(Collectors.toList())));
    }
  }

  private void assertIsNotSubmodule(Project.NameKey projectNameKey)
      throws CannotDeleteProjectException {
    try (Repository repo = repoManager.openRepository(projectNameKey);
        MergeOpRepoManager mergeOp = mergeOpProvider.get()) {
      Set<Branch.NameKey> branches = new HashSet<>();
      for (Ref ref : repo.getRefDatabase().getRefs(RefNames.REFS_HEADS).values()) {
        branches.add(new Branch.NameKey(projectNameKey, ref.getName()));
      }
      SubmoduleOp sub = subOpFactory.create(branches, mergeOp);
      for (Branch.NameKey b : branches) {
        if (!sub.superProjectSubscriptionsForSubmoduleBranch(b).isEmpty()) {
          throw new CannotDeleteProjectException("Project is subscribed by other projects.");
        }
      }
    } catch (RepositoryNotFoundException e) {
      // we're trying to delete the repository,
      // so this exception should not stop us
    } catch (IOException e) {
      throw new CannotDeleteProjectException("Project is subscribed by other projects.");
    }
  }

  private void assertDeleteWithTags(Project.NameKey projectNameKey, boolean preserveGitRepository)
      throws CannotDeleteProjectException {
    if (!preserveGitRepository && !config.deletionWithTagsAllowed()) {
      assertHasNoTags(projectNameKey);
    }
  }

  private void assertHasNoTags(Project.NameKey projectNameKey) throws CannotDeleteProjectException {
    try (Repository repo = repoManager.openRepository(projectNameKey)) {
      if (!repo.getRefDatabase().getRefs(Constants.R_TAGS).isEmpty()) {
        throw new CannotDeleteProjectException(
            String.format("Project %s has tags", projectNameKey));
      }
    } catch (IOException e) {
      throw new CannotDeleteProjectException(e);
    }
  }
}
