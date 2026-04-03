import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.mad.cw"
    compileSdk {
        version = release(36)
    }

    buildFeatures {
        buildConfig = true
    }

    defaultConfig {
        applicationId = "com.mad.cw"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        val localProps = Properties()
        val localFile = rootProject.file("local.properties")
        if (localFile.exists()) {
            localFile.inputStream().use { localProps.load(it) }
        }
        val supabaseUrl = localProps.getProperty("supabase.url", "")
        val supabaseAnonKey = localProps.getProperty("supabase.anon.key", "")
        val geminiApiKey =
            localProps.getProperty("GEMINI_API_KEY", localProps.getProperty("gemini.api.key", ""))

        fun escapeForBuildConfig(value: String): String =
            value.replace("\\", "\\\\").replace("\"", "\\\"")

        buildConfigField("String", "SUPABASE_URL", "\"${escapeForBuildConfig(supabaseUrl)}\"")
        buildConfigField("String", "SUPABASE_ANON_KEY", "\"${escapeForBuildConfig(supabaseAnonKey)}\"")
        buildConfigField("String", "GEMINI_API_KEY", "\"${escapeForBuildConfig(geminiApiKey)}\"")
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
}

dependencies {
    // Google AI Gemini (Kotlin SDK; use ChatFutures / GenerativeModel from Java)
    implementation("com.google.ai.client.generativeai:generativeai:0.9.0")
    implementation("com.google.guava:guava:33.3.1-android")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel:2.8.7")
    implementation("androidx.lifecycle:lifecycle-livedata:2.8.7")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
    implementation("com.airbnb.android:lottie:6.3.0")
    implementation("com.google.android.material:material:1.11.0")
    implementation("com.github.bumptech.glide:glide:4.16.0")
}