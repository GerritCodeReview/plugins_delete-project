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

import static com.google.common.truth.Truth.assertThat;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.gerrit.server.config.PluginConfig;
import com.google.gerrit.server.config.PluginConfigFactory;
import com.google.gerrit.server.git.WorkQueue;
import com.google.gerrit.server.git.WorkQueue.Executor;
import com.google.inject.Provider;
import com.googlesource.gerrit.plugins.deleteproject.Configuration;
import com.googlesource.gerrit.plugins.deleteproject.TimeMachine;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import org.eclipse.jgit.lib.Config;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class ArchiveRepositoryRemoverTest {

  private static final String ARCHIVE_DURATION = "1";
  private static final long CLEANUP_INTERVAL = TimeUnit.DAYS.toMillis(1);
  private static final String CUSTOM_FILE = "testFile.txt";
  private static final int NUMBER_OF_FOLDERS = 10;
  private static final int NUMBER_OF_RUNS = 5;
  private static final String PLUGIN_NAME = "delete-project";

  @Mock private Executor executorMock;
  @Mock private ScheduledFuture<?> scheduledFutureMock;
  @Mock private WorkQueue workQueueMock;
  @Mock private Provider<RepositoryCleanupTask> cleanupTaskProviderMock;
  @Mock private PluginConfigFactory pluginConfigFactoryMock;

  @Rule public TemporaryFolder tempFolder = new TemporaryFolder();

  private FilesystemDeleteHandler fsDeleteHandler;
  private ArchiveRepositoryRemover remover;
  private Configuration configMock;
  private Path archiveRepo;

  @Before
  public void setUp() throws Exception {
    when(cleanupTaskProviderMock.get())
        .thenReturn(new RepositoryCleanupTask(null, null, PLUGIN_NAME));
    when(workQueueMock.getDefaultQueue()).thenReturn(executorMock);
    doReturn(scheduledFutureMock)
        .when(executorMock)
        .scheduleAtFixedRate(
            isA(RepositoryCleanupTask.class), anyLong(), anyLong(), isA(TimeUnit.class));
    remover = new ArchiveRepositoryRemover(workQueueMock, cleanupTaskProviderMock);
    fsDeleteHandler = new FilesystemDeleteHandler(null, null, configMock);
    archiveRepo = tempFolder.newFolder("archive").toPath();
  }

  @Test
  public void testRepositoryCleanupTaskRun() throws IOException {
    setupConfigMock();
    try {
      TimeMachine.useFixedClockAt(
          Instant.ofEpochMilli(Files.getLastModifiedTime(archiveRepo).toMillis())
              .plusMillis(TimeUnit.DAYS.toMillis(Long.parseLong(ARCHIVE_DURATION)) + 10));
      fsDeleteHandler = mock(FilesystemDeleteHandler.class);
      RepositoryCleanupTask task =
          new RepositoryCleanupTask(fsDeleteHandler, configMock, PLUGIN_NAME);
      for (int i = 0; i < NUMBER_OF_RUNS; i++) {
        task.run();
      }
      verify(fsDeleteHandler, times(NUMBER_OF_RUNS))
          .recursiveDelete(archiveRepo.resolve(CUSTOM_FILE));
      assertThat(task.toString())
          .isEqualTo(
              String.format(
                  "[%s]: Clean up expired git repositories from the archive [%s]",
                  PLUGIN_NAME, archiveRepo));
    } finally {
      TimeMachine.useSystemDefaultZoneClock();
    }
  }

  @Test
  public void cleanUpOverdueRepositoriesTest() throws IOException {
    setupConfigMock();
    try {
      TimeMachine.useFixedClockAt(
          Instant.ofEpochMilli(Files.getLastModifiedTime(archiveRepo).toMillis())
              .plusMillis(TimeUnit.DAYS.toMillis(Long.parseLong(ARCHIVE_DURATION)) + 10));

      fsDeleteHandler = new FilesystemDeleteHandler(null, null, configMock);
      RepositoryCleanupTask task =
          new RepositoryCleanupTask(fsDeleteHandler, configMock, PLUGIN_NAME);
      task.run();
      assertThat(isDirEmpty(archiveRepo)).isTrue();
    } finally {
      TimeMachine.useSystemDefaultZoneClock();
    }
  }

  @Test
  public void testRepositoryCleanupTaskIsScheduledOnStart() {
    remover.start();
    verify(executorMock, times(1))
        .scheduleAtFixedRate(
            isA(RepositoryCleanupTask.class),
            eq(SECONDS.toMillis(1)),
            eq(CLEANUP_INTERVAL),
            eq(TimeUnit.MILLISECONDS));
  }

  @Test
  public void testRepositoryCleanupTaskIsCancelledOnStop() {
    remover.start();
    remover.stop();
    verify(scheduledFutureMock, times(1)).cancel(true);
  }

  private boolean isDirEmpty(final Path dir) throws IOException {
    try (DirectoryStream<Path> dirStream = Files.newDirectoryStream(dir)) {
      return !dirStream.iterator().hasNext();
    }
  }

  private void setupConfigMock() throws IOException {
    for (int i = 0; i < NUMBER_OF_FOLDERS; i++) {
      archiveRepo.resolve("myPj" + i).toFile().mkdir();
    }
    archiveRepo.resolve(CUSTOM_FILE).toFile().createNewFile();
    assertThat(isDirEmpty(archiveRepo)).isFalse();

    PluginConfig pluginConfig = new PluginConfig(PLUGIN_NAME, new Config());
    pluginConfig.setBoolean("archiveDeletedRepos", true);
    pluginConfig.setString("deleteArchivedReposAfter", ARCHIVE_DURATION);
    when(pluginConfigFactoryMock.getFromGerritConfig(PLUGIN_NAME)).thenReturn(pluginConfig);
    configMock = new Configuration(pluginConfigFactoryMock, PLUGIN_NAME, archiveRepo.toFile());
  }
}
