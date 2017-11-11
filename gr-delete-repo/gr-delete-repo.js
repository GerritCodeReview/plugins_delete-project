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
    is: 'gr-delete-repo',

    properties: {
      repoName: String,
      config: Object,
      deleteActionEndpoint: String,
    },

    attached() {
      this.deleteActionEndpoint = 'delete-project~delete';

      this.hidden = !(this.config &&
        this.config.actions[this.deleteActionEndpoint] &&
        this.config.actions[this.deleteActionEndpoint]['enabled']);
    },

    _handleCommandTap() {
      this.$.deleteRepoOverlay.open();
    },

    _handleCloseDeleteRepo() {
      this.$.deleteRepoOverlay.close();
    },

    _handleDeleteRepo() {
      var forceDelete = this.$.forceDeleteOpenChangesCheckBox.checked;
      var keepRepo = this.$.preserveGitRepoCheckBox.checked;

      var pluginRestApi = '/projects/' +
          encodeURIComponent(this.repoName) + '/' +
          this.deleteActionEndpoint;

      var json = {
        force: forceDelete,
        preserve: keepRepo
      };

      return this.plugin.restApi().post(pluginRestApi, json).then(projectDeleted => {
        Gerrit.Nav.navigateToRelativeUrl('/admin/repos');
      });
    },
  });
})();
