/**
 * @license
 * Copyright (C) 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import {htmlTemplate} from './gr-delete-repo_html.js';

class GrDeleteRepo extends Polymer.Element {
  /** @returns {string} name of the component */
  static get is() { return 'gr-delete-repo'; }

  /** @returns {?} template for this component */
  static get template() { return htmlTemplate; }

  /**
   * Defines properties of the component
   *
   * @returns {?}
   */
  static get properties() {
    return {
      repoName: String,
      config: Object,
      action: Object,
      actionId: String,
    };
  }

  connectedCallback() {
    super.connectedCallback();
    this.actionId = this.plugin.getPluginName() + '~delete';
    this.action = this.config.actions[this.actionId];
    this.hidden = !this.action;
  }

  _handleCommandTap() {
    this.$.deleteRepoOverlay.open();
  }

  _handleCloseDeleteRepo() {
    this.$.deleteRepoOverlay.close();
  }

  _handleDeleteRepo() {
    const endpoint = '/projects/' +
        encodeURIComponent(this.repoName) + '/' +
        this.actionId;

    const json = {
      force: this.$.forceDeleteOpenChangesCheckBox.checked,
      preserve: this.$.preserveGitRepoCheckBox.checked,
    };

    const errFn = response => {
      this.dispatchEvent(new CustomEvent('page-error', {
        detail: {response},
        bubbles: true,
        composed: true,
      }));
    };

    return this.plugin.restApi().send(
        this.action.method, endpoint, json, errFn)
        .then(r => {
          this.plugin.restApi().invalidateReposCache();
          Gerrit.Nav.navigateToRelativeUrl('/admin/repos');
        });
  }
}

customElements.define(GrDeleteRepo.is, GrDeleteRepo);
