plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.nex.pic"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.nex.pic"
        minSdk = 30          // Android 11
        targetSdk = 36       // Android 16
        versionCode = 1
        versionName = "1.0.0"
    }

    val ksFile = file("nexpic.jks")
    signingConfigs {
        if (ksFile.exists()) {
            create("release") {
                storeFile = ksFile
                storePassword = System.getenv("KS_PASS") ?: "nexpic123"
                keyAlias = System.getenv("KS_ALIAS") ?: "nexpic"
                keyPassword = System.getenv("KS_PASS") ?: "nexpic123"
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = if (ksFile.exists()) signingConfigs.getByName("release")
            else signingConfigs.getByName("debug")
        }
    }

    androidResources {
        localeFilters += listOf("en", "id", "in")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        viewBinding = true
    }

    packaging {
        resources.excludes += setOf("/META-INF/{AL2.0,LGPL2.1}", "DebugProbesKt.bin", "kotlin-tooling-metadata.json")
    }

    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.activity:activity-ktx:1.9.3")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.viewpager2:viewpager2:1.1.0")
    implementation("androidx.preference:preference-ktx:1.2.1")
    implementation("androidx.core:core-splashscreen:1.0.1")
    implementation("androidx.exifinterface:exifinterface:1.3.7")
    implementation("com.github.bumptech.glide:glide:4.16.0")
}
