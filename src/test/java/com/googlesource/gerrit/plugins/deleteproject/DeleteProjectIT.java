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

import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.acceptance.GitUtil.pushHead;
import static com.google.gerrit.server.group.SystemGroupBackend.REGISTERED_USERS;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

import com.google.common.base.Joiner;
import com.google.common.base.MoreObjects;
import com.google.gerrit.acceptance.GerritConfig;
import com.google.gerrit.acceptance.LightweightPluginDaemonTest;
import com.google.gerrit.acceptance.RestResponse;
import com.google.gerrit.acceptance.TestPlugin;
import com.google.gerrit.acceptance.UseLocalDisk;
import com.google.gerrit.acceptance.UseSsh;
import com.google.gerrit.common.data.Permission;
import com.google.gerrit.extensions.client.ProjectState;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.client.Project.NameKey;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.server.git.ProjectConfig;
import com.googlesource.gerrit.plugins.deleteproject.DeleteProject.Input;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.PushResult;
import org.eclipse.jgit.transport.RemoteRefUpdate;
import org.eclipse.jgit.transport.RemoteRefUpdate.Status;
import org.junit.Before;
import org.junit.Test;

@UseSsh
@TestPlugin(
  name = "delete-project",
  sysModule = "com.googlesource.gerrit.plugins.deleteproject.Module",
  sshModule = "com.googlesource.gerrit.plugins.deleteproject.SshModule",
  httpModule = "com.googlesource.gerrit.plugins.deleteproject.HttpModule"
)
public class DeleteProjectIT extends LightweightPluginDaemonTest {

  private static final String PLUGIN = "delete-project";
  private static final String ARCHIVE_FOLDER = "archiveFolder";
  private static final String PARENT_FOLDER = "parentFolder";

  private File archiveFolder;
  private File projectDir;

  @Before
  public void archiveAndProjectFoldeSetUp() throws IOException {
    archiveFolder = Paths.get(ARCHIVE_FOLDER).toFile();
    if (archiveFolder.exists()) {
      deleteFolderContents(archiveFolder, true);
    }
    projectDir = verifyProjectRepoExists(project);
  }

  @Test
  @UseLocalDisk
  public void testHttpDeleteProjectForce() throws Exception {
    RestResponse r = httpDeleteProjectHelper(true);
    r.assertNoContent();
    assertThat(projectDir.exists()).isFalse();
  }

  @Test
  @UseLocalDisk
  public void testHttpDeleteProjectNotForce() throws Exception {
    createChange();
    RestResponse r = httpDeleteProjectHelper(false);
    r.assertConflict();
    assertThat(projectDir.exists()).isTrue();
  }

  @Test
  @UseLocalDisk
  public void testHttpDeleteProjectWithWatches() throws Exception {
    watch(project.get(), null);
    RestResponse r = httpDeleteProjectHelper(true);
    r.assertNoContent();
    assertThat(projectDir.exists()).isFalse();
  }

  @Test
  @UseLocalDisk
  public void testSshDeleteProjectWithoutOptions() throws Exception {
    createChange();
    String cmd = Joiner.on(" ").join(PLUGIN, "delete", project.get());
    StringBuilder msgBuilder = new StringBuilder();
    msgBuilder.append("Really delete ");
    msgBuilder.append(project.get());
    msgBuilder.append("?\n");
    msgBuilder.append("This is an operation which permanently deletes ");
    msgBuilder.append("data. This cannot be undone!\n");
    msgBuilder.append("If you are sure you wish to delete this project, ");
    msgBuilder.append("re-run\n");
    msgBuilder.append("with the --yes-really-delete flag.\n\n");
    adminSshSession.exec(cmd);

    assertThat(projectDir.exists()).isTrue();
    assertThat(adminSshSession.getError()).isEqualTo(msgBuilder.toString());
  }

  @Test
  @UseLocalDisk
  public void testSshDeleteProjYesReallyDelete() throws Exception {
    createChange();

    String cmd = createDeleteCommand(project.get());
    StringBuilder msgBuilder = new StringBuilder();
    msgBuilder.append("There are warnings against deleting ");
    msgBuilder.append(project.get());
    msgBuilder.append(":\n");
    msgBuilder.append(" * ");
    msgBuilder.append(project.get() + " has open changes");
    msgBuilder.append("\n");
    msgBuilder.append("To really delete ");
    msgBuilder.append(project.get());
    msgBuilder.append(", re-run with the --force flag.\n");
    adminSshSession.exec(cmd);

    assertThat(projectDir.exists()).isTrue();
    assertThat(adminSshSession.getError()).isEqualTo(msgBuilder.toString());
  }

