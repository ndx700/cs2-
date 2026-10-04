plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android {
    namespace = "com.ali.cs2utility"
    compileSdk = 34
    defaultConfig {
        applicationId = "com.ali.cs2utility"
        minSdk = 24
        targetSdk = 34
        versionCode = 6
        versionName = "0.6.0-test2"
        testInstrumentationRunner = "android.test.InstrumentationTestRunner"
    }
    signingConfigs {
        getByName("debug") {
            storeFile = rootProject.file("tools/debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }
    buildTypes {
        debug { applicationIdSuffix = ".dust2" }
        release { isMinifyEnabled = false }
    }
    androidResources { noCompress += listOf("gz", "png") }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}
dependencies { testImplementation("junit:junit:4.13.2"); testImplementation("org.json:json:20240303") }
