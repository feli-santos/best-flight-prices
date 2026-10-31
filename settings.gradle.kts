pluginManagement {
    repositories { gradlePluginPortal(); mavenCentral(); google() }
}
dependencyResolutionManagement {
    repositories { mavenCentral(); google() }
}
rootProject.name = "best-flight-prices"
include(":core", ":contracts", ":server", ":fake-provider", ":client", ":ui", ":desktopApp")
