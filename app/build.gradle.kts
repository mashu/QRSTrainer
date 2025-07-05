plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.so5km.qrstrainer"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.so5km.qrstrainer"
        minSdk = 31
        targetSdk = 35
        versionCode = 13
        versionName = "1.0.13"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            keyAlias = "qrstrainer"
            keyPassword = System.getenv("KEYSTORE_PASSWORD") ?: ""
            storeFile = file("${rootProject.projectDir}/qrstrainer-release-key.jks")
            storePassword = System.getenv("KEYSTORE_PASSWORD") ?: ""
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("release")
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
        viewBinding = true
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.lifecycle.livedata.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.navigation.fragment.ktx)
    implementation(libs.androidx.navigation.ui.ktx)
    
    // CardView for statistics display
    implementation("androidx.cardview:cardview:1.0.0")
    
    // RecyclerView is included in androidx.appcompat, but let's be explicit
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
