(function() {
  'use strict';

  Polymer({
    is: 'repo-command-delete-repo',

    attached() {
      this.hidden = this.config &&
        !this.config.actions['delete-project~delete'] ||
        !this.config ||
        this.projectName === 'All-Projects';
    },

    _handleCommandTap() {
      this.$.deleteRepoOverlay.open();
    },

    _handleCloseDeleteRepo() {
      this.$.deleteRepoOverlay.close();
    },

    _handleDeleteRepo() {
      if (!this.repoName) { return; }

      const forceDelete = this.$.forceDeleteOpenChangesCheckBox.checked;
      const keepRepo = this.$.preserveGitRepoCheckBox.checked;

      const encodeName = encodeURIComponent(this.repoName);
      const pluginRestApi = '/projects/' + encodeName +
          '/delete-project~delete';

      const json = {
        force: forceDelete,
        preserve: keepRepo
      };

      return Gerrit.post(pluginRestApi, json, projectDeleted => {
        this._handleCloseDeleteRepo();

        page.show('/admin/repos/');
      });
    },
  });
})();
