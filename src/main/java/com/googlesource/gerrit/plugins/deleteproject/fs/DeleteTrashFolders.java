// Copyright (C) 2015 The Android Open Source Project
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

import static com.google.common.io.RecursiveDeleteOption.ALLOW_INSECURE;
import static com.googlesource.gerrit.plugins.deleteproject.Configuration.DEFAULT_INITIAL_DELAY_MILLIS;
import static com.googlesource.gerrit.plugins.deleteproject.Configuration.DEFAULT_PERIOD_DAYS;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Stopwatch;
import com.google.common.collect.Sets;
import com.google.common.flogger.FluentLogger;
import com.google.common.io.MoreFiles;
import com.google.gerrit.extensions.annotations.PluginName;
import com.google.gerrit.extensions.events.LifecycleListener;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.config.RepositoryConfig;
import com.google.gerrit.server.config.ScheduleConfig;
import com.google.gerrit.server.config.SitePaths;
import com.google.gerrit.server.git.WorkQueue;
import com.google.inject.Inject;
import com.googlesource.gerrit.plugins.deleteproject.Configuration;
import java.io.IOException;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.eclipse.jgit.lib.Config;

public class DeleteTrashFolders implements LifecycleListener {
  private static final FluentLogger log = FluentLogger.forEnclosingClass();

  private final WorkQueue workQueue;
  private final String pluginName;

  static class TrashFolderPredicate {

    private TrashFolderPredicate() {
      // Avoid this class being instantiated by using the default empty constructor
    }

    /**
     * Search for name which ends with a dot, 13 digits and the string ".deleted". A folder 'f' is
     * renamed to 'f.<currentTimeMillis>.deleted'. <currentTimeMillis> happens to be exactly 13
     * digits for commits created between 2002 (before git was born) and 2285.
     */
    private static final Pattern TRASH_1 = Pattern.compile(".*\\.\\d{13}.deleted");

    /**
     * New trash folder name format. It adds % chars around the "deleted" string and keeps the
     * ".git" extension.
     */
    private static final Pattern TRASH_2 = Pattern.compile(".*\\.\\d{13}.%deleted%.git");

    /**
     * Newer trash folder name format. Besides the changes in TRASH_2, it uses a timestamp format
     * (yyyyMMddHHmmss) instead of the epoch one for increased readability.
     */
    private static final Pattern TRASH_3 = Pattern.compile(".*\\.\\d{14}.%deleted%.git");

    @VisibleForTesting
    static final boolean match(String fName) {
      return TRASH_1.matcher(fName).matches()
          || TRASH_2.matcher(fName).matches()
          || TRASH_3.matcher(fName).matches();
    }

    static boolean match(Path dir) {
      return match(dir.getFileName().toString());
    }
  }

  private Set<Path> repoFolders;

  private ScheduledFuture<?> threadCompleted;
  private final Optional<ScheduleConfig.Schedule> schedule;
  private final long deleteTrashFoldersMaxAllowedTime;
  private final String trashFolderName;

  @Inject
  public DeleteTrashFolders(
      SitePaths site,
      @GerritServerConfig Config cfg,
      RepositoryConfig repositoryCfg,
      Configuration pluginCfg,
      WorkQueue workQueue,
      @PluginName String pluginName) {
    repoFolders = Sets.newHashSet();
    repoFolders.add(site.resolve(cfg.getString("gerrit", null, "basePath")));
    repoFolders.addAll(repositoryCfg.getAllBasePaths());
    schedule = pluginCfg.getSchedule();
    trashFolderName = pluginCfg.getTrashFolderName();
    deleteTrashFoldersMaxAllowedTime = pluginCfg.getDeleteTrashFoldersMaxAllowedTime();
    this.workQueue = workQueue;
    this.pluginName = pluginName;
  }

  @Override
  public void start() {
    String taskName = String.format("[%s]: DeleteTrashFolders under %s", pluginName, repoFolders);
    Runnable deleteTrashFoldersRunnable =
        new Runnable() {
          @Override
          public void run() {
            log.atInfo().log("%s : STARTED", taskName);
            evaluateIfTrashWithTimeLimit();
            log.atInfo().log("%s : ENDED", taskName);
          }

          @Override
          public String toString() {
            return taskName;
          }
        };

    ScheduledExecutorService scheduledExecutor = workQueue.getDefaultQueue();
    long initialDelay = DEFAULT_INITIAL_DELAY_MILLIS;
    long period = TimeUnit.DAYS.toMillis(DEFAULT_PERIOD_DAYS);
    if (schedule.isPresent()) {
      initialDelay = schedule.get().initialDelay();
      period = schedule.get().interval();
    }

    threadCompleted =
        scheduledExecutor.scheduleAtFixedRate(
            deleteTrashFoldersRunnable, initialDelay, period, MILLISECONDS);
  }

  private void evaluateIfTrashWithTimeLimit() {
    Stopwatch stopWatch = Stopwatch.createStarted();
    for (Path folder : repoFolders) {
      Path deletedRepoFolder = folder.resolve(trashFolderName);
      if (exceededMaxAllowedTime(deletedRepoFolder, stopWatch)) {
        break;
      }
      evaluateIfTrash(deletedRepoFolder, stopWatch);
    }
  }

  private void evaluateIfTrash(Path folder, Stopwatch stopWatch) {
    try (Stream<Path> dir = Files.walk(folder, FileVisitOption.FOLLOW_LINKS)) {
      Iterator<Path> it =
          dir.filter(Files::isDirectory).filter(TrashFolderPredicate::match).iterator();

      while (it.hasNext()) {
        if (exceededMaxAllowedTime(folder, stopWatch)) {
          break;
        }
        recursivelyDelete(it.next());
      }
    } catch (IOException e) {
      log.atSevere().withCause(e).log("Failed to evaluate %s", folder);
    }
  }

  private boolean exceededMaxAllowedTime(Path folder, Stopwatch stopWatch) {
    if (stopWatch.elapsed(TimeUnit.SECONDS) >= deleteTrashFoldersMaxAllowedTime) {
      log.atWarning().log(
          "Stopping early: exceeded max duration (%d s) while scanning %s",
          deleteTrashFoldersMaxAllowedTime, folder);
      return true;
    }
    return false;
  }

  @VisibleForTesting
  ScheduledFuture<?> getWorkerFuture() {
    return threadCompleted;
  }

  private void recursivelyDelete(Path folder) {
    try {
      MoreFiles.deleteRecursively(folder, ALLOW_INSECURE);
    } catch (IOException e) {
      log.atSevere().withCause(e).log("Failed to delete %s", folder);
    }
  }

  @Override
  public void stop() {
    if (threadCompleted != null) {
      threadCompleted.cancel(true);
      threadCompleted = null;
    }
  }
}
