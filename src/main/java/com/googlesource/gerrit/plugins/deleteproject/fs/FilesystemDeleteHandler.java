// Copyright (C) 2013 The Android Open Source Project
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

import com.google.gerrit.entities.Project;
import com.google.gerrit.extensions.events.ProjectDeletedListener;
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.inject.Inject;
import com.googlesource.gerrit.plugins.deleteproject.Configuration;
import java.io.IOException;
import java.util.Optional;
import org.eclipse.jgit.errors.RepositoryNotFoundException;

public class FilesystemDeleteHandler {
  private final RepositoryDelete repositoryDelete;
  private final DynamicSet<ProjectDeletedListener> deletedListeners;
  private final Configuration config;

  @Inject
  public FilesystemDeleteHandler(
      RepositoryDelete repositoryDelete,
      DynamicSet<ProjectDeletedListener> deletedListeners,
      Configuration config) {
    this.repositoryDelete = repositoryDelete;
    this.deletedListeners = deletedListeners;
    this.config = config;
  }

  public void delete(Project.NameKey project, boolean preserveGitRepository)
      throws IOException, RepositoryNotFoundException {
    repositoryDelete.execute(
        project,
        preserveGitRepository,
        config.shouldArchiveDeletedRepos(),
        Optional.ofNullable(config.getArchiveFolder()),
        deletedListeners);
  }
}
