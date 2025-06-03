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

import static com.google.gerrit.entities.RefNames.REFS_HEADS;
import static com.google.gerrit.entities.RefNames.REFS_TAGS;
import static com.googlesource.gerrit.plugins.deleteproject.DeleteOwnProjectCapability.DELETE_OWN_PROJECT;
import static com.googlesource.gerrit.plugins.deleteproject.DeleteProjectCapability.DELETE_PROJECT;
import static java.util.stream.Collectors.toSet;

import com.google.common.collect.Iterables;
import com.google.gerrit.entities.BranchNameKey;
import com.google.gerrit.entities.Project;
import com.google.gerrit.exceptions.StorageException;
import com.google.gerrit.extensions.annotations.PluginName;
import com.google.gerrit.extensions.api.access.PluginPermission;
import com.google.gerrit.extensions.common.ProjectInfo;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.permissions.GlobalPermission;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.ProjectPermission;
import com.google.gerrit.server.project.ProjectResource;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.query.change.InternalChangeQuery;
import com.google.gerrit.server.restapi.project.children.ListChildProjects;
import com.google.gerrit.server.submit.MergeOpRepoManager;
import com.google.gerrit.server.submit.SubmoduleConflictException;
import com.google.gerrit.server.submit.SubscriptionGraph;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import com.googlesource.gerrit.plugins.deleteproject.DeleteProject.Input;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.lib.Repository;

@Singleton
class DeletePreconditions {
  private final Configuration config;
  private final Provider<ListChildProjects> listChildProjectsProvider;
  private final Provider<MergeOpRepoManager> mergeOpProvider;
  private final String pluginName;
  private final Provider<InternalChangeQuery> queryProvider;
  private final GitRepositoryManager repoManager;
  private final SubscriptionGraph.Factory subscriptionGraphFactory;
  private final Provider<CurrentUser> userProvider;
  private final ProtectedProjects protectedProjects;
  private final PermissionBackend permissionBackend;

  @Inject
  public DeletePreconditions(
      Configuration config,
      Provider<ListChildProjects> listChildProjectsProvider,
      Provider<MergeOpRepoManager> mergeOpProvider,
      @PluginName String pluginName,
      Provider<InternalChangeQuery> queryProvider,
      GitRepositoryManager repoManager,
      SubscriptionGraph.Factory subscriptionGraphFactory,
      Provider<CurrentUser> userProvider,
      ProtectedProjects protectedProjects,
      PermissionBackend permissionBackend) {
    this.config = config;
    this.listChildProjectsProvider = listChildProjectsProvider;
    this.mergeOpProvider = mergeOpProvider;
    this.pluginName = pluginName;
    this.queryProvider = queryProvider;
    this.repoManager = repoManager;
    this.subscriptionGraphFactory = subscriptionGraphFactory;
    this.userProvider = userProvider;
    this.protectedProjects = protectedProjects;
    this.permissionBackend = permissionBackend;
  }

  void assertDeletePermission(ProjectResource rsrc) throws AuthException {
    if (!canDelete(rsrc)) {
      throw new AuthException("not allowed to delete project");
    }
  }

  protected boolean canDelete(ProjectResource rsrc) {
    PermissionBackend.WithUser userPermission = permissionBackend.user(userProvider.get());
    return userPermission.testOrFalse(GlobalPermission.ADMINISTRATE_SERVER)
        || userPermission.testOrFalse(new PluginPermission(pluginName, DELETE_PROJECT))
        || (userPermission.testOrFalse(new PluginPermission(pluginName, DELETE_OWN_PROJECT))
            && userPermission
                .project(rsrc.getNameKey())
                .testOrFalse(ProjectPermission.WRITE_CONFIG));
  }

  void assertCanBeDeleted(ProjectResource rsrc, Input input) throws ResourceConflictException {
    try {
      protectedProjects.assertIsNotProtected(rsrc);
      assertHasNoChildProjects(rsrc);
      Project.NameKey projectNameKey = rsrc.getNameKey();
      assertIsNotSubmodule(projectNameKey);
      assertDeleteWithTags(projectNameKey, input != null && input.preserve);
      assertHasOpenChanges(projectNameKey, input != null && input.force);
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
      } catch (StorageException e) {
        throw new CannotDeleteProjectException(
            String.format("Unable to verify if '%s' has open changes.", projectNameKey.get()), e);
      }
    }
  }

  private void assertHasNoChildProjects(ProjectResource rsrc) throws CannotDeleteProjectException {
    List<ProjectInfo> children;
    try {
      children = listChildProjectsProvider.get().withLimit(1).apply(rsrc).value();
    } catch (Exception e) {
      throw new CannotDeleteProjectException(
          String.format("Unable to verify if '%s' has children projects.", rsrc.getName()), e);
    }
    if (!children.isEmpty()) {
      throw new CannotDeleteProjectException(
          "Cannot delete project because it has at least one child: "
              + Iterables.getOnlyElement(children).name);
    }
  }

  private void assertIsNotSubmodule(Project.NameKey projectNameKey)
      throws CannotDeleteProjectException {
    try (Repository repo = repoManager.openRepository(projectNameKey);
        MergeOpRepoManager mergeOp = mergeOpProvider.get()) {
      Set<BranchNameKey> branches =
          repo.getRefDatabase().getRefsByPrefix(REFS_HEADS).stream()
              .map(ref -> BranchNameKey.create(projectNameKey, ref.getName()))
              .collect(toSet());
      SubscriptionGraph graph = subscriptionGraphFactory.compute(branches, mergeOp);
      for (BranchNameKey b : branches) {
        if (graph.hasSuperproject(b)) {
          throw new CannotDeleteProjectException("Project is subscribed by other projects.");
        }
      }
    } catch (RepositoryNotFoundException e) {
      // we're trying to delete the repository,
      // so this exception should not stop us
    } catch (IOException | SubmoduleConflictException e) {
      throw new CannotDeleteProjectException("Project is subscribed by other projects.", e);
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
      if (!repo.getRefDatabase().getRefsByPrefix(REFS_TAGS).isEmpty()) {
        throw new CannotDeleteProjectException(
            String.format("Project %s has tags", projectNameKey));
      }
    } catch (IOException e) {
      throw new CannotDeleteProjectException(
          String.format("Unable to verify if project %s has tags", projectNameKey), e);
    }
  }
}
