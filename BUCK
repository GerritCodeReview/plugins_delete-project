gerrit_plugin(
  name = 'delete-project',
  srcs = glob(['src/main/java/**/*.java']),
  resources = glob(['src/main/resources/**/*']),
  manifest_entries = [
    'Gerrit-PluginName: deleteproject',
    'Gerrit-Module: com.googlesource.gerrit.plugins.deleteproject.Module',
    'Gerrit-HttpModule: com.googlesource.gerrit.plugins.deleteproject.HttpModule',
    'Gerrit-SshModule: com.googlesource.gerrit.plugins.deleteproject.SshModule',
  ]
)
