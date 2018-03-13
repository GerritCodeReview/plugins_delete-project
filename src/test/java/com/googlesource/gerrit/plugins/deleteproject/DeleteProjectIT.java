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
import static com.googlesource.gerrit.plugins.deleteproject.DeleteProjectIT.TagType.LIGHTWEIGHT;

import com.google.common.base.Joiner;
import com.google.common.base.MoreObjects;
import com.google.gerrit.acceptance.GerritConfig;
import com.google.gerrit.acceptance.GitUtil;
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
import com.google.gwt.thirdparty.json.JSONObject;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;
import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.PushResult;
import org.eclipse.jgit.transport.RemoteRefUpdate;
import org.eclipse.jgit.transport.RemoteRefUpdate.Status;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

@UseSsh
@TestPlugin(
  name = "delete-project",
  sysModule = "com.googlesource.gerrit.plugins.deleteproject.Module",
  sshModule = "com.googlesource.gerrit.plugins.deleteproject.SshModule",
  httpModule = "com.googlesource.gerrit.plugins.deleteproject.HttpModule"
)
public class DeleteProjectIT extends LightweightPluginDaemonTest {

  private static final String PLUGIN = "delete-project";
  private static final String NEW_LINE = "\n";
  private static final String ARCHIVE_FOLDER = "archiveFolder";
  private static final String PARENT_FOLDER = "par";

  private File archiveFolder;
  private String cmd;
  private StringBuilder msgBuilder;

  @Rule public TemporaryFolder tempFolder = new TemporaryFolder();

  enum TagType {
    LIGHTWEIGHT(Permission.CREATE);
    final String createPermission;

    TagType(String createPermission) {
      this.createPermission = createPermission;
    }
  }

  @Before
  public void beforeTestSetUp() throws Exception {
    msgBuilder = new StringBuilder();
    archiveFolder = Paths.get(ARCHIVE_FOLDER).toFile();
    if (archiveFolder.exists()) {
      clear(archiveFolder, true);
    }
  }

  @Test
  @UseLocalDisk
  public void testHttpDeleteProjectForce() throws Exception {
    File projectDir = verifyProjectRepo(project);

    setApiUser(user);
    sender.clear();
    String endPoint = "/projects/" + project.get() + "/delete-project~delete";
    RestResponse r = adminRestSession.post(endPoint, new JSONObject().put("force", "true"));

    r.assertNoContent();
    assertThat(projectDir.exists()).isFalse();
  }

  @Test
  @UseLocalDisk
  public void testHttpDeleteProjectWithWatches() throws Exception {
    setApiUser(user);
    watch(project.get(), null);

    File projectDir = verifyProjectRepo(project);

    setApiUser(user);
    sender.clear();
    String endPoint = "/projects/" + project.get() + "/delete-project~delete";
    RestResponse r = adminRestSession.post(endPoint, new JSONObject().put("force", "true"));

    r.assertNoContent();
    assertThat(projectDir.exists()).isFalse();
  }

  @Test
  @UseLocalDisk
  public void testSshDeleteProjWithoutOptions() throws Exception {
    createChange();
    File projectDir = verifyProjectRepo(project);

    cmd = createCommand("delete", project.get());
    msgBuilder.append("Really delete ");
    msgBuilder.append(project.get());
    msgBuilder.append("?\n");
    msgBuilder.append("This is an operation which permanently deletes ");
    msgBuilder.append("data. This cannot be undone!\n");
    msgBuilder.append("If you are sure you wish to delete this project, ");
    msgBuilder.append("re-run\n");
    msgBuilder.append("with the --yes-really-delete flag.\n");
    adminSshSession.exec(cmd);

    assertThat(projectDir.exists()).isTrue();
    assertThat(adminSshSession.getError()).isEqualTo(msgBuilder.toString() + NEW_LINE);
  }

