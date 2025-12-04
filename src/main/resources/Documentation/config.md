Configuration
=============

The configuration of the @PLUGIN@ plugin is done in the `gerrit.config`
file.

```
  [plugin "@PLUGIN@"]
    allowDeletionOfReposWithTags = true

```

plugin.@PLUGIN@.allowDeletionOfReposWithTags
:	Whether repositories that contain tags can be deleted.

	In some organizations repositories that contain tags must not be
	deleted due to legal reasons. This is the case when tags are used
	to mark releases which are shipped to customers and hence the
	source code must be kept to ensure build reproducibility of the
	releases.

	By default true.

plugin.@PLUGIN@.hideProjectOnPreserve
:	Whether projects should be hidden when the preserve option is used
	for the deletion.

	Hiding the project means that the project state is set to `HIDDEN`,
	all access rights are removed and the project is reparented to the
	project defined by [parentForDeletedProjects](#parentForDeletedProjects).
	This parent project is automatically created under the root project
	when it does not exist yet.

	By default false.

plugin.@PLUGIN@.parentForDeletedProjects
:	The name of the project that is used as parent for all deleted
	projects that were preserved by hiding them.

	This project is only used when [hideProjectOnPreserve](#hideProjectOnPreserve)
	is set to true.

	By default `Deleted-Projects`.

plugin.@PLUGIN@.protectedProject
:	The name of a project that is protected against deletion. May be an exact
	name or a regular expression.

	May be specified more than once to specify multiple project names or
	patterns.

	By default not set.


plugin.@PLUGIN@.archiveDeletedRepos
:	Whether to archive repositories instead of deleting them.

	Archiving the git repository means that the repository is stored
	in a folder which is not visible to the users which, from the user
	perspective, is equivalent to the repository being deleted. The
	advantage is, archived repositories can be later recovered if, needed,
	just by moving them back to their former name and location.
	The target folder used to archive the repositories can be set by
	[archiveFolder](#archiveFolder). Archived repositories are moved
	under the [archiveFolder](#archiveFolder) and renamed to add a
	timestamp and the %archived% suffix to the original name.
	Archived repositories are kept in the archive for a time period which
	can be set by [deleteArchivedReposAfter](#deleteArchivedReposAfter).

	If this option is enabled, the project will not be deleted but rather
	renamed and moved into the archive folder.

	If the repository has been archived for a time period longer than
	[deleteArchivedReposAfter](#deleteArchivedReposAfter), it will be
	deleted from the archive by a periodic task which runs once a day.

	By default false.

plugin.@PLUGIN@.archiveFolder
:	The absolute path of the archive folder to store archived repositories.

	The git repository is archived to this target folder only if
	[archiveDeletedRepos](#archiveDeletedRepos) is set to true.

	By default `$site_path/data/delete-project`.

plugin.@PLUGIN@.deleteArchivedReposAfter
:	The time duration for the git repository to be archived.

	The following suffixes are supported to define the time unit:\n
		1. d, day, days\n
		2. w, week, weeks (1 week is treated as 7 days)\n
		3. mon, month, months (1 month is treated as 30 days)\n
		4. y, year, years (1 year is treated as 365 days)\n

	If not specified, the default time unit is in days.

	The project git repository is archived to this target folder only if
	[archiveDeletedRepos](#archiveDeletedRepos) is set to true.

	If [archiveDeletedRepos](#archiveDeletedRepos) is set to true but this
	option is set to zero, the periodic task will not be executed and the
	archived repositories need to be deleted manually or using an external
	task.

	By default 180 (days).

plugin.@PLUGIN@.deleteTrashFoldersMaxAllowedTime
: Maximum duration to delete trash folders.

	Specifies the maximum duration that the plugin will spend
	scanning and deleting trash folders during a scheduled cleanup run.

	The following suffixes are supported to define the time unit:
		1. d, day, days
		2. w, week, weeks (1 week is treated as 7 days)
		3. mon, month, months (1 month is treated as 30 days)
		4. y, year, years (1 year is treated as 365 days)

	If the operation exceeds this duration, the process stops early to avoid long-running
	deletions that could overload the system or block other scheduled tasks.

	Subsequent runs handle any remaining trash folders.

	By default 10 minutes.

plugin.@PLUGIN@.trashFolderName
: Parent folder for all trash folders

  Specifies a common location for all trash folders. When defined, it can
  massively reduce I/O overhead by not scanning the whole git data folder
  but rather a much smaller subset.

  By default empty string

Delete & Archiving Trash Folder Scheduling
=============

Trash folder cleanup and repository archiving can be scheduled to run periodically.
If no schedule is configured, the operations run periodically, once every day.

The configuration has to be added to the `@PLUGIN@.config` file.

plugin.@PLUGIN@.cleanupStartTime
:	The start time for running trash folders deletion.

	The [start time](/Documentation/config-gerrit.html#schedule-configuration-startTime)
	for running trash folders deletion.

plugin.@PLUGIN@.cleanupInterval
:	The interval between successive trash folder deletions.

	The [interval](/Documentation/config-gerrit.html#schedule-configuration-interval)
	for running trash folders deletion.

plugin.@PLUGIN@.cleanupJitter
: A maximum random delay that will be added to the job’s scheduled start time.

	See the [jitter documentation](/Documentation/config-gerrit.html#schedule-configuration-jitter)
	for details.

[Schedule examples](/Documentation/config-gerrit.html#schedule-configuration-examples)
can be found in the [Schedule Configuration](/Documentation/config-gerrit.html#schedule-configuration)
section.
