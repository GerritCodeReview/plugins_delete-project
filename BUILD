load("@rules_java//java:defs.bzl", "java_library")
load("//tools/bzl:junit.bzl", "junit_tests")
load(
    "//tools/bzl:plugin.bzl",
    "PLUGIN_DEPS",
    "PLUGIN_TEST_DEPS",
    "gerrit_plugin",
)

gerrit_plugin(
    name = "delete-project",
    srcs = glob(["src/main/java/**/*.java"]),
    manifest_entries = [
        "Gerrit-PluginName: delete-project",
        "Gerrit-Module: com.googlesource.gerrit.plugins.deleteproject.PluginModule",
        "Gerrit-HttpModule: com.googlesource.gerrit.plugins.deleteproject.HttpModule",
        "Gerrit-SshModule: com.googlesource.gerrit.plugins.deleteproject.SshModule",
    ],
    resource_jars = ["//plugins/delete-project/web:gr-delete-repo"],
    resources = glob(["src/main/resources/Documentation/*.md"]),
)

junit_tests(
    name = "delete-project_tests",
    srcs = glob(["src/test/java/**/*.java"]),
    tags = ["delete-project"],
    deps = [
        ":delete-project__plugin_test_deps",
    ],
)

java_library(
    name = "delete-project__plugin_test_deps",
    testonly = 1,
    visibility = ["//visibility:public"],
    exports = PLUGIN_DEPS + PLUGIN_TEST_DEPS + [
        ":delete-project__plugin",
        "@mockito//jar",
    ],
)
