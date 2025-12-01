plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)

    // Add the Google services Gradle plugin
    id("com.google.gms.google-services")

}

android {
    signingConfigs {
        create("Costa") {
        }
    }
    namespace = "com.oo_dev17.qrnotes"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.oo_dev17.qrnotes"
        minSdk = 25
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
    buildFeatures {
        viewBinding = true
    }
}

dependencies {
    implementation(platform("com.google.firebase:firebase-bom:34.6.0"))
    implementation("com.google.firebase:firebase-storage:22.0.1")
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("com.google.android.material:material:1.13.0")
    implementation("androidx.constraintlayout:constraintlayout:2.2.1")
    implementation("androidx.navigation:navigation-fragment-ktx:2.9.6")
    implementation("androidx.navigation:navigation-ui-ktx:2.9.6")
    implementation("androidx.recyclerview:recyclerview:1.4.0")
    implementation("com.google.firebase:firebase-common-ktx:21.0.0")
    implementation("com.google.firebase:firebase-firestore-ktx:25.1.4")
    implementation("com.google.firebase:firebase-auth:24.0.1")
    implementation("com.github.chrisbanes:PhotoView:2.3.0")
    implementation("androidx.credentials:credentials:1.5.0")
    implementation("androidx.credentials:credentials-play-services-auth:1.5.0")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.1.1")
    implementation("com.google.firebase:firebase-messaging:25.0.1")
    implementation("com.journeyapps:zxing-android-embedded:4.3.0")
    implementation(libs.androidx.activity)
    implementation(libs.androidx.annotation)
    implementation(libs.androidx.lifecycle.livedata.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.room.runtime)
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
    implementation("com.github.bumptech.glide:glide:5.0.5")
    annotationProcessor("com.github.bumptech.glide:compiler:5.0.5")
    implementation("com.google.firebase:firebase-appcheck-playintegrity:19.0.1")
}

kotlin {
    jvmToolchain(17)
}
