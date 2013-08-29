@PLUGIN@ delete
===============

NAME
----
@PLUGIN@ delete - Completely delete a project and all its changes

SYNOPSIS
--------
```
ssh -p @SSH_PORT@ @SSH_HOST@ @PLUGIN@ delete
  [--yes-really-delete]
  [--force]
  [--preserve-git-repository]
  <PROJECT>
```

DESCRIPTION
-----------
Deletes a project from the Gerrit installation, removing the Git
repository along with any changes associated with it.

There are a few caveats:

* This cannot be undone

	This is an irreversible action, and should be taken with extreme
	care. Backups are always advised of any important data.

* Project deletion does not replicate

	The delete project action does not replicate to Gerrit slaves.
	If deleting a project on the master, you must also delete it
	on the slave.

* You cannot delete projects that use "submodule subscription"

	If deleting a project that makes use of submodule subscription,
	you cannot delete the project. Remove the submodule registration
	before attempting to delete the project.

ACCESS
------
Caller must be a member of a group that is granted the 'Delete Project'
capability (provided by this plugin) or be a member of the Administrators
group.


SCRIPTING
---------
This command is intended to be used in scripts.

OPTIONS
-------

`--yes-really-delete`
:	Actually perform the deletion. If omitted, the command
	will just output information about the deletion and then
	exit. 

`--force`
:	Delete project even if it has open changes.

`--preserve-git-repository`
:	Don't delete git repository directory.


EXAMPLES
--------
See if you can delete a project:

```
  $ ssh -p @SSH_PORT@ @SSH_HOST@ @PLUGIN@ delete tools/gerrit
```

Completely delete a project:

```
  $ ssh -p @SSH_PORT@ @SSH_HOST@ @PLUGIN@ delete --yes-really-delete tools/gerrit
```


SEE ALSO
--------

* [Access Control](../../../Documentation/access-control.html)
