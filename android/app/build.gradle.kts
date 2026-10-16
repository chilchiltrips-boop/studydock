plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val keystore = providers.environmentVariable("STUDYDOCK_KEYSTORE_FILE").orNull
val storePasswordValue = providers.environmentVariable("STUDYDOCK_KEYSTORE_PASSWORD").orNull
val aliasValue = providers.environmentVariable("STUDYDOCK_KEY_ALIAS").orNull
val keyPasswordValue = providers.environmentVariable("STUDYDOCK_KEY_PASSWORD").orNull
val canSign = listOf(keystore, storePasswordValue, aliasValue, keyPasswordValue).all { !it.isNullOrBlank() }

android {
    namespace = "com.studydock.learning"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.studydock.learning"
        minSdk = 24
        targetSdk = 35
        versionCode = providers.environmentVariable("STUDYDOCK_VERSION_CODE").orNull?.toIntOrNull() ?: 4
        versionName = providers.environmentVariable("STUDYDOCK_VERSION_NAME").orNull ?: "0.3.1-native"
        vectorDrawables.useSupportLibrary = true
    }
    if (canSign) {
        signingConfigs {
            create("production") {
                storeFile = file(keystore!!)
                storePassword = storePasswordValue
                keyAlias = aliasValue
                keyPassword = keyPasswordValue
            }
        }
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            if (canSign) signingConfig = signingConfigs.getByName("production")
        }
    }
    buildFeatures { compose = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.10.01"))
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
