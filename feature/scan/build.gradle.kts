plugins {
    alias(libs.plugins.medishelf.android.feature)
}

dependencies {
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.compose)
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.lifecycle)
    // Bundled (in-APK) models: recognition works offline and never downloads anything.
    implementation(libs.mlkit.barcode.scanning)
    implementation(libs.mlkit.text.recognition)
}

dependencies {
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.truth)
    androidTestImplementation(libs.kotlinx.coroutines.test)
}
