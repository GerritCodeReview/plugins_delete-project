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

const htmlTemplate = Polymer.html`
    <style include="gr-form-styles"></style>
    <gr-repo-command
        title="[[action.label]]"
        tooltip="[[action.title]]"
        disabled="[[!action.enabled]]"
        on-command-tap="_handleCommandTap">
    </gr-repo-command>
    <gr-overlay id="deleteRepoOverlay" with-backdrop>
      <gr-dialog
          id="deleteRepoDialog"
          confirm-label="Delete"
          on-confirm="_handleDeleteRepo"
          on-cancel="_handleCloseDeleteRepo">
        <div class="header" slot="header">
          Are you really sure you want to delete the repo: "[[repoName]]"?
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
