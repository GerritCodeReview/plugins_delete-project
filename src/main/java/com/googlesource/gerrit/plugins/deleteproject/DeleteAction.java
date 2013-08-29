package com.googlesource.gerrit.plugins.deleteproject;

import com.google.gerrit.extensions.webui.UiAction;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.config.AllProjectsNameProvider;
import com.google.gerrit.server.project.ProjectResource;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.googlesource.gerrit.plugins.deleteproject.cache.CacheDeleteHandler;
import com.googlesource.gerrit.plugins.deleteproject.database.DatabaseDeleteHandler;
import com.googlesource.gerrit.plugins.deleteproject.fs.FilesystemDeleteHandler;

public class DeleteAction extends DeleteProject implements
    UiAction<ProjectResource> {
  private final Provider<CurrentUser> currentUser;

  @Inject
  DeleteAction(Provider<CurrentUser> currentUser,
      AllProjectsNameProvider allProjectsNameProvider,
      DatabaseDeleteHandler dbHandler,
      FilesystemDeleteHandler fsHandler,
      CacheDeleteHandler cacheHandler) {
    super(allProjectsNameProvider, dbHandler, fsHandler, cacheHandler);
    this.currentUser = currentUser;
  }

  @Override
  public UiAction.Description getDescription(ProjectResource rsrc) {
    return new UiAction.Description()
        .setLabel("Delete Project")
        .setTitle(isAllProjects(rsrc)
            ? String.format("No deletion of %s project",
                allProjectsName)
            : String.format("Deleting project %s", rsrc.getName()))
        .setEnabled(!isAllProjects(rsrc))
        .setVisible(currentUser.get() instanceof IdentifiedUser);
  }
}
