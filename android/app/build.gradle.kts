plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android {
  namespace = "com.studydock.learning"
  compileSdk = 35
  defaultConfig {
    applicationId = "com.studydock.learning"
    minSdk = 26
    targetSdk = 35
    versionCode = 3
    versionName = "0.2.1"
  }
  compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
  kotlinOptions { jvmTarget = "17" }
}

dependencies { implementation("androidx.webkit:webkit:1.12.1") }
