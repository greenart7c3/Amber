import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.gradle.ktlint) version libs.versions.ktlint.get()
    alias(libs.plugins.kotlin.ksp) version libs.versions.ksp.get()
    alias(libs.plugins.jetbrainsComposeCompiler)
}

android {
    namespace = "com.greenart7c3.nostrsigner"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.greenart7c3.nostrsigner"
        minSdk = 26
        versionCode = 192
        versionName = "6.1.0"

        buildConfigField("boolean", "IS_FDROID_BUILD", "false")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
        lint {
            disable += listOf("MissingTranslation", "LocalContextGetResourceValueCall")
        }
    }

    androidResources {
        localeFilters += listOf(
            "bn-rBD",
            "cs",
            "cy-rGB",
            "da-rDK",
            "de",
            "el-rGR",
            "en-rGB",
            "eo",
            "es",
            "es-rES",
            "es-rMX",
            "es-rUS",
            "et-rEE",
            "fa",
            "fi-rFI",
            "fo-rFO",
            "fr",
            "fr-rCA",
            "gu-rIN",
            "hi-rIN",
            "hr-rHR",
            "hu",
            "in",
            "in-rID",
            "it-rIT",
            "iw-rIL",
            "ja",
            "kk-rKZ",
            "ko-rKR",
            "ks-rIN",
            "ku-rTR",
            "lt-rLT",
            "ne-rNP",
            "nl",
            "nl-rBE",
            "pcm-rNG",
            "pl-rPL",
            "pt-rBR",
            "pt-rPT",
            "ru",
            "ru-rUA",
            "sa-rIN",
            "sl-rSI",
            "so-rSO",
            "sr-rSP",
            "ss-rZA",
            "sv-rSE",
            "sw-rKE",
            "sw-rTZ",
            "ta",
            "th",
            "tr",
            "uk",
            "ur-rIN",
            "uz-rUZ",
            "vi-rVN",
            "zh",
            "zh-rCN",
            "zh-rHK",
            "zh-rSG",
            "zh-rTW",
        )
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    if (System.getenv("SIGN_RELEASE") != null) {
        val keystorePropertiesFile = rootProject.file("keystore.properties")
        val keystoreProperties = Properties()
        keystoreProperties.load(FileInputStream(keystorePropertiesFile))

        signingConfigs {
            create("release") {
                keyAlias = keystoreProperties["keyAlias"] as String
                keyPassword = keystoreProperties["keyPassword"] as String
                storeFile = file(keystoreProperties["storeFile"] as String)
                storePassword = keystoreProperties["storePassword"] as String
            }
        }
    }

    buildTypes {
        release {
            if (System.getenv("SIGN_RELEASE") != null) {
                signingConfig = signingConfigs.getByName("release")
            }
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            isMinifyEnabled = true
            resValue("string", "app_name", "@string/app_name_release")
        }
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-DEBUG"
            resValue("string", "app_name", "@string/app_name_debug")
        }
    }

    flavorDimensions += "version"

    productFlavors {
        register("free") {
            isDefault = true
            dimension = "version"
        }
        register("offline") {
            dimension = "version"
        }
    }

    splits {
        abi {
            isEnable = true
            reset()
            include("x86", "x86_64", "arm64-v8a", "armeabi-v7a")
            isUniversalApk = true
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
        resValues = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "META-INF/versions/9/OSGI-INF/MANIFEST.MF"
        }
        jniLibs.useLegacyPackaging = true
    }
}

composeCompiler {
    reportsDestination.set(layout.buildDirectory.dir("compose_compiler"))
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(libs.quartz) {
        exclude(group = "net.java.dev.jna")
    }
    implementation(libs.jna) {
        artifact {
            type = "aar"
        }
    }

    implementation(libs.core.ktx)
    implementation(libs.activity.compose)
    implementation(libs.ui)
    implementation(libs.ui.tooling.preview)

    // Navigation
    implementation(libs.navigation.compose)

    implementation(libs.material3)
    implementation(libs.material.icons.extended)

    implementation(libs.appcompat)
    implementation(libs.work.runtime.ktx)
    implementation(libs.material3.window)
    implementation(libs.paging.common)
    implementation(libs.paging.compose)
    implementation(libs.paging.runtime)

    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)
    // JVM-side native secp256k1 so unit tests can exercise ECDH (NIP-44 v3).
    testImplementation(libs.secp256k1.jni.jvm)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.ui.test.junit4)
    debugImplementation(libs.ui.tooling)
    debugImplementation(libs.ui.test.manifest)

    implementation(libs.security.crypto.ktx)

    // Lifecycle
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.ktx)

    // For QR generation
    implementation(libs.core)
    implementation(libs.zxing.android.embedded)

    // Biometrics
    implementation(libs.biometric.ktx)

    ksp(libs.room.compiler)
    implementation(libs.room.ktx)
    implementation(libs.room.runtime)
    implementation(libs.room.paging)

    "freeImplementation"(libs.okhttp)
    "freeImplementation"(libs.okhttpCoroutines)
    "freeImplementation"(libs.kmptor.runtime)
    "freeImplementation"(libs.kmptor.resource.exec)

    // Load images from the web.
    implementation(libs.coil.compose)
    // view gifs
    implementation(libs.coil.gif)
    // view svgs
    implementation(libs.coil.svg)
    // enables network for coil
    implementation(libs.coil.okhttp)

    implementation(libs.storage)

    implementation(libs.datastore.preferences)
    implementation(libs.security.crypto)

    implementation(libs.lifecycle.process)
    implementation(libs.kotlinx.collections.immutable)
}
