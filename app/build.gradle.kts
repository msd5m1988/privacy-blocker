plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.hm.privacyblocker"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.hm.privacyblocker"
        minSdk = 28
        targetSdk = 34
        versionCode = 6
        versionName = "1.4.1"
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
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.cardview:cardview:1.0.0")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    // Shizuku API (no root, ADB-level shell). If Shizuku is not installed
    // the app still opens and explains how to install / authorise it.
    implementation("dev.rikka.shizuku:api:13.1.5")
    implementation("dev.rikka.shizuku:provider:13.1.5")
    // v1.1：ShizukuRunner 直接调公开的 IShizukuService.newProcess（见该文件注释），
    // 这个 aidl 产物必须在编译 classpath 上，显式声明，不只靠 api 的传递依赖。
    implementation("dev.rikka.shizuku:aidl:13.1.5")
}
