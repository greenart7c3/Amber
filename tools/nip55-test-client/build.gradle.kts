plugins {
    id("com.android.application") version "9.4.1"
}

android {
    namespace = "com.greenart7c3.nostrsigner.testclient"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.greenart7c3.nostrsigner.testclient"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}
