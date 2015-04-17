include_defs('//bucklets/gerrit_plugin.bucklet')

if STANDALONE_MODE:
  GSON = '//lib/gson:gson'
  LOG4J = '//lib/log:log4j'
else:
  GSON = '//plugins/delete-project/lib/gson:gson'
  LOG4J = '//plugins/delete-project/lib/log:log4j'

gerrit_plugin(
  name = 'delete-project',
  srcs = glob(['src/main/java/**/*.java']),
  resources = glob(['src/main/resources/**/*']),
  manifest_entries = [
    'Gerrit-PluginName: deleteproject',
    'Gerrit-Module: com.googlesource.gerrit.plugins.deleteproject.Module',
    'Gerrit-HttpModule: com.googlesource.gerrit.plugins.deleteproject.HttpModule',
    'Gerrit-SshModule: com.googlesource.gerrit.plugins.deleteproject.SshModule',
  ],
  provided_deps = [
    LOG4J,
    GSON,
  ],
)

java_library(
  name = 'classpath',
  deps = [':delete-project__plugin'],
)
