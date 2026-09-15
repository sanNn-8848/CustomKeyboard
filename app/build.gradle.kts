import java.util.Properties
import java.io.FileInputStream

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val keystoreProperties = Properties()
val localPropertiesFile = rootProject.file("local.properties")
if (localPropertiesFile.exists()) {
    keystoreProperties.load(FileInputStream(localPropertiesFile))
}

android {
    namespace = "com.romannepali.keyboard"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.romannepali.keyboard"
        minSdk = 26
        targetSdk = 35
        versionCode = 13
        versionName = "1.1.11"
    }

    val storeFileValue = keystoreProperties.getProperty("store.file")
        ?: System.getenv("MERO_STORE_FILE")
    val storePasswordValue = keystoreProperties.getProperty("store.password")
        ?: System.getenv("MERO_STORE_PASSWORD")
    val keyAliasValue = keystoreProperties.getProperty("key.alias")
        ?: System.getenv("MERO_KEY_ALIAS")
    val keyPasswordValue = keystoreProperties.getProperty("key.password")
        ?: System.getenv("MERO_KEY_PASSWORD")

    if (!storeFileValue.isNullOrBlank()) {
        signingConfigs {
            create("release") {
                storeFile = file(storeFileValue)
                storePassword = storePasswordValue
                keyAlias = keyAliasValue
                keyPassword = keyPasswordValue
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            if (!storeFileValue.isNullOrBlank()) {
                signingConfig = signingConfigs.getByName("release")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    lint {
        checkReleaseBuilds = false
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = "11"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.preference:preference-ktx:1.2.1")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.15.1")
    testImplementation("androidx.test:core:1.6.1")
}
