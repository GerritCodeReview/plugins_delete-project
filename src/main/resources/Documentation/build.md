Build
=====

This plugin is built with Buck.
Clone or link this plugin to the plugins directory of Gerrit tree
and issue the command:

```
  buck build plugins/delete-project:delete-project
```

The output is created in

```
  buck-out/gen/plugins/delete-project/delete-project.jar
```

This project can be imported into the Eclipse IDE:

```
  ./tools/eclipse/project.py
```

Note for compatibility reasons Maven build is provided, but it considered to
be deprecated and is going to be removed in one of the future versions of this
plugin.

```
  mvn clean package
```

When building with Maven, the Gerrit Plugin API must be available.
How to build the Gerrit Plugin API is described in the [Gerrit
documentation](../../../Documentation/dev-buck.html#_extension_and_plugin_api_jar_files).
