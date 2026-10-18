plugins {
    alias(libs.plugins.medishelf.android.library)
    alias(libs.plugins.medishelf.android.hilt)
    alias(libs.plugins.medishelf.android.room)
}

dependencies {
    implementation(projects.core.model)
    implementation(projects.core.common)
    api(projects.core.security)
    implementation(libs.sqlcipher.android)
    implementation(libs.androidx.sqlite.ktx)

    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
}

android {
    defaultConfig {
        consumerProguardFiles("proguard-consumer.pro")
    }
}

// Exported schemas feed MigrationTestHelper in the Robolectric tests.
androidComponents {
    onVariants { variant ->
        variant.hostTests.values.forEach { it.sources.assets?.addStaticSourceDirectory("$projectDir/schemas") }
    }
}
