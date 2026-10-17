package com.medishelf.buildlogic

import com.android.build.api.dsl.CommonExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile

internal fun Project.configureKotlinAndroid(commonExtension: CommonExtension) {
    commonExtension.apply {
        compileSdk = libs.intVersion("compileSdk")
        defaultConfig.minSdk = libs.intVersion("minSdk")
        compileOptions.sourceCompatibility = JavaVersion.VERSION_17
        compileOptions.targetCompatibility = JavaVersion.VERSION_17
    }
    configureKotlin()
}

internal fun Project.configureKotlinJvm() {
    extensions.configure<JavaPluginExtension> {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    configureKotlin()
}

private fun Project.configureKotlin() {
    tasks.withType<KotlinJvmCompile>().configureEach {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
            allWarningsAsErrors.set(providers.gradleProperty("warningsAsErrors").map(String::toBoolean).orElse(false))
            freeCompilerArgs.add("-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi")
        }
    }
}

internal fun Project.addUnitTestDependencies() {
    // Skeleton modules have Hilt-generated test sources but no tests yet.
    tasks.withType<Test>().configureEach { failOnNoDiscoveredTests.set(false) }
    dependencies {
        add("testImplementation", libs.lib("junit"))
        add("testImplementation", libs.lib("truth"))
        add("testImplementation", libs.lib("turbine"))
        add("testImplementation", libs.lib("kotlinx-coroutines-test"))
    }
}
