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
import {htmlTemplate} from './gr-delete-repo_html';
import {PluginApi} from '@gerritcodereview/typescript-api/plugin';
import {ActionInfo, ConfigInfo, HttpMethod, RepoName} from '@gerritcodereview/typescript-api/rest-api';
import {ErrorCallback} from '@gerritcodereview/typescript-api/rest';
import {property} from '@polymer/decorators';
import {PolymerElement} from '@polymer/polymer/polymer-element';

// TODO: This should be defined and exposed by @gerritcodereview/typescript-api
type GrOverlay = Element & {
  open(): void,
  close(): void,
}

// TODO: Convert this.$ usages to querySelector() calls.
interface GrDeleteRepo {
  $: {
    deleteRepoOverlay: GrOverlay;
    preserveGitRepoCheckBox: HTMLInputElement;
    forceDeleteOpenChangesCheckBox: HTMLInputElement;
  };
}

// TODO: Convert to LitElement.
class GrDeleteRepo extends PolymerElement {
  static get template() {
    return htmlTemplate;
  }

  @property({type: String})
  actionId = '';

  @property({type: Object})
  action?: ActionInfo;

  @property({type: Boolean})
  hidden = false;

  @property({type: Object})
  plugin!: PluginApi;

  @property({type: Object})
  config!: ConfigInfo;

  @property({type: String})
  repoName!: RepoName;

  connectedCallback() {
    super.connectedCallback();
    this.actionId = this.plugin.getPluginName() + '~delete';
    this.action = this.config.actions?.[this.actionId];
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

    const errFn: ErrorCallback = (response?: Response | null) => {
      this.dispatchEvent(new CustomEvent('page-error', {
        detail: {response},
        bubbles: true,
        composed: true,
      }));
    };

    return this.plugin.restApi().send(
        this.action?.method ?? HttpMethod.GET, endpoint, json, errFn)
        .then(_ => {
          this.plugin.restApi().invalidateReposCache();
          window.location.href = '/admin/repos';
        });
  }
}

customElements.define('gr-delete-repo', GrDeleteRepo);
