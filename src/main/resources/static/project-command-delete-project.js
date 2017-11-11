(function() {
  'use strict';

  Polymer({
    is: 'project-command-delete-project',

    attached() {
      console.log(this.projectName);
      console.log(this.config);
      this.hidden = this.projectName !== 'All-Projects';
    },

    _handleCommandTap() {
      alert('(softly) bork, bork.');
    },
  });
})();
