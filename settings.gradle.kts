pluginManagement {
    repositories {
        // Local offline mirror (populated by tools/fetch-deps.py). Kept first
        // so builds never need network. google()/mavenCentral() are fallbacks.
        maven { url = uri("file:///home/hatch/workspace/android-build-server/m2") }
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        maven { url = uri("file:///home/hatch/workspace/android-build-server/m2") }
        google()
        mavenCentral()
    }
}
rootProject.name = "ProVideoDownloader"
include(":app")
