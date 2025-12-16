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
import static com.googlesource.gerrit.plugins.deleteproject.Configuration.DEFAULT_INITIAL_DELAY_MILLIS;
import static com.googlesource.gerrit.plugins.deleteproject.Configuration.DEFAULT_PERIOD_DAYS;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableList;
import com.google.gerrit.server.config.RepositoryConfig;
import com.google.gerrit.server.config.ScheduleConfig;
import com.google.gerrit.server.config.SitePaths;
import com.google.gerrit.server.git.WorkQueue;
import com.googlesource.gerrit.plugins.deleteproject.Configuration;
import com.googlesource.gerrit.plugins.deleteproject.FakeScheduledExecutorService;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
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
public class DeleteTrashFoldersTest {
  private static final String DELETE_PROJECT_PLUGIN = "delete-project";
  private static final int INITIAL_DELAY_MIN = 2;
  private static final int INTERVAL_DAYS = 1;
  public static final String REPOSITORY_TO_DELETE = "repo.1234567890123.deleted";

  @Mock private RepositoryConfig repositoryCfg;

  @Mock private WorkQueue workQueue;

  @Mock private Configuration pluginCfg;

  @Rule public TemporaryFolder tempFolder = new TemporaryFolder();

  private Config cfg;
  private Path basePath;
  private DeleteTrashFolders trashFolders;
  private SitePaths sitePaths;
  private FakeScheduledExecutorService fakeScheduledExecutor;

  @Before
  public void setUp() throws Exception {
    sitePaths = new SitePaths(tempFolder.newFolder("gerrit_site").toPath());
    basePath = sitePaths.resolve("base");
    cfg = new Config();
    cfg.setString("gerrit", null, "basePath", basePath.toString());
    fakeScheduledExecutor = new FakeScheduledExecutorService();
    when(repositoryCfg.getAllBasePaths()).thenReturn(ImmutableList.of());
    when(workQueue.getDefaultQueue()).thenReturn(fakeScheduledExecutor);
    when(pluginCfg.getDeleteTrashFoldersMaxAllowedTime()).thenReturn(10L);
    when(pluginCfg.getTrashFolderName()).thenReturn("some-trash-folder");
    trashFolders =
        new DeleteTrashFolders(
            sitePaths, cfg, repositoryCfg, pluginCfg, workQueue, DELETE_PROJECT_PLUGIN);
  }

  @Test
  public void testShouldDeleteRepositoryAfterInitialDelayAndPeriodically() throws Exception {
    ZonedDateTime initialDateTime =
        ZonedDateTime.now(ZoneId.systemDefault()).plusMinutes(INITIAL_DELAY_MIN);
    String initialDateTimeFormatted = initialDateTime.format(DateTimeFormatter.ofPattern("HH:mm"));
    setupTrashFolderCleanupSchedule(
        initialDateTimeFormatted, String.format("%d days", INTERVAL_DAYS));

    DeleteTrashFolders trashFolders =
        new DeleteTrashFolders(
            sitePaths, cfg, repositoryCfg, pluginCfg, workQueue, DELETE_PROJECT_PLUGIN);
    trashFolders.start();

    try (FileRepository repoToDelete = createRepositoryToDelete(REPOSITORY_TO_DELETE)) {
      // Repository is not deleted at 1/2 time of the initial delay
      fakeScheduledExecutor.advance(
          TimeUnit.MINUTES.toSeconds(INITIAL_DELAY_MIN / 2), TimeUnit.SECONDS);
      assertThatRepositoryExists(repoToDelete);

      // Repository is deleted 1 second after the initial delay
      fakeScheduledExecutor.advance(
          TimeUnit.MINUTES.toSeconds(INITIAL_DELAY_MIN) + 1, TimeUnit.SECONDS);
      assertThatRepositoryIsDeleted(repoToDelete);
    }

    try (FileRepository repoToDelete = createRepositoryToDelete(REPOSITORY_TO_DELETE)) {
      // Repository recreated
      assertThatRepositoryExists(repoToDelete);

      // Repository is deleted again after the interval time
      fakeScheduledExecutor.advance(TimeUnit.DAYS.toSeconds(INTERVAL_DAYS), TimeUnit.SECONDS);
      assertThatRepositoryIsDeleted(repoToDelete);
    }
  }

