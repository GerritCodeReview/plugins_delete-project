Configuration
=============

The configuration of the @PLUGIN@ plugin is done in the `gerrit.config`
file.

```
  [plugin "@PLUGIN@"]
    allowDeletionOfReposWithTags = true
```

<a id="allowDeletionOfReposWithTags">
`plugin.@PLUGIN@.allowDeletionOfReposWithTags`
:	Whether repositories that contain tags can be deleted.

	In some organizations repositories that contain tags must not be
	deleted due to legal reasons. This is the case when tags are used
	to mark releases which are shipped to customers and hence the
	source code must be kept to ensure build reproducibility of the
	releases.

	By default true.

<a id="hideProjectOnPreserve">
`plugin.@PLUGIN@.hideProjectOnPreserve`
:	Whether projects should be hidden when the preserve option is used
	for the deletion.

	Hiding the project means that the project state is set to `HIDDEN`,
	all access rights are removed and the project is reparented to the
	project defined by [parentForDeletedProjects](#parentForDeletedProjects).
	This parent project is automatically created under the root project
	when it does not exist yet.

	By default false.

<a id="parentForDeletedProjects">
`plugin.@PLUGIN@.parentForDeletedProjects`
:	The name of the project that is used as parent for all deleted
	projects that were preserved by hiding them.

	This project is only used when [hideProjectOnPreserve](#hideProjectOnPreserve)
	is set to true.

	By default `Deleted-Projects`.

<a id="protectedProject">
`plugin.@PLUGIN@.protectedProject`
:	The name of a project that is protected against deletion. May be an exact
	name or a regular expression.

	May be specified more than once to specify multiple project names or
	patterns.

	By default not set.
