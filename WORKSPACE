workspace(name = "delete_project")
load("//:bazlets.bzl", "load_bazlets")

load_bazlets(
    commit = "28aa2290c7f7742261d69b358f3de30d2e87c13b",
)

#Snapshot Plugin API
#load(
#    "@com_googlesource_gerrit_bazlets//:gerrit_api_maven_local.bzl",
#    "gerrit_api_maven_local",
#)

# Load snapshot Plugin API
#gerrit_api_maven_local()

# Release Plugin API
load(
   "@com_googlesource_gerrit_bazlets//:gerrit_api.bzl",
   "gerrit_api",
)

# Load release Plugin API
gerrit_api()
