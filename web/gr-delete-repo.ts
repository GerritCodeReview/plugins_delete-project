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
import {
  ActionInfo,
  ConfigInfo,
  RepoName,
} from '@gerritcodereview/typescript-api/rest-api';
import {css, CSSResult, html, LitElement} from 'lit';
import {customElement, property, query, state} from 'lit/decorators.js';

declare global {
  interface HTMLElementTagNameMap {
    'gr-delete-repo': GrDeleteRepo;
  }
  interface Window {
    CANONICAL_PATH?: string;
  }
}

@customElement('gr-delete-repo')
export class GrDeleteRepo extends LitElement {
  @query('#deleteRepoModal')
  deleteRepoModal?: HTMLDialogElement;

  @query('#preserveGitRepoCheckBox')
  preserveGitRepoCheckBox?: HTMLInputElement;

  @query('#forceDeleteOpenChangesCheckBox')
  forceDeleteOpenChangesCheckBox?: HTMLInputElement;

  /** Guaranteed to be provided by the 'repo-command' endpoint. */
  @property({type: Object})
  plugin!: PluginApi;

  /** Guaranteed to be provided by the 'repo-command' endpoint. */
  @property({type: Object})
  config!: ConfigInfo;

  /** Guaranteed to be provided by the 'repo-command' endpoint. */
  @property({type: String})
  repoName!: RepoName;

  @state()
  private error?: string;

  static override get styles() {
    return [
      window.Gerrit?.styles.font as CSSResult,
      window.Gerrit?.styles.modal as CSSResult,
      css`
        :host {
          display: block;
          margin-bottom: var(--spacing-xxl);
        }
        /* TODO: Find a way to use shared styles in lit elements in plugins. */
        h2 {
          margin-top: var(--spacing-xxl);
          margin-bottom: var(--spacing-s);
        }
        .error {
          color: red;
        }
      `,
    ];
  }

  get action(): ActionInfo | undefined {
    return this.config?.actions?.[this.actionId];
  }

  get actionId(): string {
    return `${this.plugin.getPluginName()}~delete`;
  }

  private renderError() {
    if (!this.error) return;
    return html`<div class="error">${this.error}</div>`;
  }

  override render() {
    if (!this.action) return;
    return html`
      <h2 class="heading-2">${this.action.label}</h2>
      <gr-button
        title="${this.action.title}"
        ?disabled="${!this.action.enabled}"
        @click="${() => {
          this.error = undefined;
          this.deleteRepoModal?.showModal();
        }}"
      >
        ${this.action.label}
      </gr-button>
      ${this.renderError()}
      <dialog id="deleteRepoModal">
        <gr-dialog
          id="deleteRepoDialog"
          confirm-label="Delete"
          @confirm="${this.deleteRepo}"
          @cancel="${() => this.deleteRepoModal?.close()}"
        >
          <div class="header" slot="header">
            Are you really sure you want to delete the repo: "${this.repoName}"?
          </div>
          <div class="main" slot="main">
            <div>
              <div id="form">
                <section>
                  <input type="checkbox" id="forceDeleteOpenChangesCheckBox" />
                  <label for="forceDeleteOpenChangesCheckBox"
                    >Delete repo even if open changes exist?</label
                  >
                </section>
                <section>
                  <input type="checkbox" id="preserveGitRepoCheckBox" />
                  <label for="preserveGitRepoCheckBox"
                    >Preserve GIT Repository?</label
                  >
                </section>
              </div>
            </div>
          </div>
        </gr-dialog>
      </dialog>
    `;
  }

  private deleteRepo() {
    if (!this.action) {
      this.error = 'delete action undefined';
      this.deleteRepoModal?.close();
      return;
    }
    if (!this.action.method) {
      this.error = 'delete action does not have a HTTP method set';
      this.deleteRepoModal?.close();
      return;
    }
    this.error = undefined;

    const endpoint = `/projects/${encodeURIComponent(this.repoName)}/${
      this.actionId
    }`;
    const json = {
      force: this.forceDeleteOpenChangesCheckBox?.checked ?? false,
      preserve: this.preserveGitRepoCheckBox?.checked ?? false,
    };
    return this.plugin
      .restApi()
      .fetch(this.action.method, endpoint, json)
      .then(res => this.handleResponse(res))
      .catch(e => {
        this.handleError(e);
      });
  }

  private handleError(e: any) {
    if (typeof e === "undefined") {
      this.error = "Error deleting project";
    } else {
      this.error = e
    }
    this.deleteRepoModal?.close();
  }

  async handleResponse(response: Response | undefined) {
    if (response?.ok) {
      this.plugin.restApi().invalidateReposCache();
      this.deleteRepoModal?.close();
      window.location.href = `${this.getBaseUrl()}/admin/repos`;
    } else {
      this.handleError(undefined)
    }
  }

  private getBaseUrl() {
    return window.CANONICAL_PATH || '';
  }
}
