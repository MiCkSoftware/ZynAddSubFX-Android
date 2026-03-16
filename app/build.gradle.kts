import java.util.Properties

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
        versionName = "0.1_alpha"

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
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
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
