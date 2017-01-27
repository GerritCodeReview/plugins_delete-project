Build
=====

This plugin is built with Bazel. Only the Gerrit in-tree build is
supported.

Clone or link this plugin to the plugins directory of Gerrit's source
tree, and issue the command:

```
  bazel build plugins/@PLUGIN@
```

The output is created in

```
  bazel-genfiles/plugins/@PLUGIN@/@PLUGIN@.jar
```

To execute the tests run:

```
  bazel test plugins/@PLUGIN@:delete_project_tests
```

or filtering using the comma separated tags:

````
  bazel test --test_tag_filters=@PLUGIN@ //...
````

This project can be imported into the Eclipse IDE.
Add the plugin name to the `CUSTOM_PLUGINS` set in
Gerrit core in `tools/bzl/plugins.bzl`, and execute:

```
  ./tools/eclipse/project.py
```
