plugins {
    alias(libs.plugins.android.application)
    id("com.google.gms.google-services")
}

android {
    namespace = "com.example.capstoneprojectapp"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.capstoneprojectapp"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
        vectorDrawables.useSupportLibrary = true

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Export Room schema for migration history
        javaCompileOptions {
            annotationProcessorOptions {
                arguments["room.schemaLocation"] = "$projectDir/schemas"
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
}

dependencies {

    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.constraintlayout)
    
    // PyTorch Mobile dependencies (for ML model integration)
    implementation("org.pytorch:pytorch_android_lite:1.12.2")
    implementation("org.pytorch:pytorch_android_torchvision_lite:1.12.2")
    
    // Image processing
    implementation("androidx.exifinterface:exifinterface:1.3.6")
    
    // Permissions handling
    implementation("androidx.core:core-ktx:1.12.0")
    
    // Firebase dependencies
    implementation(platform("com.google.firebase:firebase-bom:34.4.0"))
    implementation("com.google.firebase:firebase-analytics")
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-firestore")
    implementation("com.google.android.gms:play-services-auth:20.7.0")
    
    // Room (local offline cache for farmer/guest)
    implementation("androidx.room:room-runtime:2.6.1")
    annotationProcessor("androidx.room:room-compiler:2.6.1")

    // WorkManager for background sync
    implementation("androidx.work:work-runtime:2.9.0")

    // Charts
    implementation("com.github.PhilJay:MPAndroidChart:3.1.0")
    
    // Mapping dependencies removed as expert mapping feature was deleted

    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
}
