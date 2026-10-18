pluginManagement {
    includeBuild("build-logic")
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

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

rootProject.name = "MediShelf"

include(":app")

include(":core:model")
include(":core:common")
include(":core:domain")
include(":core:nlp")
include(":core:database")
include(":core:datastore")
include(":core:security")
include(":core:data")
include(":core:backup")
include(":core:notifications")
include(":core:work")
include(":core:designsystem")
include(":core:ui")
include(":core:testing")

include(":feature:onboarding")
include(":feature:lock")
include(":feature:dashboard")
include(":feature:medicines")
include(":feature:doses")
include(":feature:search")
include(":feature:scan")
include(":feature:locations")
include(":feature:family")
include(":feature:settings")
