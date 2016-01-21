Build
=====

This plugin is built with Buck.

Two build modes are supported: Standalone and in Gerrit tree. Standalone
build mode is recommended, as this mode doesn't require local Gerrit
tree to exist.

Build standalone
----------------

Clone bucklets library:

```
  git clone https://gerrit.googlesource.com/bucklets

```
and link it to delete-project directory:

```
  cd delete-project && ln -s ../bucklets .
```

Add link to the .buckversion file:

```
  cd delete_project && ln -s bucklets/buckversion .buckversion
```

To build the plugin, issue the following command:


```
  buck build plugin
```

The output is created in

```
  buck-out/gen/delete-project/delete-project.jar
```

Build in Gerrit tree
--------------------

Clone or link this plugin to the plugins directory of Gerrit's source
tree, and issue the command:

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
