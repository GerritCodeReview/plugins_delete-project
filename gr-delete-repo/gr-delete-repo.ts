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
import {PluginApi} from '@gerritcodereview/typescript-api/plugin';
import {ActionInfo, ConfigInfo, HttpMethod, RepoName} from '@gerritcodereview/typescript-api/rest-api';
import {css, html, LitElement} from 'lit';
import {customElement, property, query} from 'lit/decorators.js';

// TODO: This should be defined and exposed by @gerritcodereview/typescript-api
type GrOverlay = Element & {
  open(): void,
  close(): void,
}

// TODO: This should be defined and exposed by @gerritcodereview/typescript-api
function sharedStyles() {
  const template = customElements.get('dom-module').import('shared-styles', 'template');
  return template.content.querySelector('style');
}

@customElement('gr-delete-repo')
class GrDeleteRepo extends LitElement {
  @query('#deleteRepoOverlay')
  deleteRepoOverlay?: GrOverlay;

  @query('#preserveGitRepoCheckBox')
  preserveGitRepoCheckBox: HTMLInputElement;

  @query('#forceDeleteOpenChangesCheckBox')
  forceDeleteOpenChangesCheckBox: HTMLInputElement;

  /** Guaranteed to be provided by the 'repo-command' endpoint. */
  @property({type: Object})
  plugin!: PluginApi;

  /** Guaranteed to be provided by the 'repo-command' endpoint. */
  @property({type: Object})
  config!: ConfigInfo;

  /** Guaranteed to be provided by the 'repo-command' endpoint. */
  @property({type: String})
  repoName!: RepoName;

  /** Internal property only. */
  @property({type: String})
  error?: string;

  static styles = css`
    :host {
      display: block;
      margin-bottom: var(--spacing-xxl);
    }
    .error {
      color: red;
    }
  `;

  get action(): ActionInfo {
    return this.config.actions?.[this.actionId];
  }

  get actionId(): string {
    return this.plugin.getPluginName() + '~delete';
  }

  renderError() {
    if (!this.error) return;
    return html`<div class="error">${this.error}</div>`;
  }

  render() {
    if (!this.action) {
      this.error = `Error: No action for '${this.actionId}'.`;
      return this.renderError();
    }
    return html`
      ${sharedStyles()}
      <h3>${this.action.label}</h3>
      <gr-button
        title="${this.action.title}"
        ?disabled="${!this.action.enabled}"
        @click="${() => {
          this.error = undefined;
          this.deleteRepoOverlay.open()
        }}"
      >
        ${this.action.label}
      </gr-button>
      ${this.renderError()}
      <gr-overlay id="deleteRepoOverlay" with-backdrop>
        <gr-dialog
            id="deleteRepoDialog"
            confirm-label="Delete"
            @confirm="${this.deleteRepo}"
            @cancel="${() => this.deleteRepoOverlay.close()}">
          <div class="header" slot="header">
            Are you really sure you want to delete the repo: "${this.repoName}"?
          </div>
          <div class="main" slot="main">
            <div class="gr-form-styles">
              <div id="form">
                  <section>
                    <input
                        type="checkbox"
                        id="forceDeleteOpenChangesCheckBox">
                    <label for="forceDeleteOpenChangesCheckBox">Delete repo even if open changes exist?</label>
                  </section>
                  <section>
                    <input
                        type="checkbox"
                        id="preserveGitRepoCheckBox">
                    <label for="preserveGitRepoCheckBox">Preserve GIT Repository?</label>
                  </section>
              </div>
            </div>
          </div>
        </gr-dialog>
      </gr-overlay>
    `;
  }

  private deleteRepo() {
    if (!this.action) {
      this.error = 'delete action undefined';
      this.deleteRepoOverlay.close();
      return;
    }
    if (!this.action.method) {
      this.error = 'delete action does not have a HTTP method set';
      this.deleteRepoOverlay.close();
      return;
    }
    this.error = undefined;

    const endpoint = `/projects/${encodeURIComponent(this.repoName)}/${this.actionId}`;
    const json = {
      force: this.forceDeleteOpenChangesCheckBox.checked,
      preserve: this.preserveGitRepoCheckBox.checked,
    };
    return this.plugin.restApi().send(this.action.method, endpoint, json)
        .then(_ => {
          this.plugin.restApi().invalidateReposCache();
          this.deleteRepoOverlay.close();
          window.location.href = '/admin/repos';
        });
  }
}
