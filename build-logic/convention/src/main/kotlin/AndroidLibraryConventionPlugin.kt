import com.android.build.api.dsl.LibraryExtension
import com.medishelf.buildlogic.addUnitTestDependencies
import com.medishelf.buildlogic.configureKotlinAndroid
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

class AndroidLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("com.android.library")

        extensions.configure<LibraryExtension> {
            configureKotlinAndroid(this)
            defaultConfig.testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
            testOptions.unitTests.isIncludeAndroidResources = true
            testOptions.animationsDisabled = true
            // Namespace derived from module path: :core:ui -> com.medishelf.core.ui
            namespace = "com.medishelf" + path.replace(':', '.')
        }
        addUnitTestDependencies()
    }
}
