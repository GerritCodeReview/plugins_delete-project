// Copyright (C) 2018 Ericsson
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
import com.google.gerrit.acceptance.TestPlugin;
import com.google.gerrit.acceptance.UseLocalDisk;
import com.google.gerrit.acceptance.UseSsh;
import com.google.gerrit.common.data.Permission;
import com.google.gerrit.reviewdb.client.RefNames;
import java.io.File;
import org.eclipse.jgit.lib.PersonIdent;
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
  private static final String NEW_LINE = "\n";

  private String cmd;
  private StringBuilder msgBuilder;

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
  }

  @Test
  @UseLocalDisk
  public void testSshDeleteProjWithoutOptions() throws Exception {
    createChange();
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
    assertThat(adminSshSession.getError()).isEqualTo(msgBuilder.toString() + NEW_LINE);
  }

  @Test
  @UseLocalDisk
  public void testSshDeleteProjYesReallyDelete() throws Exception {
    createChange();
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
    assertThat(adminSshSession.getError()).isEqualTo(msgBuilder.toString() + NEW_LINE);
  }

  @Test
  @UseLocalDisk
  public void testSshDeleteProjYesReallyDeleteForce() throws Exception {
    createChange();
    repoManager.openRepository(project);
    cmd = createCommand("delete", "--yes-really-delete", "--force", project.get());
    adminSshSession.exec(cmd);
    assertThat(adminSshSession.getError()).isNull();
  }

  @Test
  @UseLocalDisk
  public void testSshDeleteProjPreserveGitRepoEnabled() throws Exception {
    cmd =
        createCommand("delete", "--yes-really-delete", "--preserve-git-repository", project.get());
    adminSshSession.exec(cmd);
    assertThat(adminSshSession.getError()).isNull();
  }

  @Test
  @UseLocalDisk
  @GerritConfig(name = "plugin.delete-project.enablePreserveOption", value = "false")
  public void testSshDeleteProjPreserveGitRepoNotEnabled() throws Exception {
    cmd =
        createCommand("delete", "--yes-really-delete", "--preserve-git-repository", project.get());
    adminSshSession.exec(cmd);
    msgBuilder.append("Since the enablePreserveOption is configured to be false, ");
    msgBuilder.append("the --preserve-git-repository option is not allowed to be used.\n");
    msgBuilder.append("Please remove this option and retry.");
    assertThat(adminSshSession.getError()).isEqualTo(msgBuilder.toString() + NEW_LINE);
  }

  @Test
  @UseLocalDisk
  @GerritConfig(name = "plugin.delete-project.hideProjectOnPreserve", value = "true")
  public void testHideProject() throws Exception {
    cmd =
        createCommand("delete", "--yes-really-delete", "--preserve-git-repository", project.get());
    adminSshSession.exec(cmd);
    assertThat(adminSshSession.getError()).isNull();
  }

  @Test
  @UseLocalDisk
  public void testDeleteProjWithChildren() throws Exception {
    String childrenString = createProject("foo", project, true).get();
    cmd = createCommand("delete", "--yes-really-delete", project.get());
    adminSshSession.exec(cmd);
    assertThat(adminSshSession.getError())
        .isEqualTo(
            "fatal: Cannot delete project because it has children: " + childrenString + NEW_LINE);
  }

  @Test
  @UseLocalDisk
  public void testDeleteAllProject() throws Exception {
    String name = allProjects.get();
    cmd = createCommand("delete", "--yes-really-delete", name);
    adminSshSession.exec(cmd);
    String path =
        adminSshSession
            .getError()
            .replace("fatal: Perhaps you meant to rm -fR ", "")
            .replace("\n", "");
    File testRepo = new File(path);
    assertThat(testRepo.exists()).isTrue();
  }

  @Test
  @UseLocalDisk
  @GerritConfig(name = "plugin.delete-project.allowDeletionOfReposWithTags", value = "false")
  public void testDeleteProjWithTags() throws Exception {
    for (TagType tagType : TagType.values()) {
      grant(tagType.createPermission, project, "refs/tags/*", false, REGISTERED_USERS);
      pushTagOldCommitNotForce(tagType, null, Status.OK);
    }

    cmd = createCommand("delete", "--yes-really-delete", project.get());
    adminSshSession.exec(cmd);
    assertThat(adminSshSession.getError())
        .isEqualTo("fatal: Project " + project.get() + " has tags" + NEW_LINE);
  }

  @Test
  @UseLocalDisk
  @GerritConfig(name = "plugin.delete-project.archiveDeletedRepos", value = "true")
  public void testArchiveProject() throws Exception {
    cmd = createCommand("delete", "--yes-really-delete", project.get());
    adminSshSession.exec(cmd);
    assertThat(adminSshSession.getError()).isNull();
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
}