  @Test
  @UseLocalDisk
  public void testSshDeleteProjYesReallyDeleteForce() throws Exception {
    createChange();
    String cmd = createDeleteCommand("--force", project.get());
    adminSshSession.exec(cmd);
    assertThat(adminSshSession.getError()).isNull();
    assertThat(projectDir.exists()).isFalse();
  }

  @Test
  @UseLocalDisk
  @GerritConfig(name = "plugin.delete-project.enablePreserveOption", value = "true")
  public void testSshDeleteProjPreserveGitRepoEnabled() throws Exception {
    String cmd = createDeleteCommand("--preserve-git-repository", project.get());
    adminSshSession.exec(cmd);

    assertThat(adminSshSession.getError()).isNull();
    assertThat(projectDir.exists()).isTrue();
  }

  @Test
  @UseLocalDisk
  @GerritConfig(name = "plugin.delete-project.enablePreserveOption", value = "false")
  public void testSshDeleteProjPreserveGitRepoNotEnabled() throws Exception {
    String cmd = createDeleteCommand("--preserve-git-repository", project.get());
    adminSshSession.exec(cmd);
    StringBuilder msgBuilder = new StringBuilder();
    msgBuilder.append("Since the enablePreserveOption is configured to be false, ");
    msgBuilder.append("the --preserve-git-repository option is not allowed to be used.\n");
    msgBuilder.append("Please remove this option and retry.\n");

    assertThat(adminSshSession.getError()).isEqualTo(msgBuilder.toString());
    assertThat(projectDir.exists()).isTrue();
  }

  @Test
  @UseLocalDisk
  @GerritConfig(name = "plugin.delete-project.hideProjectOnPreserve", value = "true")
  public void testSshHideProject() throws Exception {
    String cmd = createDeleteCommand("--preserve-git-repository", project.get());
    adminSshSession.exec(cmd);

    ProjectConfig cfg = projectCache.checkedGet(project).getConfig();
    ProjectState state = cfg.getProject().getState();

    assertThat(state).isEqualTo(ProjectState.HIDDEN);
    assertThat(adminSshSession.getError()).isNull();
    assertThat(projectDir.exists()).isTrue();
  }

  @Test
  @UseLocalDisk
  public void testDeleteProjWithChildren() throws Exception {
    String childrenString = createProject("foo", project, true).get();
    verifyProjectRepoExists(Project.NameKey.parse(childrenString));

    String cmd = createDeleteCommand(project.get());
    adminSshSession.exec(cmd);
    assertThat(adminSshSession.getError())
        .isEqualTo(
            "fatal: Cannot delete project because it has children: " + childrenString + "\n");
    assertThat(projectDir.exists()).isTrue();
  }

  @Test
  @UseLocalDisk
  public void testDeleteAllProject() throws Exception {
    String name = allProjects.get();
    File allProjDir = verifyProjectRepoExists(Project.NameKey.parse(name));
    String cmd = createDeleteCommand(name);
    adminSshSession.exec(cmd);
    String path =
        adminSshSession
            .getError()
            .replace("fatal: Perhaps you meant to rm -fR ", "")
            .replace("\n", "");
    File testRepo = new File(path);
    assertThat(testRepo.exists()).isTrue();
    assertThat(allProjDir.exists()).isTrue();
  }

  @Test
  @UseLocalDisk
  @GerritConfig(name = "plugin.delete-project.allowDeletionOfReposWithTags", value = "false")
  public void testDeleteProjWithTags() throws Exception {
    grant(Permission.CREATE, project, "refs/tags/*", false, REGISTERED_USERS);
    pushTagOldCommitNotForce(Status.OK);

    String cmd = createDeleteCommand(project.get());
    adminSshSession.exec(cmd);
    assertThat(adminSshSession.getError())
        .isEqualTo("fatal: Project " + project.get() + " has tags" + "\n");
    assertThat(projectDir.exists()).isTrue();
  }

  @Test
  @UseLocalDisk
  @GerritConfig(name = "plugin.delete-project.archiveDeletedRepos", value = "true")
  @GerritConfig(name = "plugin.delete-project.archiveFolder", value = ARCHIVE_FOLDER)
  public void testArchiveProject() throws Exception {
    assertThat(archiveFolder.exists()).isTrue();
    assertThat(isEmpty(archiveFolder.toPath())).isTrue();

    String cmd = createDeleteCommand(project.get());
    adminSshSession.exec(cmd);

    assertThat(adminSshSession.getError()).isNull();
    assertThat(isEmpty(archiveFolder.toPath())).isFalse();
    assertThat(containsDeletedProject(archiveFolder.toPath(), project.get())).isTrue();
    assertThat(projectDir.exists()).isFalse();
  }

