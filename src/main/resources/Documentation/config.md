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
	`Deleted-Projects` project. The `Deleted-Projects` project is
	automatically created under the root project when it does not exist
	yet.
	By default false.
