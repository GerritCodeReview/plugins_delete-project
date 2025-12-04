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

import static java.util.concurrent.TimeUnit.DAYS;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.SECONDS;
import static java.util.stream.Collectors.toList;

import com.google.common.base.Strings;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.extensions.annotations.PluginData;
import com.google.gerrit.extensions.annotations.PluginName;
import com.google.gerrit.server.config.ConfigUtil;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.config.PluginConfig;
import com.google.gerrit.server.config.PluginConfigFactory;
import com.google.gerrit.server.config.ScheduleConfig;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import org.eclipse.jgit.lib.Config;

@Singleton
public class Configuration {
  private static final FluentLogger log = FluentLogger.forEnclosingClass();
  private static final String DELETED_PROJECTS_PARENT = "Deleted-Projects";
  private static final long DEFAULT_ARCHIVE_DURATION_DAYS = 180;
  protected static final long DEFAULT_TRASH_FOLDER_MAX_ALLOWED_TIME_MINUTES = 10;
  public static final String DEFAULT_TRASH_FOLDER_NAME = "";
  public static final long DEFAULT_INITIAL_DELAY_MILLIS = 1000;
  public static final long DEFAULT_PERIOD_DAYS = 1;

  private final boolean allowDeletionWithTags;
  private final boolean archiveDeletedRepos;
  private final boolean hideProjectOnPreserve;
  private final long deleteArchivedReposAfter;
  private final long deleteTrashFoldersMaxAllowedTime;
  private final String deletedProjectsParent;
  private final String trashFolderName;
  private final Path archiveFolder;
  private final List<Pattern> protectedProjects;
  private final Optional<ScheduleConfig.Schedule> schedule;
  private final PluginConfig cfg;

  private File pluginData;

  @Inject
  public Configuration(
      PluginConfigFactory pluginConfigFactory,
      @PluginName String pluginName,
      @PluginData File pluginData,
      @GerritServerConfig Config gerritConfig) {
    this.cfg = pluginConfigFactory.getFromGerritConfig(pluginName);
    this.pluginData = pluginData;
    this.allowDeletionWithTags = cfg.getBoolean("allowDeletionOfReposWithTags", true);
    this.hideProjectOnPreserve = cfg.getBoolean("hideProjectOnPreserve", false);
    this.deletedProjectsParent = cfg.getString("parentForDeletedProjects", DELETED_PROJECTS_PARENT);
    this.archiveDeletedRepos = cfg.getBoolean("archiveDeletedRepos", false);
    this.deleteTrashFoldersMaxAllowedTime =
        getTrashFoldersMaxAllowedTimeFromConfig(
            cfg.getString("deleteTrashFoldersMaxAllowedTime", pluginData.toString()));
    this.archiveFolder =
        getArchiveFolderFromConfig(cfg.getString("archiveFolder", pluginData.toString()));
    this.deleteArchivedReposAfter =
        getArchiveDurationFromConfig(
            Strings.nullToEmpty(cfg.getString("deleteArchivedReposAfter")));
    this.protectedProjects =
        Arrays.asList(cfg.getStringList("protectedProject")).stream()
            .map(Pattern::compile)
            .collect(toList());

    checkDeprecatedScheduleConfigEntries();
    this.schedule =
        ScheduleConfig.builder(gerritConfig, "plugin")
            .setSubsection(pluginName)
            .setKeyInterval("cleanupInterval")
            .setKeyStartTime("cleanupStartTime")
            .setKeyJitter("cleanupJitter")
            .buildSchedule();
    this.trashFolderName = cfg.getString("trashFolderName", DEFAULT_TRASH_FOLDER_NAME);
  }

  public String getTrashFolderName() {
    return trashFolderName;
  }

  public long getDeleteTrashFoldersMaxAllowedTime() {
    return deleteTrashFoldersMaxAllowedTime;
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

  public List<Pattern> protectedProjects() {
    return protectedProjects;
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

  private Path getArchiveFolderFromConfig(String configValue) {
    try {
      return Files.createDirectories(Path.of(configValue));
    } catch (Exception e) {
      log.atWarning().log(
          "Failed to create folder %s: %s; using default path: %s",
          configValue, e.getMessage(), pluginData);
      return pluginData.toPath();
    }
  }

  private long getArchiveDurationFromConfig(String configValue) {
    try {
      return ConfigUtil.getTimeUnit(
          configValue, DAYS.toMillis(DEFAULT_ARCHIVE_DURATION_DAYS), MILLISECONDS);
    } catch (IllegalArgumentException e) {
      log.atWarning().log(
          "The configured archive duration is not valid: %s; using the default value: %d days",
          e.getMessage(), DEFAULT_ARCHIVE_DURATION_DAYS);
      return DAYS.toMillis(DEFAULT_ARCHIVE_DURATION_DAYS);
    }
  }

  private long getTrashFoldersMaxAllowedTimeFromConfig(String configValue) {
    long defaultConfigValue = MINUTES.toSeconds(DEFAULT_TRASH_FOLDER_MAX_ALLOWED_TIME_MINUTES);
    try {
      return ConfigUtil.getTimeUnit(configValue, defaultConfigValue, SECONDS);
    } catch (IllegalArgumentException e) {
      log.atWarning().log(
          "The configured trash folder max allowed time is not valid: %s; using the default value:"
              + " %d minutes",
          e.getMessage(), DEFAULT_TRASH_FOLDER_MAX_ALLOWED_TIME_MINUTES);
      return defaultConfigValue;
    }
  }

  private void checkDeprecatedScheduleConfigEntries() {
    List<String> deprecatedConfigEntries =
        List.of(
            "deleteTrashFolderInterval", "deleteTrashFolderStartTime", "deleteTrashFolderJitter");
    for (String deprecatedConfigEntry : deprecatedConfigEntries) {
      if (cfg.getString(deprecatedConfigEntry) != null) {
        log.atSevere().log("Ignored deprecated configuration value %s still in use. Check the docs for the available settings.", deprecatedConfigEntry);
      }
    }
  }

  public Optional<ScheduleConfig.Schedule> getSchedule() {
    return schedule;
  }
}
