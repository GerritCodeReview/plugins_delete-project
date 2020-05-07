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

Gerrit.install(function(self) {
  if (window.Polymer) { return; }

  function onDeleteProject(c) {
    var f = c.checkbox();
    var b = c.button('Delete',
      {onclick: function(){
        c.call(
          {force: f.checked, preserve: false},
          function(r) {
            c.hide();
            window.alert('The project: "'
              + c.project
              + '" was deleted.'),
            Gerrit.go('/admin/projects/');
          });
      }});
    c.popup(c.div(
      c.msg('Are you really sure you want to delete the project: "'
        + c.project
        + '"?'),
      c.br(),
      c.label(f, 'Delete project even if open changes exist?'),
      c.br(),
      b));
  }
  self.onAction('project', 'delete', onDeleteProject);
});
