import java.util.Properties

plugins {
    alias(libs.plugins.medishelf.android.application)
    alias(libs.plugins.medishelf.android.applicationCompose)
    alias(libs.plugins.medishelf.android.hilt)
    alias(libs.plugins.kotlin.serialization)
}

/**
 * Release signing comes from environment variables (CI) or an untracked keystore.properties
 * (local). Without either, release builds are produced unsigned rather than failing.
 */
val releaseSigning: Map<String, String>? = run {
    val props = Properties().apply {
        rootProject.file("keystore.properties").takeIf { it.isFile }?.inputStream()?.use(::load)
    }
    fun value(env: String, prop: String) = System.getenv(env)?.takeIf(String::isNotBlank) ?: props.getProperty(prop)
    mapOf(
        "storeFile" to value("MEDISHELF_KEYSTORE_PATH", "storeFile"),
        "storePassword" to value("MEDISHELF_KEYSTORE_PASSWORD", "storePassword"),
        "keyAlias" to value("MEDISHELF_KEY_ALIAS", "keyAlias"),
        "keyPassword" to value("MEDISHELF_KEY_PASSWORD", "keyPassword"),
    ).takeIf { config -> config.values.all { !it.isNullOrBlank() } }?.mapValues { it.value!! }
}

android {
    namespace = "com.medishelf"

    defaultConfig {
        applicationId = "com.medishelf"
        // CI passes these from the git tag (v1.2.3 -> 1.2.3 / 1002003).
        versionCode = providers.gradleProperty("versionCode").orNull?.toInt() ?: 1
        versionName = providers.gradleProperty("versionName").orNull ?: "0.1.0-dev"
    }

    signingConfigs {
        if (releaseSigning != null) {
            create("release") {
                storeFile = file(releaseSigning.getValue("storeFile"))
                storePassword = releaseSigning.getValue("storePassword")
                keyAlias = releaseSigning.getValue("keyAlias")
                keyPassword = releaseSigning.getValue("keyPassword")
            }
        }
    }

    // Per-ABI APKs for direct download (ML Kit + SQLCipher native libs make the universal APK
    // ~70 MB). Play uses the AAB, which splits by ABI on its own. AGP can't produce the bundle
    // in the same run as ABI-split APKs with resource shrinking on, so build the bundle with
    // -PabiSplits=false in a separate invocation.
    splits {
        abi {
            isEnable = providers.gradleProperty("abiSplits").orNull?.toBoolean() ?: true
            reset()
            include("arm64-v8a", "armeabi-v7a", "x86_64")
            isUniversalApk = true
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfigs.findByName("release")?.let { signingConfig = it }
        }
    }

    buildFeatures {
        buildConfig = true
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    implementation(projects.core.designsystem)
    implementation(projects.core.ui)
    implementation(projects.core.model)
    implementation(projects.core.data)
    implementation(projects.core.work)
    implementation(projects.core.database)
    implementation(projects.core.datastore)
    implementation(projects.core.security)
    implementation(projects.core.notifications)
    implementation(projects.core.backup)
    implementation(libs.androidx.hilt.work)
    implementation(libs.androidx.work.runtime)
    implementation(projects.core.domain)

    implementation(projects.feature.onboarding)
    implementation(projects.feature.lock)
    implementation(projects.feature.dashboard)
    implementation(projects.feature.medicines)
    implementation(projects.feature.scan)
    implementation(projects.feature.locations)
    implementation(projects.feature.family)
    implementation(projects.feature.settings)

    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(projects.core.testing)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(libs.androidx.navigation.testing)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4.accessibility)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    // Espresso < 3.7 calls an InputManager API removed in Android 17.
    androidTestImplementation(libs.androidx.test.espresso.core)
}
