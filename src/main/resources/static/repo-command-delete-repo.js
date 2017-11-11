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
