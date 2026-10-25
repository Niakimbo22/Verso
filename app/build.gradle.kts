import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// En CI : GITHUB_RUN_NUMBER. En local : valeurs de secours (versionCode fixe, haut,
// pour ne jamais redescendre sous une version publiée par la CI).
val ciRunNumber: Int? = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull()

// Signature : le workflow décode le keystore et renseigne ces variables.
// Sans elles, le build reste possible avec la clé debug d'Android.
val signingKeystore: String? = System.getenv("SIGNING_KEYSTORE_PATH")?.takeIf { it.isNotBlank() }
val signingPassword: String? = System.getenv("SIGNING_PASSWORD")?.takeIf { it.isNotBlank() }
val signingAlias: String? = System.getenv("SIGNING_ALIAS")?.takeIf { it.isNotBlank() }
val hasReleaseSigning = signingKeystore != null &&
    signingPassword != null &&
    signingAlias != null &&
    file(signingKeystore).exists()

// SDK Glyph : présent seulement si l'AAR est dans app/libs (voir app/libs/README.md).
// Avec l'AAR, on compile la vraie implémentation (src/glyph) ; sans, une version
// factice (src/glyphStub) pour que le build passe toujours.
val glyphAar = file("libs/glyph-matrix-sdk-2.0.aar")
val hasGlyphSdk = glyphAar.exists()

android {
    namespace = "fr.niakimbo.verso"
    compileSdk = 35

    defaultConfig {
        applicationId = "fr.niakimbo.verso"
        minSdk = 34
        targetSdk = 35
        versionCode = if (ciRunNumber != null) 1000 + ciRunNumber else 900000
        versionName = if (ciRunNumber != null) "0.1.$ciRunNumber" else "0.1.0-local"
        buildConfigField("boolean", "HAS_GLYPH_SDK", hasGlyphSdk.toString())
    }

    sourceSets {
        getByName("main") {
            java.srcDir(if (hasGlyphSdk) "src/glyph/java" else "src/glyphStub/java")
        }
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = file(signingKeystore!!)
                storePassword = signingPassword
                keyAlias = signingAlias
                keyPassword = signingPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = if (hasReleaseSigning) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2025.05.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")

    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")

    // Liquid Glass (réfraction, flou, reflet) : https://github.com/Kyant0/AndroidLiquidGlass (Apache 2.0)
    implementation("io.github.kyant0:backdrop:1.0.1")

    testImplementation("junit:junit:4.13.2")

    // SDK Glyph : ajouté uniquement si l'AAR est présent (voir app/libs/README.md).
    if (hasGlyphSdk) {
        implementation(files(glyphAar))
    }
}
