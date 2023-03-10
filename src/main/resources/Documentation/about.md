Provides the ability to delete a project.

Deleting a project means that the project is completely removed from
the Gerrit installation, including all its changes and optionally its
Git repository.

When a project is fully deleted, a project deletion event is fired.
Other plugins can listen to this event by implementing
`com.google.gerrit.extensions.events.ProjectDeletedListener` which is
part of the Gerrit core extension API. The project deletion event is
only fired if the Git repository of the project is deleted.

Limitations
-----------

There are a few caveats:

* This cannot be undone

	This is an irreversible action, and should be taken with extreme
	care. Backups are always advised of any important data.

* You cannot delete projects that use "submodule subscription"

	If deleting a project that makes use of submodule subscription,
	you cannot delete the project. Remove the submodule registration
	before attempting to delete the project.

Replication of project deletions
--------------------------------

This plugin does not replicate any project deletions, but it triggers
an event when a project is deleted. The [replication plugin]
(https://gerrit-review.googlesource.com/#/admin/projects/plugins/replication)
can be configured to listen to the project deletion event and to
replicate project deletions.

Event after project deletion
-----------------------------------

This plugin generates an event after project deletion. Format of
the event:

=== Project Deleted

Sent after project deletion.

type:: "project-deleted"

projectName:: Name of the deleted project

eventCreatedOn:: Time in seconds since the UNIX epoch when this event was
created.

*NOTE*: This event will be delivered only to the unrestricted listeners.
Unrestricted events listeners implement
`com.google.gerrit.server.events.EventListener` without performing any
permission checking.

Access
------

To be allowed to delete arbitrary projects a user must be a member of a
group that is granted the 'Delete Project' capability (provided by this
plugin) or the 'Administrate Server' capability. Project owners are
allowed to delete their own projects if they are member of a group that
is granted the 'Delete Own Project' capability (provided by this
plugin).

Note
------
When new project is created, user has time duration to delete the project
by triggering REST or SSH call or using UI menu, if the project does not
have created changes. It's introduced to give user permissions to delete
wrongly created project himself/herself. Time duration value is set up
in gerrit.config. It's described in configuration section
(plugin.@PLUGIN@.deleteProjectTimeDuration). Time duration value is
12 hours by default.

Please take into account two preconditions to enable current functionality:
1) To be allowed to delete project according to creation time a user must be
a member of a group, that is granted the 'Delete Project by Creation Time'
capability (provided by this plugin) or the 'Administrate Server' capability.
2) User should be the owner of created project, otherwise the option is
not allowed.
