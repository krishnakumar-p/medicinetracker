plugins {
    alias(libs.plugins.medishelf.android.library)
    alias(libs.plugins.medishelf.android.hilt)
}

dependencies {
    implementation(projects.core.common)
    implementation(projects.core.domain)
    implementation(projects.core.datastore)
    implementation(libs.androidx.biometric)
    implementation(libs.androidx.datastore)
    implementation(libs.androidx.lifecycle.process)

    testImplementation(projects.core.testing)
    testImplementation(libs.androidx.datastore)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
}
