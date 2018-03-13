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

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class FilesystemDeleteHandlerTest {
  private static final int NUMBER_OF_FOLDERS = 10;
  private static final int NUMBER_OF_FILES = 10;

  private FilesystemDeleteHandler fsDeleteHandler;
  private Path archiveRepo;
  private Path projectRepo;

  @Rule public TemporaryFolder tempFolder = new TemporaryFolder();

  @Before
  public void setUp() throws Exception {
    fsDeleteHandler = new FilesystemDeleteHandler(null, null, null);
    archiveRepo = tempFolder.newFolder("archive").toPath();
    projectRepo = tempFolder.newFolder("project").toPath();
  }

  @Test
  public void recursiveArchiveTest() throws IOException {
    assertThat(isDirEmpty(archiveRepo)).isTrue();

    projectRepo.resolve("myPj").toFile().mkdir();
    for (int i = 0; i < NUMBER_OF_FOLDERS; i++) {
      projectRepo.resolve("myPj" + i).toFile().mkdir();
    }
    for (int i = 0; i < NUMBER_OF_FOLDERS; i++) {
      projectRepo.resolve("newFile" + i + "txt").toFile().createNewFile();
    }
    archiveRepo = archiveRepo.resolve("pj");
    fsDeleteHandler.recursiveArchive(projectRepo, archiveRepo);
    assertThat(isDirEmpty(archiveRepo)).isFalse();
    assertThat(numberOfSubDir(archiveRepo) - 1).isEqualTo(NUMBER_OF_FOLDERS + NUMBER_OF_FILES);
  }

  @Test
  public void recursiveDeleteTest() throws IOException {
    assertThat(isDirEmpty(projectRepo)).isTrue();

    projectRepo.resolve("myPj").toFile().mkdir();
    for (int i = 0; i < NUMBER_OF_FOLDERS; i++) {
      projectRepo.resolve("myPj" + i).toFile().mkdir();
    }
    for (int i = 0; i < NUMBER_OF_FOLDERS; i++) {
      projectRepo.resolve("newFile" + i + "txt").toFile().createNewFile();
    }
    assertThat(isDirEmpty(projectRepo)).isFalse();
    fsDeleteHandler.recursiveDelete(projectRepo);
    assertThat(projectRepo.toFile().exists()).isFalse();
  }

  private boolean isDirEmpty(final Path dir) throws IOException {
    try (DirectoryStream<Path> dirStream = Files.newDirectoryStream(dir)) {
      return !dirStream.iterator().hasNext();
    }
  }

  private long numberOfSubDir(final Path dir) throws IOException {
    try (Stream<Path> dirStream = Files.list(dir)) {
      return dirStream.count();
    }
  }
}
