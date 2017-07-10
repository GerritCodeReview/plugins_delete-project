load("@com_googlesource_gerrit_bazlets//tools:classpath.bzl", "classpath_collector")

java_library(
    name = "classpath",
    testonly = 1,
    runtime_deps = [
        "//:delete-project__plugin_test_deps",
    ],
)

classpath_collector(
    name = "main_classpath_collect",
    testonly = 1,
    deps = [
        "//:delete-project__plugin_test_deps",
    ],
)
