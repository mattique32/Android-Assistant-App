plugins {
    id("com.android.application")
    id("kotlin-android")
    id("kotlin-kapt")
    id("kotlin-android-extensions")
}

android {
    compileSdkVersion(Config.Android.compileSdk)

    defaultConfig {
        applicationId = Config.applicationId
        minSdkVersion(Config.Android.wearMinSdk)
        targetSdkVersion(Config.Android.targetSdk)

        val envCode = System.getenv("VERSION_CODE")
        val vCode = envCode?.toIntOrNull() ?: 1
        versionCode = vCode
        versionName = "${Config.version}-$vCode"
    }

    signingConfigs {
        create("release") {
            storeFile = file("../release_keystore.jks")
            storePassword = System.getenv("KEYSTORE_PASSWORD") ?: "homeassistant"
            keyAlias = System.getenv("KEYSTORE_ALIAS") ?: "homeassistant"
            keyPassword = System.getenv("KEYSTORE_ALIAS_PASSWORD") ?: "homeassistant"
            isV1SigningEnabled = true
            isV2SigningEnabled = true
        }
    }

    buildTypes {
        named("debug").configure {
            applicationIdSuffix = ".debug"
        }
        named("release").configure {
            isDebuggable = true
            isJniDebuggable = false
            isZipAlignEnabled = true
            signingConfig = signingConfigs.getByName("release")
        }
    }

    viewBinding {
        isEnabled = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    testOptions {
        unitTests.apply { isReturnDefaultValues = true }
    }

    tasks.withType<Test> {
        useJUnitPlatform {
            includeEngines("spek2")
        }
    }

    lintOptions {
        disable("MissingTranslation")
    }
}

dependencies {
    implementation(project(":common"))
    implementation(project(":domain"))
    implementation(project(":resources"))

    implementation(Config.Dependency.Kotlin.core)
    implementation(Config.Dependency.Kotlin.coroutines)
    implementation(Config.Dependency.Kotlin.coroutinesAndroid)

    implementation(Config.Dependency.AndroidX.core)
    implementation(Config.Dependency.AndroidX.lifecycle)
    implementation(Config.Dependency.AndroidX.lifecycleExtensions)
    implementation(Config.Dependency.AndroidX.lifecycleJava8)
    implementation(Config.Dependency.AndroidX.fragment)
    implementation(Config.Dependency.AndroidX.recyclerview)
    implementation(Config.Dependency.AndroidX.wear)
    implementation(Config.Dependency.AndroidX.preference)

    implementation(Config.Dependency.Google.material)
    implementation(Config.Dependency.Google.dagger)
    kapt(Config.Dependency.Google.daggerCompiler)

    implementation(Config.Dependency.Google.wearableSupport)
    compileOnly(Config.Dependency.Google.wearable)

    implementation(Config.Dependency.Play.wearable)
}