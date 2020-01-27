Build
=====

This plugin is built with Bazel and two build modes are supported:

* Standalone
* In Gerrit tree.

Standalone build mode is recommended, as this mode doesn't require local Gerrit
tree to exist.

## Build standalone

To build the plugin, issue the following command:

```
  bazel build @PLUGIN@
```

The output is created in

```
  bazel-bin/@PLUGIN@.jar
```

To package the plugin sources run:

```
  bazel build lib@PLUGIN@__plugin-src.jar
```

The output is created in:

```
  bazel-bin/lib@PLUGIN@__plugin-src.jar
```

To execute the tests run:

```
  bazel test //...
```

This project can be imported into the Eclipse IDE. Execute:

```
  ./tools/eclipse/project.sh
```

to generate the required files and then import the project.


## Build in Gerrit tree

Clone or link this plugin to the plugins directory of Gerrit's source
tree, and issue the command:

```
  bazel build plugins/@PLUGIN@
```

The output is created in

```
  bazel-bin/plugins/@PLUGIN@/@PLUGIN@.jar
```

To execute the tests run:

```
  bazel test plugins/@PLUGIN@:@PLUGIN@_tests
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
