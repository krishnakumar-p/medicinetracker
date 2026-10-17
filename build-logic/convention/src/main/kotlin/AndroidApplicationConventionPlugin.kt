import com.android.build.api.dsl.ApplicationExtension
import com.medishelf.buildlogic.addUnitTestDependencies
import com.medishelf.buildlogic.configureKotlinAndroid
import com.medishelf.buildlogic.intVersion
import com.medishelf.buildlogic.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

class AndroidApplicationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("com.android.application")

        extensions.configure<ApplicationExtension> {
            configureKotlinAndroid(this)
            defaultConfig.targetSdk = libs.intVersion("targetSdk")
            defaultConfig.testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
            testOptions.unitTests.isIncludeAndroidResources = true
            testOptions.animationsDisabled = true
        }
        addUnitTestDependencies()
    }
}
