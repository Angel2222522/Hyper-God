import java.io.File
import java.security.MessageDigest
import java.net.URI

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.kapt")
}

data class PinnedAsset(
    val relativePath: String,
    val sourceUrl: String,
    val size: Long,
    val sha256: String
)

fun fileSha256(file: File): String {
    val digest = MessageDigest.getInstance("SHA-256")
    file.inputStream().use { input ->
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val read = input.read(buffer)
            if (read <= 0) break
            digest.update(buffer, 0, read)
        }
    }
    return digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
}

val pinnedAssets = listOf(
    PinnedAsset(
        "src/main/assets/tessdata/ell.traineddata",
        "https://raw.githubusercontent.com/tesseract-ocr/tessdata_fast/65727574dfcd264acbb0c3e07860e4e9e9b22185/ell.traineddata",
        1_419_514L,
        "4fba8a0b461038d51f1c20d043d4f2ac38c4e778f1b90830847f7bd8fa3ba726"
    ),
    PinnedAsset(
        "src/main/assets/tessdata/eng.traineddata",
        "https://raw.githubusercontent.com/tesseract-ocr/tessdata_fast/65727574dfcd264acbb0c3e07860e4e9e9b22185/eng.traineddata",
        4_113_088L,
        "7d4322bd2a7749724879683fc3912cb542f19906c83bcc1a52132556427170b2"
    ),
    PinnedAsset(
        "src/main/res/font/noto_sans_variable.ttf",
        "https://raw.githubusercontent.com/google/fonts/e1118da94a8cb00cf6d06cdac9ef13eb1e5c6ab7/ofl/notosans/NotoSans%5Bwdth,wght%5D.ttf",
        2_049_096L,
        "bfb7bb691513f12e734dc346c03a03f784912432d7e3fa8e56efcf906fe86b3d"
    )
)
val preparePinnedAssets = tasks.register("preparePinnedAssets") {
    doLast {
        for (asset in pinnedAssets) {
            val destination = layout.projectDirectory.file(asset.relativePath).asFile
            val alreadyValid = destination.isFile &&
                destination.length() == asset.size &&
                fileSha256(destination) == asset.sha256
            if (alreadyValid) continue
            destination.parentFile?.mkdirs()
            val temporary = File(destination.parentFile, ".${destination.name}.download")
            try {
                URI(asset.sourceUrl).toURL().openStream().use { input ->
                    temporary.outputStream().use { output -> input.copyTo(output) }
                }
                check(temporary.length() == asset.size) {
                    "Unexpected pinned asset size for ${asset.relativePath}: ${temporary.length()}"
                }
                check(fileSha256(temporary) == asset.sha256) {
                    "Pinned asset checksum mismatch for ${asset.relativePath}"
                }
                if (destination.exists()) check(destination.delete()) { "Could not replace ${asset.relativePath}" }
                check(temporary.renameTo(destination)) { "Could not install ${asset.relativePath}" }
            } finally {
                temporary.delete()
            }
        }
    }
}

val releaseKeystorePath = System.getenv("HYPER_GOD_KEYSTORE_PATH")
val releaseKeystorePassword = System.getenv("HYPER_GOD_KEYSTORE_PASSWORD")
val releaseKeyAlias = System.getenv("HYPER_GOD_KEY_ALIAS")
val releaseKeyPassword = System.getenv("HYPER_GOD_KEY_PASSWORD")
val releaseSigningInputs = listOf(
    releaseKeystorePath,
    releaseKeystorePassword,
    releaseKeyAlias,
    releaseKeyPassword
)
val hasAnyReleaseSigningInput = releaseSigningInputs.any { !it.isNullOrBlank() }
val hasAllReleaseSigningInputs = releaseSigningInputs.all { !it.isNullOrBlank() }

if (hasAnyReleaseSigningInput && !hasAllReleaseSigningInputs) {
    throw GradleException(
        "Incomplete Hyper God release signing configuration. " +
            "Provide all HYPER_GOD_* signing environment variables or none."
    )
}

android {
    namespace = "com.angel.hypergod"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.angel.hypergod"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true
    }

    sourceSets {
        getByName("main") {
            // The pinned models are versioned with the source tree. The build
            // verifies their size and Git blob identity before packaging.
            assets.setSrcDirs(listOf(layout.projectDirectory.dir("src/main/assets").asFile))
        }
        getByName("androidTest") {
            assets.srcDir("$projectDir/schemas")
        }
    }

    signingConfigs {
        if (hasAllReleaseSigningInputs) {
            create("release") {
                storeFile = file(releaseKeystorePath!!)
                storePassword = releaseKeystorePassword!!
                keyAlias = releaseKeyAlias!!
                keyPassword = releaseKeyPassword!!
            }
        }
    }

    buildTypes {
        release {
            if (hasAllReleaseSigningInputs) {
                signingConfig = signingConfigs.getByName("release")
            }
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
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
        buildConfig = true
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }

    lint {
        abortOnError = true
        warningsAsErrors = false
    }
}

tasks.configureEach {
    if (name.startsWith("merge") && (name.endsWith("Assets") || name.endsWith("Resources"))) {
        dependsOn(preparePinnedAssets)
    }
}

kapt {
    correctErrorTypes = true
    arguments {
        arg("room.schemaLocation", "$projectDir/schemas")
        arg("room.incremental", "true")
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2025.05.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.fragment:fragment-ktx:1.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.0")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.room:room-runtime:2.7.0")
    implementation("androidx.room:room-ktx:2.7.0")
    kapt("androidx.room:room-compiler:2.7.0")

    implementation("androidx.work:work-runtime-ktx:2.10.1")
    implementation("androidx.biometric:biometric:1.1.0")
    implementation("androidx.exifinterface:exifinterface:1.4.0")

    implementation("cz.adaptech.tesseract4android:tesseract4android-openmp:4.9.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
    testImplementation("androidx.room:room-testing:2.7.0")
    testImplementation("androidx.arch.core:core-testing:2.2.0")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test:rules:1.6.1")
    androidTestImplementation("androidx.room:room-testing:2.7.0")
    androidTestImplementation("androidx.work:work-testing:2.10.1")
    androidTestImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
