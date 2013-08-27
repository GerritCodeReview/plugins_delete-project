// Copyright (C) 2013 The Android Open Source Project
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
    function onDeleteProject(c) {
      var t = c.checkbox();
      var b = c.button('Delete...',
        {onclick: function(){
          c.call(
            {preserve: t.checked},
            function(r) {
              c.hide();
              window.alert(r);
              Gerrit.go('/admin/projects/');
            });
        }});
      c.popup(c.div(
        c.msg('Are you really sure you want to delete the project: "'
          + c.project.name
          + '"?'),
        c.br(),
        c.label(t, 'Preserve GIT Repository?'),
        c.br(),
        b));
    }
    self.onAction('project', 'delete-project', onDeleteProject);
  });
