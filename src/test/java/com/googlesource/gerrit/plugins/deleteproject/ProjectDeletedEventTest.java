// Copyright (C) 2022 The Android Open Source Project
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
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.gerrit.entities.Project;
import com.google.gerrit.entities.Project.NameKey;
import com.google.gerrit.extensions.registration.DynamicItem;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.events.EventDispatcher;
import com.google.gerrit.server.project.ProjectResource;
import com.google.gerrit.server.project.ProjectState;
import com.google.inject.Provider;
import com.googlesource.gerrit.plugins.deleteproject.DeleteProject.Input;
import com.googlesource.gerrit.plugins.deleteproject.cache.CacheDeleteHandler;
import com.googlesource.gerrit.plugins.deleteproject.database.DatabaseDeleteHandler;
import com.googlesource.gerrit.plugins.deleteproject.fs.FilesystemDeleteHandler;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class ProjectDeletedEventTest {

  private static final NameKey PROJECT_NAME_KEY = Project.nameKey("test-project");
  private static final String INSTANCE_ID = "test-instance-id";

  @Mock private DatabaseDeleteHandler dbHandler;
  @Mock private FilesystemDeleteHandler fsHandler;
  @Mock private CacheDeleteHandler cacheHandler;
  @Mock private Provider<CurrentUser> userProvider;
  @Mock private DeleteLog deleteLog;
  @Mock private DeletePreconditions preConditions;
  @Mock private Configuration cfg;
  @Mock private EventDispatcher dispatcher;
  @Mock private DynamicItem<EventDispatcher> dispatcherProvider;
  @Mock private HideProject hideProject;
  @Mock private IdentifiedUser currentUser;
  @Mock private ProjectState state;
  @Captor private ArgumentCaptor<ProjectDeletedEvent> projectDeletedEventCaptor;

  private Project project = Project.builder(PROJECT_NAME_KEY).build();

  private DeleteProject objectUnderTest;

  @Before
  public void setup() throws Exception {
    when(dispatcherProvider.get()).thenReturn(dispatcher);
    when(userProvider.get()).thenReturn(currentUser);
    when(state.getProject()).thenReturn(project);
    objectUnderTest =
        new DeleteProject(
            dbHandler,
            fsHandler,
            cacheHandler,
            userProvider,
            deleteLog,
            preConditions,
            cfg,
            hideProject,
            dispatcherProvider,
            INSTANCE_ID);
  }

  @Test
  public void shouldSendProjectDeletedEventAfterProjectDeletion() throws Exception {
    Input input = new Input();
    input.force = false;
    input.preserve = false;
    objectUnderTest.doDelete(new ProjectResource(state, currentUser), input);

    verify(dispatcher).postEvent(eq(PROJECT_NAME_KEY), projectDeletedEventCaptor.capture());
    ProjectDeletedEvent event = projectDeletedEventCaptor.getValue();
    assertThat(event.instanceId).isEqualTo(INSTANCE_ID);
    assertThat(event.getProjectNameKey()).isEqualTo(PROJECT_NAME_KEY);
    assertThat(event.type).isEqualTo(ProjectDeletedEvent.TYPE);
  }
}
