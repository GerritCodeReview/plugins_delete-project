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
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableList;
import com.google.gerrit.acceptance.WaitUtil;
import com.google.gerrit.server.config.RepositoryConfig;
import com.google.gerrit.server.config.ScheduleConfig;
import com.google.gerrit.server.config.SitePaths;
import com.google.gerrit.server.git.WorkQueue;
import com.googlesource.gerrit.plugins.deleteproject.Configuration;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.Executors;
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

  @Mock private RepositoryConfig repositoryCfg;

  @Mock private WorkQueue workQueue;

  @Mock private Configuration pluginCfg;

  @Rule public TemporaryFolder tempFolder = new TemporaryFolder();

  private Config cfg;
  private Path basePath;
  private DeleteTrashFolders trashFolders;
  private SitePaths sitePaths;

  @Before
  public void setUp() throws Exception {
    sitePaths = new SitePaths(tempFolder.newFolder("gerrit_site").toPath());
    basePath = sitePaths.resolve("base");
    cfg = new Config();
    cfg.setString("gerrit", null, "basePath", basePath.toString());
    when(repositoryCfg.getAllBasePaths()).thenReturn(ImmutableList.of());
    when(workQueue.getDefaultQueue()).thenReturn(Executors.newSingleThreadScheduledExecutor());
    when(pluginCfg.getDeleteTrashFoldersMaxAllowedTime()).thenReturn(10L);
    trashFolders = new DeleteTrashFolders(sitePaths, cfg, repositoryCfg, pluginCfg, workQueue);
  }

  @Test
  public void testDoesNotDeleteTrashAtStartupIfScheduledInFuture() throws Exception {
    int INITIAL_DELAY_MIN = 1;
    FileRepository repoToDelete = createRepository("repo.1234567890123.deleted");

    ZonedDateTime nowPlus2 =
        ZonedDateTime.now(ZoneId.systemDefault()).plusMinutes(INITIAL_DELAY_MIN);
    String nowPlus2formatted = nowPlus2.format(DateTimeFormatter.ofPattern("HH:mm"));

    cfg.setString("deleteTrashFolder", null, "startTime", nowPlus2formatted);
    when(pluginCfg.getSchedule())
        .thenReturn(ScheduleConfig.createSchedule(cfg, "deleteTrashFolder"));
    DeleteTrashFolders trashFolders =
        new DeleteTrashFolders(sitePaths, cfg, repositoryCfg, pluginCfg, workQueue);
    trashFolders.start();
    Thread.sleep(Duration.ofSeconds((INITIAL_DELAY_MIN * 60) - 5).toMillis());
    assertThat(repoToDelete.getDirectory().exists()).isTrue();

    WaitUtil.waitUntil(
        () -> trashFolders.getWorkerFuture().isDone(), Duration.ofMinutes(INITIAL_DELAY_MIN * 3));
    assertThat(repoToDelete.getDirectory().exists()).isFalse();
  }

  @Test
  public void testStart() throws Exception {
    FileRepository repoToDelete = createRepository("repo.1234567890123.deleted");
    FileRepository repoToKeep = createRepository("anotherRepo.git");
    trashFolders.start();
    trashFolders.getWorkerFuture().get();
    assertThat(repoToDelete.getDirectory().exists()).isFalse();
    assertThat(repoToKeep.getDirectory().exists()).isTrue();
  }

  @Test
  public void shouldStopProcessingWhenTimeoutExceeded() throws IOException {
    when(pluginCfg.getDeleteTrashFoldersMaxAllowedTime()).thenReturn(0L);

    DeleteTrashFolders deleteTrashFolders =
        new DeleteTrashFolders(sitePaths, cfg, repositoryCfg, pluginCfg, workQueue);

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
}
