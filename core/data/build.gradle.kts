plugins {
    alias(libs.plugins.medishelf.android.library)
    alias(libs.plugins.medishelf.android.hilt)
}

dependencies {
    api(projects.core.domain)
    implementation(projects.core.common)
    implementation(projects.core.database)
    implementation(projects.core.datastore)
    implementation(projects.core.security)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.datastore)

    testImplementation(projects.core.testing)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.room.runtime)
}
