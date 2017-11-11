(function() {
  'use strict';

  Polymer({
    is: 'project-command-delete-project',

    attached() {
      this.$.restAPI.getProjectConfig(this.projectName).then(
          config => {
            this.hidden = config &&
              !config.actions['delete-project~delete'] ||
              !config ||
              this.projectName === 'All-Projects';
          }).catch(() => {
            this.hidden = this.config &&
              !this.config.actions['delete-project~delete'] ||
              !this.config ||
              this.projectName === 'All-Projects';
          });
    },

    _handleCommandTap() {
      this.$.deleteProjectOverlay.open();
    },

    _handleCloseDeleteProject() {
      this.$.deleteProjectOverlay.close();
    },

    _handleDeleteProject() {
      if (!this.projectName) { return; }

      const forceDelete = this.$.forceDeleteOpenChangesCheckBox.checked;
      const keepRepo = this.$.preserveGitRepoCheckBox.checked;

      const encodeName = encodeURIComponent(this.projectName);
      const pluginRestApi = '/projects/' + encodeName +
          '/delete-project~delete';

      const json = {
        force: forceDelete,
        preserve: keepRepo
      };

      return Gerrit.post(pluginRestApi, json, projectDeleted => {
        this._handleCloseDeleteProject();

        page.show('/admin/projects/');
      });
    },
  });
})();
