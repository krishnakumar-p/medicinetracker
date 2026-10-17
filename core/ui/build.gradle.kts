plugins {
    alias(libs.plugins.medishelf.android.library)
    alias(libs.plugins.medishelf.android.libraryCompose)
}

dependencies {
    api(projects.core.designsystem)
    implementation(projects.core.model)
    implementation(libs.androidx.lifecycle.viewmodel.savedstate)
    implementation(libs.kotlinx.coroutines.core)
}
