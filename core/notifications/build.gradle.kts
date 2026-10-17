plugins {
    alias(libs.plugins.medishelf.android.library)
    alias(libs.plugins.medishelf.android.hilt)
}

dependencies {
    implementation(projects.core.model)
    implementation(projects.core.domain)
    implementation(libs.androidx.core.ktx)

    testImplementation(projects.core.testing)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
}
