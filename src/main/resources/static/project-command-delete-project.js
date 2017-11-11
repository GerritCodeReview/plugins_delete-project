(function() {
  'use strict';

  Polymer({
    is: 'project-command-delete-project',

    attached() {
      console.log(this.projectName);
      console.log(this.config);
      this.hidden = this.projectName === 'All-Projects';
    },

    _handleCommandTap() {
      this.$.deleteProjectOverlay.open();
    },

    _handleDeleteProject() {
      this.$.createNewChangeModal.handleCreateChange();
      this._handleCloseDeleteProject();
    },

    _handleCloseDeleteProject() {
      this.$.deleteProjectOverlay.close();
    },

    handleDeleteProject() {
      const forceDelete = this.$.forceDeleteOpenChangesCheckBox.checked;
      const keepRepo = this.$.preserveGitRepoCheckBox.checked;
      return this.$.restAPI.createChange(this.projectName, forceDelete,
          keepRepo)
          .then(projectDeleted => {
            if (!projectDeleted) {
              return;
            }
            page.show('/admin/projects/');
          });
    },
  });
})();
