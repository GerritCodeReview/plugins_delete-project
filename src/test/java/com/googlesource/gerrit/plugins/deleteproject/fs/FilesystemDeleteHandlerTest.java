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

package com.googlesource.gerrit.plugins.deleteproject.fs;


import com.google.gerrit.entities.Project;
import com.google.gerrit.extensions.events.ProjectDeletedListener;
import com.google.gerrit.extensions.registration.DynamicSet;
import com.googlesource.gerrit.plugins.deleteproject.Configuration;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class FilesystemDeleteHandlerTest {

  @Mock private RepositoryDelete repositoryDelete;
  @Mock private ProjectDeletedListener projectDeleteListener;
  @Mock private Configuration config;

  @Rule public TemporaryFolder tempFolder = new TemporaryFolder();

  private DynamicSet<ProjectDeletedListener> deletedListeners = DynamicSet.emptySet();
  private Path basePath;

  @Before
  public void setUp() throws Exception {
    basePath = tempFolder.newFolder().toPath().resolve("archive");
    deletedListeners.add("", projectDeleteListener);
  }

  @Test
  public void shouldExtractArchivingParamsFromConfig() throws Exception {
    boolean doArchive = true;
    Project.NameKey project = Project.NameKey.parse("testProject");
    boolean noPreserveGitRepository = false;

    Mockito.when(config.shouldArchiveDeletedRepos()).thenReturn(doArchive);
    Mockito.when(config.getArchiveFolder()).thenReturn(basePath);

    FilesystemDeleteHandler filesystemDeleteHandler =
        new FilesystemDeleteHandler(repositoryDelete, deletedListeners, config);
    filesystemDeleteHandler.delete(project, noPreserveGitRepository);
    Mockito.verify(repositoryDelete)
        .execute(
            project, noPreserveGitRepository, doArchive, Optional.of(basePath), deletedListeners);
  }
}
