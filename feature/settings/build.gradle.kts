plugins {
    alias(libs.plugins.medishelf.android.feature)
}

dependencies {
    // Shows the same disclaimer text as first launch.
    implementation(projects.feature.onboarding)
}
