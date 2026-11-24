import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Release signing settings come from local.properties, which is not in git:
//   family.storeFile=<path to the keystore, relative to app/>
//   family.storePassword=...   family.keyAlias=...   family.keyPassword=...
// Without them (or without the keystore file) the release build is signed with the debug key.
val localProps = Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}
val familyStore: File? = localProps.getProperty("family.storeFile")?.let { file(it) }?.takeIf { it.exists() }
val canSign = familyStore != null && localProps.getProperty("family.storePassword") != null

android {
    namespace = "com.finnishkids.verbisland"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.finnishkids.verbisland"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    // The same key as the other family apps, so later versions install over this one (settings: see top of file).
    signingConfigs {
        if (canSign) create("family") {
            storeFile = familyStore
            storePassword = localProps.getProperty("family.storePassword")
            keyAlias = localProps.getProperty("family.keyAlias")
            keyPassword = localProps.getProperty("family.keyPassword")
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            signingConfig = if (canSign) signingConfigs.getByName("family") else signingConfigs.getByName("debug")
        }
    }

    buildFeatures { compose = true }
    composeOptions { kotlinCompilerExtensionVersion = "1.5.3" } // matches Kotlin 1.9.10

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    // built-in Finnish clips (assets/fi/*.ogg) are played straight from the APK
    androidResources { noCompress += listOf("ogg") }
    packaging { resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" } }
}

dependencies {
    // Only standard Compose + Material 3
    implementation(platform("androidx.compose:compose-bom:2024.02.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.8.2")

    testImplementation("junit:junit:4.13.2")
}
