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

import static java.util.concurrent.TimeUnit.MILLISECONDS;

import com.google.common.base.Strings;
import com.google.gerrit.extensions.annotations.PluginData;
import com.google.gerrit.extensions.annotations.PluginName;
import com.google.gerrit.server.config.ConfigUtil;
import com.google.gerrit.server.config.PluginConfig;
import com.google.gerrit.server.config.PluginConfigFactory;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class Configuration {
  private static final String DELETED_PROJECTS_PARENT = "Deleted-Projects";
  private static final Logger log = LoggerFactory.getLogger(Configuration.class);
  private static final long DEFAULT_ARCHIVE_DURATION_DAYS = 180;

  private final boolean allowDeletionWithTags;
  private final boolean archiveDeletedRepos;
  private final Path archiveFolder;
  private final long deleteArchivedReposAfter;
  private final String deletedProjectsParent;
  private final boolean enablePreserveOption;
  private final boolean hideProjectOnPreserve;

  private PluginConfig cfg;
  private File pluginData;

  @Inject
  public Configuration(
      PluginConfigFactory pluginConfigFactory,
      @PluginName String pluginName,
      @PluginData File pluginData) {
    this.cfg = pluginConfigFactory.getFromGerritConfig(pluginName);
    this.pluginData = pluginData;

    this.allowDeletionWithTags = cfg.getBoolean("allowDeletionOfReposWithTags", true);
    this.hideProjectOnPreserve = cfg.getBoolean("hideProjectOnPreserve", false);
    this.deletedProjectsParent = cfg.getString("parentForDeletedProjects", DELETED_PROJECTS_PARENT);
    this.archiveDeletedRepos = cfg.getBoolean("archiveDeletedRepos", false);
    this.deleteArchivedReposAfter = getArchiveDurationFromConfig();
    this.archiveFolder = getArchiveFolderFromConfig();
    this.enablePreserveOption = cfg.getBoolean("enablePreserveOption", true);
  }

  public boolean deletionWithTagsAllowed() {
    return allowDeletionWithTags;
  }

  public boolean projectOnPreserveHidden() {
    return hideProjectOnPreserve;
  }

  public String getDeletedProjectsParent() {
    return deletedProjectsParent;
  }

  public boolean shouldArchiveDeletedRepos() {
    return archiveDeletedRepos;
  }

  public Path getArchiveFolder() {
    return archiveFolder;
  }

  public long getArchiveDuration() {
    return deleteArchivedReposAfter;
  }

  public boolean enablePreserveOption() {
    return enablePreserveOption;
  }

  private long getArchiveDurationFromConfig() {
    long duration;
    try {
      duration =
          ConfigUtil.getTimeUnit(
              Strings.nullToEmpty(cfg.getString("deleteArchivedReposAfter")),
              TimeUnit.DAYS.toMillis(DEFAULT_ARCHIVE_DURATION_DAYS),
              MILLISECONDS);
    } catch (IllegalArgumentException e) {
      log.warn(
          "The configured archive duration is not valid, use the default value: {} days",
          DEFAULT_ARCHIVE_DURATION_DAYS);
      duration = TimeUnit.DAYS.toMillis(DEFAULT_ARCHIVE_DURATION_DAYS);
    }
    return duration;
  }

  private Path getArchiveFolderFromConfig() {
    Path archiveDir = Paths.get(cfg.getString("archiveFolder", pluginData.toString()));
    if (!Files.exists(archiveDir)) {
      try {
        Files.createDirectories(archiveDir);
        log.info("Archive folder {} does not exist, creating it then now", archiveDir);
      } catch (Exception e) {
        log.warn(
            "Archive folder {} does not exist, just failed to create it, so using default path: {}",
            archiveDir,
            pluginData);
        archiveDir = pluginData.toPath();
      }
    }
    return archiveDir;
  }
}
