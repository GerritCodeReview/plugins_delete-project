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

import com.google.gerrit.extensions.events.LifecycleListener;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.config.SitePaths;
import com.google.inject.Inject;

import org.eclipse.jgit.lib.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;

public class DeleteTrashFolders implements LifecycleListener {
  private static final Logger log = LoggerFactory.getLogger(DeleteTrashFolders.class);
  private File gitDir;

  @Inject
  public DeleteTrashFolders(SitePaths site, @GerritServerConfig Config cfg) {
    gitDir = site.resolve(cfg.getString("gerrit", null, "basePath"));
  }

  class TrashFolderRemover extends SimpleFileVisitor<Path> {
    @Override
    public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs)
        throws IOException {
      String fName = dir.getFileName().toString();
      // Search for directories which end with a dot, 13 digits and the string
      // ".deleted". A folder 'f' is renamed to 'f.<currentTimeMillis>.deleted'.
      // <currentTimeMillis> happens to be exactly 13 digits for commits created
      // between 2002 (before git was born) and 2285.
      if (fName.endsWith(".deleted")
          && fName.matches(".*\\d{13}.deleted$")) {
        log.warn("Will delete this folder: {}", dir);
        recursiveDelete(dir);
        return FileVisitResult.SKIP_SUBTREE;
      }
      return super.preVisitDirectory(dir, attrs);
    }

    @Override
    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
        throws IOException {
      // Whenever we detect a regular file named 'HEAD' contained in a folder
      // ending with ".git" then don't try to search for trash folders in this
      // subtree. We are in a GITDIR and don't expect trash folders inside
      // GITDIR's.
      if (attrs.isRegularFile() && file.getFileName().toString().equals("HEAD")
          && file.getParent().toString().endsWith(".git")) {
        return FileVisitResult.SKIP_SIBLINGS;
      }
      return super.visitFile(file, attrs);
    }

    /**
     * Recursively delete the specified file and all of its contents.
     *
     * @throws IOException
     */
    private void recursiveDelete(Path file) throws IOException {
      Files.walkFileTree(file, new SimpleFileVisitor<Path>() {
          @Override
          public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
              throws IOException {
            Files.delete(file);
            return FileVisitResult.CONTINUE;
          }

          @Override
          public FileVisitResult postVisitDirectory(Path dir, IOException e)
              throws IOException {
            if (e != null) {
              throw e;
            }
            Files.delete(dir);
            return FileVisitResult.CONTINUE;
          }
      });
    }
  }



  @Override
  public void start() {
    try {
      Files.walkFileTree(gitDir.toPath(), new TrashFolderRemover());
    } catch (IOException e) {
      log.warn("Exception occured while trying to delete trash folders", e);
    }
  }

  @Override
  public void stop() {
  }
}

