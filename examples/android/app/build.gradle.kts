import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.example.aiaiavatardemo"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.aiaiavatardemo"
        minSdk = 24
        targetSdk = 35
        versionCode = 5
        versionName = "1.4"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Read ZEGO_APP_ID from local.properties or gradle.properties or env var
        val localProps = Properties()
        val localPropsFile = project.rootProject.file("local.properties")
        if (localPropsFile.exists()) localProps.load(localPropsFile.inputStream())
        val envAppId = System.getenv("ZEGO_APPID") ?: ""
        val propAppId = localProps.getProperty("ZEGO_APP_ID") ?: project.findProperty("ZEGO_APP_ID")?.toString() ?: ""
        val appId = when {
            propAppId.isNotEmpty() && propAppId != "0" -> propAppId
            envAppId.isNotEmpty() -> envAppId
            else -> "0"
        }

        // Read ZEGO_API_BASE_URL from local.properties with fallback
        val propApiUrl = localProps.getProperty("ZEGO_API_BASE_URL") ?: project.findProperty("ZEGO_API_BASE_URL")?.toString() ?: ""
        val apiBaseUrl = if (propApiUrl.isNotEmpty()) propApiUrl else "http://10.0.2.2:3000"

        buildConfigField("long", "ZEGO_APP_ID", appId)
        buildConfigField("String", "ZEGO_API_BASE_URL", "\"$apiBaseUrl\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isMinifyEnabled = false
        }
    }

    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = "11"
    }
}

dependencies {
    // ZEGO Express SDK (RTC)
    implementation("im.zego:express-video:3.17.0")

    // Network
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // JSON
    implementation("com.google.code.gson:gson:2.10.1")

    // Material Design
    implementation(libs.androidx.material)

    // AndroidX
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.constraintlayout)

    // Testing
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}