  @Test
  public void testShouldDeleteRepositoryAfterInitialDelayAndDailyIfNoScheduleIsConfigured()
      throws Exception {
    trashFolders.start();

    try (FileRepository repoToDelete = createRepositoryToDelete(REPOSITORY_TO_DELETE)) {
      // Repository is not deleted at 1/2 time of the initial delay
      fakeScheduledExecutor.advance(DEFAULT_INITIAL_DELAY_MILLIS / 2, TimeUnit.MILLISECONDS);
      assertThatRepositoryExists(repoToDelete);

      // Repository is deleted 1 second after the initial delay
      fakeScheduledExecutor.advance(DEFAULT_INITIAL_DELAY_MILLIS + 1, TimeUnit.MILLISECONDS);
      assertThatRepositoryIsDeleted(repoToDelete);
    }

    try (FileRepository repoToDelete = createRepositoryToDelete(REPOSITORY_TO_DELETE)) {
      // Repository recreated
      assertThatRepositoryExists(repoToDelete);

      // Repository is deleted again after the interval time
      fakeScheduledExecutor.advance(
          TimeUnit.DAYS.toMillis(DEFAULT_PERIOD_DAYS), TimeUnit.MILLISECONDS);
      assertThatRepositoryIsDeleted(repoToDelete);
    }
  }

  private static void assertThatRepositoryIsDeleted(FileRepository repoToDelete) {
    assertFalse(
        "Repository " + repoToDelete.getDirectory() + " has not been deleted",
        repoToDelete.getDirectory().exists());
  }

  private static void assertThatRepositoryExists(FileRepository repoToDelete) {
    assertTrue(
        "Repository " + repoToDelete.getDirectory() + " does not exist",
        repoToDelete.getDirectory().exists());
  }

  @Test
  public void shouldStopProcessingWhenTimeoutExceeded() throws IOException {
    when(pluginCfg.getDeleteTrashFoldersMaxAllowedTime()).thenReturn(0L);

    DeleteTrashFolders deleteTrashFolders =
        new DeleteTrashFolders(
            sitePaths, cfg, repositoryCfg, pluginCfg, workQueue, DELETE_PROJECT_PLUGIN);

    for (int i = 0; i < 10; i++) {
      Path trash = basePath.resolve(String.format("repo.%013d.deleted", i));
      Files.createDirectories(trash);
    }

    deleteTrashFolders.start();
    deleteTrashFolders.getWorkerFuture().cancel(true);

    Stream<Path> remaining =
        Files.walk(basePath)
            .filter(Files::isDirectory)
            .filter(DeleteTrashFolders.TrashFolderPredicate::match);

    assertThat(remaining.count()).isGreaterThan(0L);
  }

  private FileRepository createRepository(String repoName) throws IOException {
    Path repoPath = Files.createDirectories(basePath.resolve(repoName));
    Repository repository = new FileRepository(repoPath.toFile());
    repository.create(true);
    return (FileRepository) repository;
  }

  private FileRepository createRepositoryToDelete(String repoName) throws IOException {
    return createRepository(
        basePath.resolve(pluginCfg.getTrashFolderName()).resolve(repoName).toString());
  }

  private void setupTrashFolderCleanupSchedule(String startTime, String interval) {
    cfg.setString("plugin", DELETE_PROJECT_PLUGIN, "deleteTrashFolderStartTime", startTime);
    cfg.setString("plugin", DELETE_PROJECT_PLUGIN, "deleteTrashFolderInterval", interval);
    Optional<ScheduleConfig.Schedule> schedule =
        ScheduleConfig.builder(cfg, "plugin")
            .setSubsection(DELETE_PROJECT_PLUGIN)
            .setKeyStartTime("deleteTrashFolderStartTime")
            .setKeyInterval("deleteTrashFolderInterval")
            .buildSchedule();
    when(pluginCfg.getSchedule()).thenReturn(schedule);
  }
}
