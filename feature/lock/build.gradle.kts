plugins {
    alias(libs.plugins.medishelf.android.feature)
}

dependencies {
    // The lock feature owns biometric prompts and the lock state, so it talks to core:security directly.
    implementation(projects.core.security)
    implementation(libs.androidx.biometric)
    implementation(libs.androidx.activity.compose)
    testImplementation(projects.core.security)
}
