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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Sets;
import com.google.gerrit.extensions.events.LifecycleListener;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.config.RepositoryConfig;
import com.google.gerrit.server.config.SitePaths;
import com.google.inject.Inject;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Set;
import java.util.regex.Pattern;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.RepositoryCache.FileKey;
import org.eclipse.jgit.util.FS;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DeleteTrashFolders implements LifecycleListener {
  private static final Logger log = LoggerFactory.getLogger(DeleteTrashFolders.class);

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

    @VisibleForTesting
    static final boolean match(String fName) {
      return TRASH_1.matcher(fName).matches() || TRASH_2.matcher(fName).matches();
    }

    static boolean match(Path dir) {
      return match(dir.getFileName().toString());
    }
  }

  private Set<Path> repoFolders;
  private Thread thread;

  @Inject
  public DeleteTrashFolders(
      SitePaths site, @GerritServerConfig Config cfg, RepositoryConfig repositoryCfg) {
    repoFolders = Sets.newHashSet();
    repoFolders.add(site.resolve(cfg.getString("gerrit", null, "basePath")));
    repoFolders.addAll(repositoryCfg.getAllBasePaths());
  }

  class TrashFolderRemover extends SimpleFileVisitor<Path> {

    @Override
    public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs)
        throws IOException {
      String fName = dir.getFileName().toString();
      if (TrashFolderPredicate.match(fName)) {
        log.warn("Will delete this folder: {}", dir);
        recursiveDelete(dir);
        return FileVisitResult.SKIP_SUBTREE;
      } else if (FileKey.isGitRepository(dir.toFile(), FS.DETECTED)) {
        // We are in a GITDIR and don't expect trash folders inside GITDIR's.
        return FileVisitResult.SKIP_SUBTREE;
      }

      return super.preVisitDirectory(dir, attrs);
    }

    /**
     * Recursively delete the specified file and all of its contents.
     *
     * @throws IOException
     */
    private void recursiveDelete(Path file) throws IOException {
      Files.walkFileTree(
          file,
          new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
                throws IOException {
              Files.delete(file);
              return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException e) throws IOException {
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
    thread =
        new Thread(
            () -> {
              for (Path folder : repoFolders) {
                if (!folder.toFile().exists()) {
                  log.debug("Base path {} does not exist", folder);
                  continue;
                }
                try {
                  Files.walkFileTree(folder, new TrashFolderRemover());
                } catch (IOException e) {
                  log.warn("Exception while trying to delete trash folders", e);
                }
              }
            },
            "DeleteTrashFolders");
    thread.start();
  }

  @VisibleForTesting
  Thread getWorkerThread() {
    return thread;
  }

  @Override
  public void stop() {}
}
