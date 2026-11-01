plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.pro.videodownloader.player"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.pro.videodownloader.player"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
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
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        viewBinding = true
    }
}

dependencies {
    // Align all Kotlin artifacts (stdlib, jdk7/jdk8, reflect) to the plugin's version.
    implementation(platform("org.jetbrains.kotlin:kotlin-bom:2.0.20"))
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    // Build-server fix: androidx.annotation:annotation:1.6.0 is a metadata-only
    // marker whose classes live in annotation-jvm; the offline file mirror
    // does not follow Gradle's "available-at" indirection, so depend on the
    // jvm artifact directly.
    implementation("androidx.annotation:annotation-jvm:1.6.0")
}
