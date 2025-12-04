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

import static com.google.common.io.RecursiveDeleteOption.ALLOW_INSECURE;
import static com.googlesource.gerrit.plugins.deleteproject.Configuration.DEFAULT_INITIAL_DELAY_MILLIS;
import static com.googlesource.gerrit.plugins.deleteproject.Configuration.DEFAULT_PERIOD_DAYS;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.flogger.FluentLogger;
import com.google.common.io.MoreFiles;
import com.google.gerrit.extensions.annotations.PluginName;
import com.google.gerrit.extensions.events.LifecycleListener;
import com.google.gerrit.server.config.ScheduleConfig;
import com.google.gerrit.server.git.WorkQueue;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import com.googlesource.gerrit.plugins.deleteproject.Configuration;
import com.googlesource.gerrit.plugins.deleteproject.TimeMachine;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

@Singleton
public class ArchiveRepositoryRemover implements LifecycleListener {

  private final WorkQueue queue;
  private final Optional<ScheduleConfig.Schedule> schedule;
  private final Provider<RepositoryCleanupTask> repositoryCleanupTaskProvider;
  private ScheduledFuture<?> scheduledCleanupTask;

  @Inject
  ArchiveRepositoryRemover(
      WorkQueue queue,
      Provider<RepositoryCleanupTask> repositoryCleanupTaskProvider,
      Configuration pluginCfg) {
    schedule = pluginCfg.getSchedule();
    this.queue = queue;
    this.repositoryCleanupTaskProvider = repositoryCleanupTaskProvider;
  }

  @Override
  public void start() {
    long initialDelay = DEFAULT_INITIAL_DELAY_MILLIS;
    long period = TimeUnit.DAYS.toMillis(DEFAULT_PERIOD_DAYS);
    if (schedule.isPresent()) {
      initialDelay = schedule.get().initialDelay();
      period = schedule.get().interval();
    }

    scheduledCleanupTask =
        queue
            .getDefaultQueue()
            .scheduleAtFixedRate(
                repositoryCleanupTaskProvider.get(), initialDelay, period, MILLISECONDS);
  }

  @Override
  public void stop() {
    if (scheduledCleanupTask != null) {
      scheduledCleanupTask.cancel(true);
      scheduledCleanupTask = null;
    }
  }

  @VisibleForTesting
  ScheduledFuture<?> getWorkerFuture() {
    return scheduledCleanupTask;
  }
}

class RepositoryCleanupTask implements Runnable {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final Configuration config;
  private final String pluginName;

  @Inject
  RepositoryCleanupTask(Configuration config, @PluginName String pluginName) {
    this.config = config;
    this.pluginName = pluginName;
  }

  @Override
  public void run() {
    logger.atInfo().log("Cleaning up expired git repositories...");
    cleanUpOverdueRepositories();
    logger.atInfo().log("Cleaning up expired git repositories... Done");
  }

  @Override
  public String toString() {
    return String.format(
        "[%s]: Clean up expired git repositories from the archive [%s]",
        pluginName, config.getArchiveFolder());
  }

  private void cleanUpOverdueRepositories() {
    for (Path path : listOverdueFiles(config.getArchiveDuration())) {
      try {
        MoreFiles.deleteRecursively(path, ALLOW_INSECURE);
      } catch (IOException e) {
        logger.atWarning().withCause(e).log(
            "Error trying to clean the archived git repository: %s", path);
      }
    }
  }

  private List<Path> listOverdueFiles(long duration) {
    List<Path> files = new ArrayList<>();
    File targetDir = config.getArchiveFolder().toFile();
    long nowTimestamp = TimeMachine.now().toEpochMilli();

    for (File repo : targetDir.listFiles()) {
      try {
        long lastModifiedTime = Files.getLastModifiedTime(repo.toPath()).toMillis();
        long expiryTimestamp = lastModifiedTime + duration;
        if (nowTimestamp > expiryTimestamp) {
          files.add(repo.toPath());
        }
      } catch (IOException e) {
        logger.atWarning().withCause(e).log(
            "Error trying to get last modified time for file: %s", repo.toPath());
      }
    }
    return files;
  }
}
