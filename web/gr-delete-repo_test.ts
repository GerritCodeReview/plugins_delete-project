/**
 * @license
 * Copyright (C) 2021 The Android Open Source Project
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
import './test/test-setup';
import './gr-delete-repo';
import {queryAndAssert} from './test/test-util';
import {PluginApi} from '@gerritcodereview/typescript-api/plugin';
import {GrDeleteRepo} from './gr-delete-repo';
import {
  ConfigInfo,
  HttpMethod,
  RepoName,
} from '@gerritcodereview/typescript-api/rest-api';
import {fixture, html, assert} from '@open-wc/testing';
import sinon from 'sinon';

suite('gr-delete-repo tests', () => {
  let element: GrDeleteRepo;
  let fetchStub: sinon.SinonStub;

  setup(async () => {
    fetchStub = sinon.stub();
    fetchStub.returns(Promise.resolve({}));

    element = await fixture(html`<gr-delete-repo></gr-delete-repo>`);
    element.repoName = 'test-repo-name' as RepoName;
    element.config = {
      actions: {
        'delete-project~delete': {
          label: 'test-action-label',
          title: 'test-aciton-title',
          enabled: true,
          method: HttpMethod.DELETE,
        },
      },
    } as unknown as ConfigInfo;
    element.plugin = {
      getPluginName: () => 'delete-project',
      restApi: () => {
        return {
          fetch: fetchStub,
          invalidateReposCache: () => {},
        };
      },
    } as unknown as PluginApi;
    await element.updateComplete;
  });

  test('confirm and send', () => {
    const dialog = queryAndAssert<HTMLElement>(element, '#deleteRepoDialog');
    dialog.dispatchEvent(new CustomEvent('confirm'));
    assert.isTrue(fetchStub.called);
    const method = fetchStub.firstCall.args[0] as HttpMethod;
    const endpoint = fetchStub.firstCall.args[1] as string;
    const json = fetchStub.firstCall.args[2];
    assert.equal(method, HttpMethod.DELETE);
    assert.equal(endpoint, '/projects/test-repo-name/delete-project~delete');
    assert.deepEqual(json, {force: false, preserve: false});
  });
});