  @Test
  @UseLocalDisk
  @GerritConfig(name = "plugin.delete-project.archiveDeletedRepos", value = "true")
  @GerritConfig(name = "plugin.delete-project.archiveFolder", value = ARCHIVE_FOLDER)
  public void testDeleteAndArchiveProjectWithParentFolder() throws Exception {
    assertThat(archiveFolder.exists()).isTrue();
    assertThat(isEmpty(archiveFolder.toPath())).isTrue();

    String name = "pj1";
    String projectName = createProject(name).get();
    File projectDir = verifyProjectRepoExists(Project.NameKey.parse(projectName));

    Path parentFolder = projectDir.toPath().getParent().resolve(PARENT_FOLDER).resolve(projectName);
    parentFolder.toFile().mkdirs();
    assertThat(parentFolder.toFile().exists()).isTrue();
    assertThat(isEmpty(parentFolder)).isTrue();

    Files.move(projectDir.toPath(), parentFolder, REPLACE_EXISTING);
    assertThat(parentFolder.toFile().exists()).isTrue();
    assertThat(isEmpty(parentFolder)).isFalse();

    String cmd = createDeleteCommand(PARENT_FOLDER + "/" + projectName);
    adminSshSession.exec(cmd);

    assertThat(isEmpty(archiveFolder.toPath())).isFalse();
    assertThat(containsDeletedProject(archiveFolder.toPath().resolve(PARENT_FOLDER), name))
        .isTrue();
    assertThat(projectDir.exists()).isFalse();
    assertThat(adminSshSession.getError()).isNull();

    assertThat(projectDir.exists()).isFalse();
    assertThat(parentFolder.toFile().exists()).isFalse();
  }

  private File verifyProjectRepoExists(NameKey name) throws IOException {
    Repository projectRepo = repoManager.openRepository(name);
    File projectDir = projectRepo.getDirectory();
    assertThat(projectDir.exists()).isTrue();
    return projectDir;
  }

  private RestResponse httpDeleteProjectHelper(boolean force) throws Exception {
    setApiUser(user);
    sender.clear();
    String endPoint = "/projects/" + project.get() + "/delete-project~delete";
    Input i = new Input();
    i.force = force;
    RestResponse r = adminRestSession.post(endPoint, i);

    return r;
  }

  private void deleteFolderContents(File folder, boolean keepRoot) {
    File[] files = folder.listFiles();
    if (files != null) {
      for (File f : files) {
        if (f.isDirectory()) {
          deleteFolderContents(f, false);
        } else {
          f.delete();
        }
      }
    }
    if (!keepRoot) {
      folder.delete();
    }
  }

  private String createDeleteCommand(String cmd, String... params) {
    return Joiner.on(" ")
        .join(PLUGIN, "delete", "--yes-really-delete", cmd, Joiner.on(" ").join(params));
  }

  private String pushTagOldCommitNotForce(Status expectedStatus) throws Exception {
    testRepo = cloneProject(project, user);
    commitBuilder().ident(user.getIdent()).message("subject (" + System.nanoTime() + ")").create();
    String tagName = MoreObjects.firstNonNull(null, "v1_" + System.nanoTime());

    grant(Permission.SUBMIT, project, "refs/for/refs/heads/master", false, REGISTERED_USERS);
    pushHead(testRepo, "refs/for/master%submit");

    String tagRef = RefNames.REFS_TAGS + tagName;
    PushResult r = pushHead(testRepo, tagRef, false, false);
    RemoteRefUpdate refUpdate = r.getRemoteUpdate(tagRef);
    assertThat(refUpdate.getStatus()).named("LIGHTWEIGHT").isEqualTo(expectedStatus);
    return tagName;
  }

  private boolean isEmpty(Path dir) throws IOException {
    try (Stream<Path> dirStream = Files.list(dir)) {
      return !dirStream.iterator().hasNext();
    }
  }

  private boolean containsDeletedProject(Path dir, String projectName) throws IOException {
    try (Stream<Path> dirStream = Files.list(dir)) {
      return dirStream.filter(d -> d.toString().contains(projectName)).findFirst().isPresent();
    }
  }
}
