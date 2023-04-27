/*
 * Copyright (C) 2023 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.googlesource.gerrit.plugins.deleteproject.fs;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

import com.google.gerrit.entities.Project;
import com.google.gerrit.extensions.events.ProjectDeletedListener;
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.server.git.GitRepositoryManager;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.Optional;
import java.util.stream.Stream;
import org.eclipse.jgit.internal.storage.file.FileRepository;
import org.eclipse.jgit.lib.Repository;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class RepositoryDeleteTest {

  private static final boolean NO_ARCHIVE = false;
  private static final boolean NO_PRESERVE_GIT_REPO = false;
  private static final Optional<Path> NO_ARCHIVE_PATH = Optional.empty();

  @Mock private GitRepositoryManager repoManager;
  @Mock private ProjectDeletedListener projectDeleteListener;

  @Rule public TemporaryFolder tempFolder = new TemporaryFolder();

  private DynamicSet<ProjectDeletedListener> deletedListeners = DynamicSet.emptySet();
  private RepositoryDelete repositoryDelete;
  private Path basePath;

  @Before
  public void setUp() throws Exception {
    basePath = tempFolder.newFolder().toPath().resolve("base");
  }

  @Test
  public void shouldDeleteRepository() throws Exception {
    String repoName = "testRepo";
    Repository repository = createRepository(repoName);
    Project.NameKey nameKey = Project.nameKey(repoName);
    when(repoManager.openRepository(nameKey)).thenReturn(repository);
    repositoryDelete = new RepositoryDelete(repoManager);
    repositoryDelete.execute(
        nameKey, NO_PRESERVE_GIT_REPO, NO_ARCHIVE, NO_ARCHIVE_PATH, deletedListeners);
    assertThat(repository.getDirectory().exists()).isFalse();
  }

  @Test
  public void shouldDeleteEmptyParentFolders() throws Exception {
    String repoName = "a/b/c";
    Repository repository = createRepository(repoName);
    Project.NameKey nameKey = Project.nameKey(repoName);
    when(repoManager.openRepository(nameKey)).thenReturn(repository);
    repositoryDelete = new RepositoryDelete(repoManager);
    repositoryDelete.execute(
        nameKey, NO_PRESERVE_GIT_REPO, NO_ARCHIVE, NO_ARCHIVE_PATH, deletedListeners);
    assertThat(repository.getDirectory().exists()).isFalse();
  }

  @Test
  public void shouldKeepCommonFolders() throws Exception {
    String repoToDeleteName = "a/b/c/d";
    Repository repoToDelete = createRepository(repoToDeleteName);

    String repoToKeepName = "a/b/e";
    Repository repoToKeep = createRepository(repoToKeepName);

    Project.NameKey nameKey = Project.nameKey(repoToDeleteName);
    when(repoManager.openRepository(nameKey)).thenReturn(repoToDelete);
    repositoryDelete = new RepositoryDelete(repoManager);
    repositoryDelete.execute(
        nameKey, NO_PRESERVE_GIT_REPO, NO_ARCHIVE, NO_ARCHIVE_PATH, deletedListeners);
    assertThat(repoToDelete.getDirectory().exists()).isFalse();
    assertThat(repoToKeep.getDirectory().exists()).isTrue();
  }

  @Test
  public void shouldPreserveRepository() throws Exception {
    String repoName = "preservedRepo";
    boolean preserveGitRepo = true;
    Repository repository = createRepository(repoName);
    Project.NameKey nameKey = Project.nameKey(repoName);
    when(repoManager.openRepository(nameKey)).thenReturn(repository);
    repositoryDelete = new RepositoryDelete(repoManager);
    repositoryDelete.execute(
        nameKey, preserveGitRepo, NO_ARCHIVE, NO_ARCHIVE_PATH, deletedListeners);
    assertThat(repository.getDirectory().exists()).isTrue();
  }

  private FileRepository createRepository(String repoName) throws IOException {
    Path repoPath = Files.createDirectories(basePath.resolve(repoName));
    Repository repository = new FileRepository(repoPath.toFile());
    repository.create(true);
    return (FileRepository) repository;
  }

  @Test
  public void archiveRepository() throws Exception {
    String repoName = "parent_project/p3";
    Repository repository = createRepository(repoName);
    Path archiveFolder = basePath.resolve("test_archive");
    Project.NameKey nameKey = Project.nameKey(repoName);
    when(repoManager.openRepository(nameKey)).thenReturn(repository);
    repositoryDelete = new RepositoryDelete(repoManager);
    repositoryDelete.execute(
        nameKey, NO_PRESERVE_GIT_REPO, true, Optional.of(archiveFolder), deletedListeners);
    assertThat(repository.getDirectory().exists()).isFalse();
    String patternToVerify = archiveFolder.resolve(repoName).toString() + "*%archived%.git";
    assertThat(pathExistsWithPattern(archiveFolder, patternToVerify)).isTrue();
  }

  @Test
  public void shouldNotifyListenersOnSuccessfulRepoDeletion() throws Exception {
    String repoName = "testRepo";
    Repository repository = createRepository(repoName);
    Project.NameKey nameKey = Project.nameKey(repoName);
    when(repoManager.openRepository(nameKey)).thenReturn(repository);
    repositoryDelete = new RepositoryDelete(repoManager);
    deletedListeners.add("", projectDeleteListener);
    repositoryDelete.execute(
        nameKey, NO_PRESERVE_GIT_REPO, NO_ARCHIVE, NO_ARCHIVE_PATH, deletedListeners);
    Mockito.verify(projectDeleteListener).onProjectDeleted(any());
  }

  @Test
  public void shouldNotNotifyListenersIfTheListenersSetIsEmpty() throws Exception {
    String repoName = "testRepo";
    Repository repository = createRepository(repoName);
    Project.NameKey nameKey = Project.nameKey(repoName);
    when(repoManager.openRepository(nameKey)).thenReturn(repository);
    repositoryDelete = new RepositoryDelete(repoManager);
    repositoryDelete.execute(
        nameKey, NO_PRESERVE_GIT_REPO, NO_ARCHIVE, NO_ARCHIVE_PATH, deletedListeners);
    Mockito.verify(projectDeleteListener, never()).onProjectDeleted(any());
  }

  private boolean pathExistsWithPattern(Path archiveFolder, String patternToVerify)
      throws IOException {
    PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:" + patternToVerify);
    try (Stream<Path> stream = Files.walk(archiveFolder)) {
      return stream.anyMatch(matcher::matches);
    }
  }
}