  @Test
  @UseLocalDisk
  public void testSshDeleteProjYesReallyDelete() throws Exception {
    createChange();
    File projectDir = verifyProjectRepo(project);

    cmd = createCommand("delete", "--yes-really-delete", project.get());
    msgBuilder.append("There are warnings against deleting ");
    msgBuilder.append(project.get());
    msgBuilder.append(":\n");
    msgBuilder.append(" * ");
    msgBuilder.append(project.get() + " has open changes");
    msgBuilder.append("\n");
    msgBuilder.append("To really delete ");
    msgBuilder.append(project.get());
    msgBuilder.append(", re-run with the --force flag.");
    adminSshSession.exec(cmd);

    assertThat(projectDir.exists()).isTrue();
    assertThat(adminSshSession.getError()).isEqualTo(msgBuilder.toString() + NEW_LINE);
  }

  @Test
  @UseLocalDisk
  public void testSshDeleteProjYesReallyDeleteForce() throws Exception {
    createChange();
    File projectDir = verifyProjectRepo(project);
    cmd = createCommand("delete", "--yes-really-delete", "--force", project.get());
    adminSshSession.exec(cmd);
    assertThat(adminSshSession.getError()).isNull();
    assertThat(projectDir.exists()).isFalse();
  }

  @Test
  @UseLocalDisk
  @GerritConfig(name = "plugin.delete-project.enablePreserveOption", value = "true")
  public void testSshDeleteProjPreserveGitRepoEnabled() throws Exception {
    File projectDir = verifyProjectRepo(project);
    cmd =
        createCommand("delete", "--yes-really-delete", "--preserve-git-repository", project.get());
    adminSshSession.exec(cmd);

    assertThat(adminSshSession.getError()).isNull();
    assertThat(projectDir.exists()).isTrue();
  }

  @Test
  @UseLocalDisk
  @GerritConfig(name = "plugin.delete-project.enablePreserveOption", value = "false")
  public void testSshDeleteProjPreserveGitRepoNotEnabled() throws Exception {
    File projectDir = verifyProjectRepo(project);
    cmd =
        createCommand("delete", "--yes-really-delete", "--preserve-git-repository", project.get());
    adminSshSession.exec(cmd);
    msgBuilder.append("Since the enablePreserveOption is configured to be false, ");
    msgBuilder.append("the --preserve-git-repository option is not allowed to be used.\n");
    msgBuilder.append("Please remove this option and retry.");

    assertThat(adminSshSession.getError()).isEqualTo(msgBuilder.toString() + NEW_LINE);
    assertThat(projectDir.exists()).isTrue();
  }

  @Test
  @UseLocalDisk
  @GerritConfig(name = "plugin.delete-project.hideProjectOnPreserve", value = "true")
  public void testSshHideProject() throws Exception {
    File projectDir = verifyProjectRepo(project);
    cmd =
        createCommand("delete", "--yes-really-delete", "--preserve-git-repository", project.get());
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
    File projectDir = verifyProjectRepo(project);
    String childrenString = createProject("foo", project, true).get();
    Repository childProjRepo = repoManager.openRepository(Project.NameKey.parse(childrenString));
    File childProjDir = childProjRepo.getDirectory();
    assertThat(childProjDir.exists()).isTrue();

    cmd = createCommand("delete", "--yes-really-delete", project.get());
    adminSshSession.exec(cmd);
    assertThat(adminSshSession.getError())
        .isEqualTo(
            "fatal: Cannot delete project because it has children: " + childrenString + NEW_LINE);
    assertThat(projectDir.exists()).isTrue();
  }

  @Test
  @UseLocalDisk
  public void testDeleteAllProject() throws Exception {
    String name = allProjects.get();
    File allProjDir = verifyProjectRepo(Project.NameKey.parse(name));
    cmd = createCommand("delete", "--yes-really-delete", name);
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
    File projectDir = verifyProjectRepo(project);
    for (TagType tagType : TagType.values()) {
      grant(tagType.createPermission, project, "refs/tags/*", false, REGISTERED_USERS);
      pushTagOldCommitNotForce(tagType, null, Status.OK);
    }

    cmd = createCommand("delete", "--yes-really-delete", project.get());
    adminSshSession.exec(cmd);
    assertThat(adminSshSession.getError())
        .isEqualTo("fatal: Project " + project.get() + " has tags" + NEW_LINE);
    assertThat(projectDir.exists()).isTrue();
  }

