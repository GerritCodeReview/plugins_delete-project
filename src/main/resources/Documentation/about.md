Provides the ability to delete a project.

Deleting a project means that the project is completely removed from
the Gerrit installation, including all its changes and optionally its
Git repository.

Limitations
-----------

There are a few caveats:

* This cannot be undone

	This is an irreversible action, and should be taken with extreme
	care. Backups are always advised of any important data.

* Project deletion does not replicate

	The delete project action does not replicate to Gerrit slaves.
	If deleting a project on the master, you must also delete it
	on the slaves.

* You cannot delete projects that use "submodule subscription"

	If deleting a project that makes use of submodule subscription,
	you cannot delete the project. Remove the submodule registration
	before attempting to delete the project.

Access
------

To be allowed to delete projects a user must be a member of a group
that is granted the 'Delete Project' capability (provided by this
plugin) or the 'Administrate Server' capability.

