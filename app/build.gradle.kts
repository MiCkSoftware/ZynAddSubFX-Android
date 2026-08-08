import java.util.Properties
import org.gradle.api.GradleException

val releaseStoreFile = secretValue("LARIA_RELEASE_STORE_FILE")
val releaseStorePassword = secretValue("LARIA_RELEASE_STORE_PASSWORD")
val releaseKeyAlias = secretValue("LARIA_RELEASE_KEY_ALIAS")
val releaseKeyPassword = secretValue("LARIA_RELEASE_KEY_PASSWORD")
val hasReleaseSigning = listOf(
    releaseStoreFile,
    releaseStorePassword,
    releaseKeyAlias,
    releaseKeyPassword
).all { it.isNotBlank() }

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.mick.zynaddsubfx"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = "com.mick.zynaddsubfx"
        minSdk = 29
        targetSdk = 36
        versionCode = getVersionCode()
        versionName = "alpha.${versionCode}"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        ndk {
            abiFilters += listOf("arm64-v8a", "x86_64")
        }

        externalNativeBuild {
            cmake {
                cppFlags += listOf("-std=c++17")
                arguments += listOf("-DZYN_FFT_BACKEND=FFTW3F_NATIVE")
            }
        }
    }

    buildTypes {
        debug {
            // Keep the development build installable next to the release app.
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
        }
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = file(expandUserHome(releaseStoreFile))
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        } else if (isReleaseBuildRequested()) {
            throw GradleException(
                "Missing release signing config. Define LARIA_RELEASE_STORE_FILE, " +
                    "LARIA_RELEASE_STORE_PASSWORD, LARIA_RELEASE_KEY_ALIAS, and " +
                    "LARIA_RELEASE_KEY_PASSWORD in local.properties or environment variables."
            )
        }
    }
}

fun getVersionCode(): Int {
    val isRelease = gradle.startParameter.taskNames.any { it.contains("release", ignoreCase = true) }
    val versionFile = file("version.properties")
    val props = Properties()

    if (!versionFile.exists()) {
        versionFile.writeText("versionCode=1\n")
    }

    versionFile.inputStream().use { props.load(it) }
    val code = props.getProperty("versionCode").toIntOrNull() ?: 1

    if (!isRelease) {
        return code
    }

    props.setProperty("versionCode", (code + 1).toString())
    versionFile.outputStream().use { props.store(it, null) }

    return code
}

fun secretValue(key: String): String {
    val localProperties = Properties()
    val localPropertiesFile = rootProject.file("local.properties")
    if (localPropertiesFile.exists()) {
        localPropertiesFile.inputStream().use { localProperties.load(it) }
    }
    return localProperties.getProperty(key)?.takeIf { it.isNotBlank() }
        ?: providers.environmentVariable(key).orNull?.takeIf { it.isNotBlank() }
        ?: ""
}

fun expandUserHome(path: String): String =
    if (path == "~" || path.startsWith("~/")) {
        System.getProperty("user.home") + path.removePrefix("~")
    } else {
        path
    }

fun isReleaseBuildRequested(): Boolean = gradle.startParameter.taskNames.any { taskName ->
    taskName.contains("release", ignoreCase = true) &&
        !taskName.contains("androidTest", ignoreCase = true) &&
        !taskName.contains("connected", ignoreCase = true) &&
        !taskName.contains("install", ignoreCase = true)
}

tasks.register("printVersionInfo") {
    group = "versioning"
    description = "Print the current Android versionCode and versionName."

    doLast {
        println("versionCode=${getVersionCode()}")
        println("versionName=${android.defaultConfig.versionName}")
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material3.adaptive.navigation.suite)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
