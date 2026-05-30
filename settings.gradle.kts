pluginManagement {
  repositories {
    google {
      content {
        includeGroupByRegex("com\\.android.*")
        includeGroupByRegex("com\\.google.*")
        includeGroupByRegex("androidx.*")
      }
    }
    mavenCentral()
    gradlePluginPortal()
  }
}

plugins { id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0" }

dependencyResolutionManagement {
  repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
  repositories {
    google()
    maven { url = uri("https://jitpack.io") }
    mavenCentral()
    maven { url = uri("https://github.com/arthenica/ffmpeg-kit/releases/download/v6.0/") }
    maven { url = uri("https://raw.githubusercontent.com/arthenica/ffmpeg-kit-android-binary/main/releases/") }
  }
}

rootProject.name = "My Application"

include(":app")
