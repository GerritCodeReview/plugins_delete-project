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
import 'chai/chai';
import '@gerritcodereview/typescript-api/gerrit';
import {css} from 'lit';

declare global {
  interface Window {
    assert: typeof chai.assert;
    expect: typeof chai.expect;
    sinon: typeof sinon;
  }
  let assert: typeof chai.assert;
  let expect: typeof chai.expect;
  let sinon: typeof sinon;
}
window.assert = chai.assert;
window.expect = chai.expect;
window.sinon = sinon;

window.Gerrit = {
  install: () => {},
  styles: {
    form: css``,
    menuPage: css``,
    subPage: css``,
    table: css``,
  },
};