  @Test
  @UseLocalDisk
  @GerritConfig(name = "plugin.delete-project.archiveDeletedRepos", value = "true")
  @GerritConfig(name = "plugin.delete-project.archiveFolder", value = ARCHIVE_FOLDER)
  public void testArchiveProject() throws Exception {
    assertThat(archiveFolder.exists()).isTrue();
    assertThat(isDirEmpty(archiveFolder.toPath())).isTrue();

    File projectDir = verifyProjectRepo(project);

    cmd = createCommand("delete", "--yes-really-delete", project.get());
    adminSshSession.exec(cmd);

    assertThat(adminSshSession.getError()).isNull();
    assertThat(isDirEmpty(archiveFolder.toPath())).isFalse();
    assertThat(containsDeleteProject(archiveFolder.toPath(), project.get())).isTrue();
    assertThat(projectDir.exists()).isFalse();
  }

  @Test
  @UseLocalDisk
  public void deleteProjectWithParentFolder() throws Exception {
    String projectName = createProject("pj1").get();
    File projectDir = verifyProjectRepo(Project.NameKey.parse(projectName));

    Path parentFolder = projectDir.toPath().getParent().resolve(PARENT_FOLDER).resolve(projectName);
    FileUtils.moveDirectory(projectDir, parentFolder.toFile());
    assertThat(parentFolder.toFile().exists()).isTrue();
    assertThat(isDirEmpty(parentFolder)).isFalse();

    cmd = createCommand("delete", "--yes-really-delete", PARENT_FOLDER + "/" + projectName);
    adminSshSession.exec(cmd);

    assertThat(projectDir.exists()).isFalse();
    assertThat(parentFolder.toFile().exists()).isFalse();
  }

  public File verifyProjectRepo(NameKey name) throws IOException {
    Repository projectRepo = repoManager.openRepository(name);
    File projectDir = projectRepo.getDirectory();
    assertThat(projectDir.exists()).isTrue();
    return projectDir;
  }

  private void clear(File folder, boolean keepRoot) {
    File[] files = folder.listFiles();
    if (files != null) {
      for (File f : files) {
        if (f.isDirectory()) {
          clear(f, false);
        } else {
          f.delete();
        }
      }
    }
    if (!keepRoot) {
      folder.delete();
    }
  }

  private String createCommand(String cmd, String... params) {
    return Joiner.on(" ").join(PLUGIN, cmd, Joiner.on(" ").join(params));
  }

  private String pushTagOldCommitNotForce(TagType tagType, String tagName, Status expectedStatus)
      throws Exception {
    testRepo = cloneProject(project, user);
    commit(user.getIdent(), "subject");
    boolean createTag = tagName == null;
    tagName = MoreObjects.firstNonNull(tagName, "v1_" + System.nanoTime());

    grant(Permission.SUBMIT, project, "refs/for/refs/heads/master", false, REGISTERED_USERS);
    pushHead(testRepo, "refs/for/master%submit");

    String tagRef = RefNames.REFS_TAGS + tagName;
    PushResult r =
        tagType == LIGHTWEIGHT
            ? pushHead(testRepo, tagRef, false, false)
            : GitUtil.pushTag(testRepo, tagName, !createTag);
    RemoteRefUpdate refUpdate = r.getRemoteUpdate(tagRef);
    assertThat(refUpdate.getStatus()).named(tagType.name()).isEqualTo(expectedStatus);
    return tagName;
  }

  private void commit(PersonIdent ident, String subject) throws Exception {
    commitBuilder().ident(ident).message(subject + " (" + System.nanoTime() + ")").create();
  }

  private boolean isDirEmpty(final Path dir) throws IOException {
    try (Stream<Path> dirStream = Files.list(dir)) {
      return dirStream.count() == 0;
    }
  }

  private boolean containsDeleteProject(final Path dir, String projectName) throws IOException {
    try (Stream<Path> dirStream = Files.list(dir)) {
      return dirStream.iterator().next().toString().contains(projectName);
    }
  }
}
