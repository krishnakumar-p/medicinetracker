plugins {
    alias(libs.plugins.medishelf.jvm.library)
}

dependencies {
    api(libs.kotlinx.coroutines.core)
    implementation(libs.javax.inject)
}
