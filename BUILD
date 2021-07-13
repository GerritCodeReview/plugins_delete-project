load("@rules_java//java:defs.bzl", "java_library")
load("//tools/bzl:junit.bzl", "junit_tests")
load("//tools/js:eslint.bzl", "eslint")
load(
    "//tools/bzl:plugin.bzl",
    "PLUGIN_DEPS",
    "PLUGIN_TEST_DEPS",
    "gerrit_plugin",
)
load("//tools/bzl:js.bzl", "gerrit_js_bundle")
load("@npm//@bazel/typescript:index.bzl", "ts_project")

gerrit_plugin(
    name = "delete-project",
    srcs = glob(["src/main/java/**/*.java"]),
    manifest_entries = [
        "Gerrit-PluginName: delete-project",
        "Gerrit-Module: com.googlesource.gerrit.plugins.deleteproject.Module",
        "Gerrit-HttpModule: com.googlesource.gerrit.plugins.deleteproject.HttpModule",
        "Gerrit-SshModule: com.googlesource.gerrit.plugins.deleteproject.SshModule",
    ],
    resource_jars = [":gr-delete-repo"],
    resources = glob(["src/main/resources/Documentation/*.md"]),
    deps = ["@commons-io//jar"],
)

ts_project(
    name = "gr-delete-repo-ts",
    srcs = glob([
        "gr-delete-repo/*.ts",
    ]),
    tsc = "//tools/node_tools:tsc-bin",
    tsconfig = "//plugins:plugin-tsconfig",
    deps = [
        "@plugins_npm//@gerritcodereview/typescript-api",
        "@plugins_npm//@polymer/decorators",
        "@plugins_npm//@polymer/polymer",
    ],
)

gerrit_js_bundle(
    name = "gr-delete-repo",
    srcs = [":gr-delete-repo-ts"],
    entry_point = "gr-delete-repo/plugin.js",
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
        "@commons-io//jar",
        "@mockito//jar",
    ],
)

# Define the eslinter for the plugin
# The eslint macro creates 2 rules: lint_test and lint_bin
eslint(
    name = "lint",
    srcs = glob([
        "gr-delete-repo/**/*.js",
    ]),
    config = ".eslintrc.json",
    data = [],
    extensions = [
        ".js",
    ],
    ignore = ".eslintignore",
    plugins = [
        "@npm//eslint-config-google",
        "@npm//eslint-plugin-html",
        "@npm//eslint-plugin-import",
        "@npm//eslint-plugin-jsdoc",
    ],
)
