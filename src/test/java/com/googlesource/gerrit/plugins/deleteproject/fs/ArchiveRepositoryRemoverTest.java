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
import static java.util.stream.Collectors.toList;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.base.Joiner;
import com.google.gerrit.server.config.ScheduleConfig;
import com.google.gerrit.server.git.WorkQueue;
import com.google.inject.Provider;
import com.googlesource.gerrit.plugins.deleteproject.Configuration;
import com.googlesource.gerrit.plugins.deleteproject.FakeScheduledExecutorService;
import com.googlesource.gerrit.plugins.deleteproject.TimeMachine;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.StreamSupport;
import org.eclipse.jgit.internal.storage.file.FileRepository;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.Repository;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class ArchiveRepositoryRemoverTest {

  private static final int INITIAL_DELAY_MIN = 1;
  private static final int INTERVAL_DAYS = 1;
  private static final long ARCHIVE_DURATION = 1;
  private static final int NUMBER_OF_REPOS = 10;
  private static final String PLUGIN_NAME = "delete-project";

  @Mock private WorkQueue workQueueMock;
  @Mock private Provider<RepositoryCleanupTask> cleanupTaskProviderMock;
  @Mock private Configuration configMock;
  @Mock private Configuration pluginCfg;

  @Rule public TemporaryFolder tempFolder = new TemporaryFolder();

  private ArchiveRepositoryRemover remover;
  private Path archiveRepo;
  private FakeScheduledExecutorService fakeScheduledExecutor;
  private Config cfg;

  @Before
  public void setUp() throws Exception {
    cfg = new Config();
    fakeScheduledExecutor = new FakeScheduledExecutorService();
    when(cleanupTaskProviderMock.get()).thenReturn(new RepositoryCleanupTask(pluginCfg, PLUGIN_NAME));
    when(workQueueMock.getDefaultQueue()).thenReturn(fakeScheduledExecutor);
    archiveRepo = tempFolder.newFolder("archive").toPath();
    when(configMock.getArchiveFolder()).thenReturn(archiveRepo);
    when(configMock.getArchiveDuration()).thenReturn(ARCHIVE_DURATION);

    ZonedDateTime initialDateTime =
        ZonedDateTime.now(ZoneId.systemDefault()).plusMinutes(INITIAL_DELAY_MIN);
    String initialDateTimeFormatted = initialDateTime.format(DateTimeFormatter.ofPattern("HH:mm"));
    setupArchiveFolderCleanupSchedule(
        initialDateTimeFormatted, String.format("%d minutes", INTERVAL_DAYS));

    remover = new ArchiveRepositoryRemover(workQueueMock, cleanupTaskProviderMock, pluginCfg);
  }

  @Test
  public void cleanUpOverdueRepositoriesTest() throws IOException {
    assertDirectoryContents(archiveRepo, true);
    setupArchiveFolder();

    remover.start();
    try {
//      TimeMachine.useFixedClockAt(
//          Instant.ofEpochMilli(Files.getLastModifiedTime(archiveRepo).toMillis())
//              .plusMillis(TimeUnit.DAYS.toMillis(ARCHIVE_DURATION) + 10));

      fakeScheduledExecutor.advance(
          TimeUnit.DAYS.toSeconds(INITIAL_DELAY_MIN + ARCHIVE_DURATION + 2), TimeUnit.MILLISECONDS);

      RepositoryCleanupTask task = new RepositoryCleanupTask(configMock, PLUGIN_NAME);
      task.run();
      assertThat(task.toString())
          .isEqualTo(
              String.format(
                  "[%s]: Clean up expired git repositories from the archive [%s]",
                  PLUGIN_NAME, archiveRepo));
      assertDirectoryContents(archiveRepo, true);
    } finally {
      TimeMachine.useSystemPctZoneClock();
    }
  }

  @Test
  public void testRepositoryCleanupTaskIsCancelledOnStop() {
    remover.start();
    assertThat(remover.getWorkerFuture()).isNotNull();

    remover.stop();
    assertThat(remover.getWorkerFuture()).isNull();
  }

  private void setupArchiveFolder() throws IOException {
    for (int i = 0; i < NUMBER_OF_REPOS; i++) {
      createRepository("Repo_" + i);
    }
    assertDirectoryContents(archiveRepo, false);
  }

  private FileRepository createRepository(String repoName) throws IOException {
    Path repoPath = Files.createDirectories(archiveRepo.resolve(repoName));
    Repository repository = new FileRepository(repoPath.toFile());
    repository.create(true);
    return (FileRepository) repository;
  }

  private void assertDirectoryContents(Path dir, boolean expectEmpty) throws IOException {
    try (DirectoryStream<Path> dirStream = Files.newDirectoryStream(dir)) {
      List<Path> paths = StreamSupport.stream(dirStream.spliterator(), false).collect(toList());
      if (expectEmpty && !paths.isEmpty()) {
        fail(
            String.format(
                "Expected dir [%s] to be empty but it contains: %s",
                dir, Joiner.on(", ").join(paths)));
      } else if (!expectEmpty && paths.isEmpty()) {
        fail(String.format("Expected dir [%s] to be non-empty but it is empty", dir));
      }
    }
  }

  private void setupArchiveFolderCleanupSchedule(String startTime, String interval) {
    cfg.setString("plugin", PLUGIN_NAME, "deleteTrashFolderStartTime", startTime);
    cfg.setString("plugin", PLUGIN_NAME, "deleteTrashFolderInterval", interval);
    Optional<ScheduleConfig.Schedule> schedule =
        ScheduleConfig.builder(cfg, "plugin")
            .setSubsection(PLUGIN_NAME)
            .setKeyStartTime("deleteTrashFolderStartTime")
            .setKeyInterval("deleteTrashFolderInterval")
            .buildSchedule();
    when(pluginCfg.getSchedule()).thenReturn(schedule);
  }
}
