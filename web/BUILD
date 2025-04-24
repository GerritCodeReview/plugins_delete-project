load("//tools/js:eslint.bzl", "plugin_eslint")
load("//tools/bzl:js.bzl", "gerrit_js_bundle", "karma_test")
load("@npm//@bazel/typescript:index.bzl", "ts_config", "ts_project")

package_group(
    name = "visibility",
    packages = ["//plugins/delete-project/..."],
)

package(default_visibility = [":visibility"])

ts_config(
    name = "tsconfig",
    src = "tsconfig.json",
    deps = [
        "//plugins:tsconfig-plugins-base.json",
    ],
)

ts_project(
    name = "delete-project-ts",
    srcs = glob(
        ["**/*.ts"],
        exclude = ["**/*test*"],
    ),
    incremental = True,
    out_dir = "_bazel_ts_out",
    tsc = "//tools/node_tools:tsc-bin",
    tsconfig = ":tsconfig",
    deps = [
        "@plugins_npm//@gerritcodereview/typescript-api",
        "@plugins_npm//lit",
    ],
)

ts_project(
    name = "delete-project-ts-tests",
    srcs = glob(["**/*.ts"]),
    incremental = True,
    out_dir = "_bazel_ts_out_tests",
    tsc = "//tools/node_tools:tsc-bin",
    tsconfig = ":tsconfig",
    deps = [
        "@plugins_npm//:node_modules",
        "@ui_dev_npm//:node_modules",
    ],
)

gerrit_js_bundle(
    name = "gr-delete-repo",
    srcs = [":delete-project-ts"],
    entry_point = "_bazel_ts_out/plugin.js",
)

web_test_runner(
    name = "web_test_runner",
    srcs = ["web_test_runner.sh"],
    data = [
        ":tsconfig",
        ":delete-project-ts-tests",
        "@plugins_npm//:node_modules",
    ],
)

plugin_eslint()
