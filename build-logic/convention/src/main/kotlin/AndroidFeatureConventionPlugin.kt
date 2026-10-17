import com.medishelf.buildlogic.lib
import com.medishelf.buildlogic.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

class AndroidFeatureConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("medishelf.android.library")
        pluginManager.apply("medishelf.android.library.compose")
        pluginManager.apply("medishelf.android.hilt")
        pluginManager.apply("org.jetbrains.kotlin.plugin.serialization")
        pluginManager.apply("org.jetbrains.kotlin.plugin.parcelize")

        dependencies {
            add("implementation", project(":core:model"))
            add("implementation", project(":core:domain"))
            add("implementation", project(":core:designsystem"))
            add("implementation", project(":core:ui"))

            add("implementation", libs.lib("androidx-hilt-lifecycle-viewmodel-compose"))
            add("implementation", libs.lib("androidx-lifecycle-runtime-compose"))
            add("implementation", libs.lib("androidx-lifecycle-viewmodel-compose"))
            add("implementation", libs.lib("androidx-navigation-compose"))
            add("implementation", libs.lib("kotlinx-serialization-json"))

            add("testImplementation", project(":core:testing"))
            add("testImplementation", libs.lib("robolectric"))
            add("testImplementation", libs.lib("androidx-navigation-testing"))
            add("testImplementation", libs.lib("androidx-compose-ui-test-junit4"))
            add("testImplementation", libs.lib("androidx-compose-ui-test-junit4-accessibility"))
            add("debugImplementation", libs.lib("androidx-compose-ui-test-manifest"))
        }
    }
}
