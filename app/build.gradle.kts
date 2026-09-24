plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val fkVersion = providers.gradleProperty("fkVersion").orNull ?: "0.1.0"
val fkVersionCode = providers.gradleProperty("fkVersionCode").orNull?.toInt() ?: 1
require(fkVersion.matches(Regex("[0-9]+\\.[0-9]+\\.[0-9]+")))
require(fkVersionCode in 1..2_100_000_000)

android {
    namespace = "uk.cassiangroup.fkinput"
    compileSdk = 36
    ndkVersion = "28.0.13004108"

    defaultConfig {
        applicationId = "uk.cassiangroup.fkinput"
        minSdk = 29
        targetSdk = 36
        versionCode = fkVersionCode
        versionName = fkVersion
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        ndk {
            abiFilters += "arm64-v8a"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }
}

dependencies {
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
}
