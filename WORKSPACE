workspace(name = "delete_project")

load("//:bazlets.bzl", "load_bazlets")

load_bazlets(
    commit = "87908ae95402aa78dcb29075a7822509c9d04af6",
    #    local_path = "/home/<user>/projects/bazlets",
)

#Snapshot Plugin API
load(
    "@com_googlesource_gerrit_bazlets//:gerrit_api_maven_local.bzl",
    "gerrit_api_maven_local",
)

# Load snapshot Plugin API
gerrit_api_maven_local()

# Release Plugin API
#load(
#    "@com_googlesource_gerrit_bazlets//:gerrit_api.bzl",
#    "gerrit_api",
#)

# Load release Plugin API
#gerrit_api()
