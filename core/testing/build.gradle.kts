plugins {
    alias(libs.plugins.medishelf.jvm.library)
}

// Pure-JVM test doubles shared by domain, data and feature tests.
dependencies {
    api(projects.core.domain)
    api(projects.core.common)
    api(libs.junit)
    api(libs.kotlinx.coroutines.test)
    api(libs.turbine)
    api(libs.truth)
}
