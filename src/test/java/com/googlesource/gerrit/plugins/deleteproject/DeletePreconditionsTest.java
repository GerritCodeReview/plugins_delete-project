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

import static com.google.common.truth.Truth.assertThat;
import static com.googlesource.gerrit.plugins.deleteproject.DeleteOwnProjectCapability.DELETE_OWN_PROJECT;
import static com.googlesource.gerrit.plugins.deleteproject.DeleteProjectCapability.DELETE_PROJECT;
import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableList;
import com.google.gerrit.entities.Project;
import com.google.gerrit.exceptions.StorageException;
import com.google.gerrit.extensions.api.access.PluginPermission;
import com.google.gerrit.extensions.common.ProjectInfo;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.permissions.GlobalPermission;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.ProjectPermission;
import com.google.gerrit.server.project.ProjectResource;
import com.google.gerrit.server.project.ProjectState;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.query.change.InternalChangeQuery;
import com.google.gerrit.server.restapi.project.ListChildProjects;
import com.google.gerrit.server.submit.MergeOpRepoManager;
import com.google.gerrit.server.submit.SubscriptionGraph;
import com.google.inject.Provider;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class DeletePreconditionsTest {
  private static final String PLUGIN_NAME = "delete-project";
  private static final Project.NameKey PROJECT_NAMEKEY = Project.nameKey("test-project");

  @Mock private Configuration config;
  @Mock private Provider<ListChildProjects> listChildProjectsProvider;
  @Mock private Provider<MergeOpRepoManager> mergeOpProvider;
  @Mock private Provider<InternalChangeQuery> queryProvider;
  @Mock private GitRepositoryManager repoManager;
  @Mock private SubscriptionGraph.Factory subscriptionGraphFactory;
  @Mock private CurrentUser currentUser;
  @Mock private Provider<CurrentUser> userProvider;
  @Mock private ProjectState state;
  @Mock private ProtectedProjects protectedProjects;
  @Mock private PermissionBackend permissionBackend;
  @Mock private PermissionBackend.WithUser userPermission;

  private ProjectResource rsrc;
  private DeletePreconditions preConditions;

  @Before
  public void setUp() {
    when(userProvider.get()).thenReturn(currentUser);
    rsrc = new ProjectResource(state, currentUser);
    when(rsrc.getNameKey()).thenReturn(PROJECT_NAMEKEY);
    preConditions =
        new DeletePreconditions(
            config,
            listChildProjectsProvider,
            mergeOpProvider,
            PLUGIN_NAME,
            queryProvider,
            repoManager,
            subscriptionGraphFactory,
            userProvider,
            protectedProjects,
            permissionBackend);
  }

  @Test
  public void testUserCanDeleteIfAdmin() {
    when(permissionBackend.user(currentUser)).thenReturn(userPermission);
    when(userPermission.testOrFalse(GlobalPermission.ADMINISTRATE_SERVER)).thenReturn(true);
    assertThat(preConditions.canDelete(rsrc)).isTrue();
  }

  @Test
  public void testUserCanDeleteIfHasDeletePermission() {
    when(permissionBackend.user(currentUser)).thenReturn(userPermission);
    when(userPermission.testOrFalse(new PluginPermission(PLUGIN_NAME, DELETE_PROJECT)))
        .thenReturn(true);
    assertThat(preConditions.canDelete(rsrc)).isTrue();
  }

  @Test
  public void testUserCanDeleteIfIsOwnerAndHasDeleteOwnPermission() {
    when(permissionBackend.user(currentUser)).thenReturn(userPermission);
    when(userPermission.testOrFalse(new PluginPermission(PLUGIN_NAME, DELETE_OWN_PROJECT)))
        .thenReturn(true);
    PermissionBackend.ForProject projectPermission = mock(PermissionBackend.ForProject.class);
    when(projectPermission.testOrFalse(ProjectPermission.WRITE_CONFIG)).thenReturn(true);
    when(userPermission.project(PROJECT_NAMEKEY)).thenReturn(projectPermission);
    assertThat(preConditions.canDelete(rsrc)).isTrue();
  }

  @Test
  public void testUserCannotDelete() throws Exception {
    when(permissionBackend.user(currentUser)).thenReturn(userPermission);
    AuthException thrown =
        assertThrows(AuthException.class, () -> preConditions.assertDeletePermission(rsrc));
    assertThat(thrown).hasMessageThat().contains("not allowed to delete project");
  }

  @Test
  public void testIsProtectedSoCannotBeDeleted() throws Exception {
    doThrow(CannotDeleteProjectException.class).when(protectedProjects).assertIsNotProtected(rsrc);
    assertThrows(
        ResourceConflictException.class,
        () -> preConditions.assertCanBeDeleted(rsrc, new DeleteProject.Input()));
  }

  @Test
  public void testHasChildrenSoCannotBeDeleted() throws Exception {
    doNothing().when(protectedProjects).assertIsNotProtected(rsrc);
    ListChildProjects childProjects = mock(ListChildProjects.class);
    when(listChildProjectsProvider.get()).thenReturn(childProjects);
    when(childProjects.withLimit(1)).thenReturn(childProjects);
    when(childProjects.apply(rsrc)).thenReturn(Response.ok(ImmutableList.of(new ProjectInfo())));
    ResourceConflictException thrown =
        assertThrows(
            ResourceConflictException.class,
            () -> preConditions.assertCanBeDeleted(rsrc, new DeleteProject.Input()));
    assertThat(thrown)
        .hasMessageThat()
        .contains("Cannot delete project because it has at least one child:");
  }

  @Test
  public void testAssertHasOpenChangesNoForceSet() throws Exception {
    InternalChangeQuery queryChange = mock(InternalChangeQuery.class);
    ChangeData cd = mock(ChangeData.class);
    when(queryChange.byProjectOpen(PROJECT_NAMEKEY)).thenReturn(ImmutableList.of(cd));
    when(queryProvider.get()).thenReturn(queryChange);
    String expectedMessage = String.format("Project '%s' has open changes.", PROJECT_NAMEKEY.get());
    CannotDeleteProjectException thrown =
        assertThrows(
            CannotDeleteProjectException.class,
            () -> preConditions.assertHasOpenChanges(PROJECT_NAMEKEY, false));
    assertThat(thrown).hasMessageThat().contains(expectedMessage);
  }

  @Test
  public void testUnableToAssertOpenChanges() throws Exception {
    InternalChangeQuery queryChange = mock(InternalChangeQuery.class);
    doThrow(StorageException.class).when(queryChange).byProjectOpen(PROJECT_NAMEKEY);
    when(queryProvider.get()).thenReturn(queryChange);
    String expectedMessage =
        String.format("Unable to verify if '%s' has open changes.", PROJECT_NAMEKEY.get());
    CannotDeleteProjectException thrown =
        assertThrows(
            CannotDeleteProjectException.class,
            () -> preConditions.assertHasOpenChanges(PROJECT_NAMEKEY, false));
    assertThat(thrown).hasMessageThat().contains(expectedMessage);
  }
}
