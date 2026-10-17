package com.medishelf.buildlogic

import com.android.build.api.dsl.CommonExtension
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

internal fun Project.configureAndroidCompose(commonExtension: CommonExtension) {
    pluginManager.apply("org.jetbrains.kotlin.plugin.compose")
    commonExtension.buildFeatures.compose = true

    dependencies {
        val bom = libs.lib("androidx-compose-bom")
        add("implementation", platform(bom))
        add("androidTestImplementation", platform(bom))
        add("testImplementation", platform(bom))
        add("implementation", libs.lib("androidx-compose-ui-tooling-preview"))
        add("debugImplementation", libs.lib("androidx-compose-ui-tooling"))
    }
}
