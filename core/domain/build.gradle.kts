plugins {
    alias(libs.plugins.medishelf.jvm.library)
}

dependencies {
    api(projects.core.model)
    api(projects.core.common)
    implementation(libs.javax.inject)

    testImplementation(projects.core.testing)
}
