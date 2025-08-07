# Delete project plugin for Gerrit Code Review

A plugin which allows projects to be deleted from Gerrit via an SSH command,
REST API or the Project settings screen.

[![Build Status](https://gerrit-ci.gerritforge.com/view/Plugins-master/job/plugin-delete-project-bazel-master/badge/icon
)](https://gerrit-ci.gerritforge.com/view/Plugins-master/job/plugin-delete-project-bazel-master/)

## JavaScript Plugin Development

For running unit tests execute:

    bazel test //plugins/delete-project/web:web_test_runner

For checking or fixing eslint formatter problems run:

    bazel test //plugins/delete-project/web:lint_test
    bazel run //plugins/delete-project/web:lint_bin -- --fix "$(pwd)/plugins/delete-project/web"

For testing the plugin with
[Gerrit FE Dev Helper](https://gerrit.googlesource.com/gerrit-fe-dev-helper/)
build the JavaScript bundle and copy it to the `plugins/` folder:

    bazel build //plugins/delete-project/web:gr-delete-repo
    cp -f bazel-bin/plugins/delete-project/web/gr-delete-repo.js plugins/

and let the Dev Helper redirect from `.+/plugins/delete-project/static/gr-delete-repo.js` to
`http://localhost:8081/plugins_/gr-delete-repo.js`.
