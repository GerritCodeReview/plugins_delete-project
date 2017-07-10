load("//tools/bzl:plugin.bzl", "PLUGIN_DEPS", "PLUGIN_TEST_DEPS")
load("//tools/bzl:classpath.bzl", "classpath_collector")

java_library(
    name = "classpath",
    runtime_deps = PLUGIN_DEPS + PLUGIN_TEST_DEPS + [
        "//:delete-project__plugin",
    ],
)

classpath_collector(
    name = "main_classpath_collect",
    deps = PLUGIN_DEPS + PLUGIN_TEST_DEPS + [
        "//:delete-project__plugin",
    ],
)
