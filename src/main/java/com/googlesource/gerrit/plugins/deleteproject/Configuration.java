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

import com.google.gerrit.extensions.annotations.PluginName;
import com.google.gerrit.server.config.PluginConfig;
import com.google.gerrit.server.config.PluginConfigFactory;
import com.google.inject.Inject;
import com.google.inject.Singleton;

@Singleton
public class Configuration {
  private static String DELETED_PROJECTS_PARENT = "Deleted-Projects";

  private final boolean allowDeletionWithTags;
  private final boolean hideProjectOnPreserve;
  private final String deletedProjectsParent;

  @Inject
  public Configuration(PluginConfigFactory pluginConfigFactory, @PluginName String pluginName) {
    PluginConfig cfg = pluginConfigFactory.getFromGerritConfig(pluginName);
    allowDeletionWithTags = cfg.getBoolean("allowDeletionOfReposWithTags", true);
    hideProjectOnPreserve = cfg.getBoolean("hideProjectOnPreserve", false);
    deletedProjectsParent =
        cfg.getString("parentForDeletedProjects", DELETED_PROJECTS_PARENT);
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
}