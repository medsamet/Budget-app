plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Cle de signature de debogage stable.
//
// Sans elle, chaque execution de GitHub Actions genere une cle differente et
// la nouvelle version refuse de s'installer par-dessus la precedente
// (« application non installee »), obligeant a desinstaller et donc a perdre
// les donnees. La cle est stockee en base64 pour rester un fichier texte,
// seul format que l'on puisse publier via l'API GitHub.
//
// Il s'agit d'une cle de debogage, au mot de passe conventionnel « android » :
// elle n'a aucune valeur de securite et ne convient pas a une publication sur
// le Play Store, qui exigera une cle privee tenue secrete.
val debugKeystoreEncoded = rootProject.file("ci/debug-keystore.base64")
val debugKeystore = rootProject.file("ci/debug.keystore")
if (debugKeystoreEncoded.exists() && !debugKeystore.exists()) {
    debugKeystore.writeBytes(
        java.util.Base64.getMimeDecoder().decode(debugKeystoreEncoded.readText())
    )
}

android {
    namespace = "com.medsamet.budgetapp"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.medsamet.budgetapp"
        minSdk = 26
        targetSdk = 35
        versionCode = 3
        versionName = "0.2.1"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        getByName("debug") {
            if (debugKeystore.exists()) {
                storeFile = debugKeystore
                storePassword = "android"
                keyAlias = "androiddebugkey"
                keyPassword = "android"
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
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    val composeBom = platform("androidx.compose:compose-bom:2024.10.01")
    implementation(composeBom)

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    implementation("androidx.work:work-runtime-ktx:2.9.1")

    debugImplementation("androidx.compose.ui:ui-tooling")

    testImplementation("junit:junit:4.13.2")
}
