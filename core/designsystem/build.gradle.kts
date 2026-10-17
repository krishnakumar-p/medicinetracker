plugins {
    alias(libs.plugins.medishelf.android.library)
    alias(libs.plugins.medishelf.android.libraryCompose)
}

dependencies {
    api(libs.androidx.compose.foundation)
    api(libs.androidx.compose.material3)
    api(libs.androidx.compose.material.icons.core)
    api(libs.androidx.compose.ui)
}
