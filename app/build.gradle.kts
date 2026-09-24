plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
}

// Supply upload credentials through the environment; never commit a keystore or passwords.
val uploadStoreFile = providers.environmentVariable("FORUM_INDEX_UPLOAD_STORE_FILE")
val uploadStorePassword = providers.environmentVariable("FORUM_INDEX_UPLOAD_STORE_PASSWORD")
val uploadKeyAlias = providers.environmentVariable("FORUM_INDEX_UPLOAD_KEY_ALIAS")
val uploadKeyPassword = providers.environmentVariable("FORUM_INDEX_UPLOAD_KEY_PASSWORD")
val hasUploadSigning = listOf(uploadStoreFile, uploadStorePassword, uploadKeyAlias, uploadKeyPassword)
    .all { !it.orNull.isNullOrBlank() }

android {
    namespace = "com.musaraj.forumindex"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.musaraj.forumindex"
        minSdk = 28
        targetSdk = 37
        versionCode = providers.gradleProperty("releaseVersionCode").orElse("1").get().toInt().also {
            require(it in 1..2_100_000_000) { "releaseVersionCode must be between 1 and 2100000000" }
        }
        versionName = providers.gradleProperty("releaseVersionName").orElse("1.0").get().also {
            require(it.isNotBlank()) { "releaseVersionName must not be blank" }
        }
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures.compose = true

    signingConfigs {
        if (hasUploadSigning) {
            create("upload") {
                storeFile = rootProject.file(uploadStoreFile.get())
                storePassword = uploadStorePassword.get()
                keyAlias = uploadKeyAlias.get()
                keyPassword = uploadKeyPassword.get()
            }
        }
    }

    buildTypes {
        release {
            isDebuggable = false
            if (hasUploadSigning) signingConfig = signingConfigs.getByName("upload")
        }
    }
}

val checkPlaySigning by tasks.registering {
    group = "verification"
    description = "Require upload signing credentials before building the Play release bundle."
    doLast {
        check(hasUploadSigning) {
            "Set all four FORUM_INDEX_UPLOAD_* environment variables; see docs/play-store-release.md."
        }
        check(rootProject.file(uploadStoreFile.get()).isFile) { "Upload keystore file does not exist." }
    }
}

tasks.matching { it.name == "bundleRelease" }.configureEach {
    dependsOn(checkPlaySigning)
}

dependencies {
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20250517")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
    androidTestImplementation("androidx.test:runner:1.7.0")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
