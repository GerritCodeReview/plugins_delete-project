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

(function(window) {
  'use strict';

  var ELEMENT = '/static/project-command-delete-project.html';

  // PolyGerrit function

  /**
   * Create an empty DeleteProjectPlugin instance.
   *
   * @param {Plugin} gerritPlugin An object used to contain the plugin.
   *    Schema for Plugin: https://goo.gl/6nfYna.
   *
   * TODO: Conver to class (es6 syntax) once we drop support for GWTUI.
   *
   */
  function DeleteProjectPlugin(gerritPlugin) {
    this._gerritPlugin = gerritPlugin;
    this._element = null;
  }

  DeleteProjectPlugin.prototype.handleProject = function() {
    return this.handleProjectCommand();
  }

  /** @return {Promise} Resolves when the element is loaded. */
  DeleteProjectPlugin.prototype.loadElement = function() {
    return new Promise((resolve, reject) => {
      Polymer.Base.importHref(
          this._gerritPlugin.url(ELEMENT), resolve, reject);
    });
  }

  DeleteProjectPlugin.prototype.handleProjectCommand = function() {
    this.loadElement().catch((err) => {
      console.error('Could not load delete project element', err);
    });
  }

  window.Gerrit.install(function(self) {
      // GWTUI function
      function onDeleteProject(c) {
        var f = c.checkbox();
        var p = c.checkbox();
        var b = c.button('Delete',
          {onclick: function(){
            c.call(
              {force: f.checked, preserve: p.checked},
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
          c.label(p, 'Preserve GIT Repository?'),
          c.br(),
          b));
      }

      if (window.Polymer) {
        // Low-level API
        var plugin = new DeleteProjectPlugin(self);
        plugin.handleProject();
        self.registerCustomComponent(
            'project-command', 'project-command-delete-project');
      } else {
        self.onAction('project', 'delete', onDeleteProject);
      }
  });

  if (window.Polymer) {
    window.__DeleteProjectPlugin = window.__DeleteProjectPlugin || DeleteProjectPlugin;
  }
})(window);
