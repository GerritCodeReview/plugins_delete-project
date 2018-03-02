load("//tools/bzl:maven_jar.bzl", "maven_jar")

def external_plugin_deps():
  maven_jar(
    name = "mockito",
    artifact = "org.mockito:mockito-core:2.16.0",
    sha1 = "a022ee494c753789a1e7cae75099de81d8a5cea6",
    deps = [
      "@byte_buddy//jar",
      "@byte_buddy_agent//jar",
      "@objenesis//jar",
    ],
  )

  BYTE_BUDDY_VERSION = "1.7.9"

  maven_jar(
    name = "byte_buddy",
    artifact = "net.bytebuddy:byte-buddy:" + BYTE_BUDDY_VERSION,
    sha1 = "51218a01a882c04d0aba8c028179cce488bbcb58",
  )

  maven_jar(
    name = "byte_buddy_agent",
    artifact = "net.bytebuddy:byte-buddy-agent:" + BYTE_BUDDY_VERSION,
    sha1 = "a6c65f9da7f467ee1f02ff2841ffd3155aee2fc9",
  )

  maven_jar(
    name = "objenesis",
    artifact = "org.objenesis:objenesis:2.6",
    sha1 = "639033469776fd37c08358c6b92a4761feb2af4b",
  )
