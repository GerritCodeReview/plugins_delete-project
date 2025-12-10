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

import com.google.common.flogger.FluentLogger;
import com.google.gerrit.extensions.annotations.PluginName;
import com.google.gerrit.server.git.WorkQueue;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.googlesource.gerrit.plugins.deleteproject.Configuration;
import com.googlesource.gerrit.plugins.deleteproject.TimeMachine;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@Singleton
public class ArchiveRepositoryRemover extends AbstractScheduledTask {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final Configuration config;
  private final String pluginName;

  @Inject
  ArchiveRepositoryRemover(
      WorkQueue queue, Configuration pluginCfg, @PluginName String pluginName) {
    super(queue, pluginCfg.getSchedule());
    this.config = pluginCfg;
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
      recursivelyDelete(path);
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
