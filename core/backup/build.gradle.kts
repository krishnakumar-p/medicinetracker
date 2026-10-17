plugins {
    alias(libs.plugins.medishelf.android.library)
    alias(libs.plugins.medishelf.android.hilt)
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    implementation(projects.core.domain)
    implementation(projects.core.common)
    implementation(projects.core.database)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.zip4j)
    implementation(libs.androidx.room.runtime)

    testImplementation(projects.core.testing)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
}
