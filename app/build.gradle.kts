plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

val hasFirebaseConfig = layout.projectDirectory.file("google-services.json").asFile.isFile
if (hasFirebaseConfig) {
    apply(plugin = "com.google.gms.google-services")
}

val releaseSigningNames = listOf(
    "ANDROID_RELEASE_KEYSTORE_PATH", "ANDROID_RELEASE_STORE_PASSWORD",
    "ANDROID_RELEASE_KEY_ALIAS", "ANDROID_RELEASE_KEY_PASSWORD", "ANDROID_RELEASE_CERT_SHA256"
)
val releaseSigningEnvironment = releaseSigningNames.associateWith { providers.environmentVariable(it).orNull }
val hasReleaseSigning = releaseSigningEnvironment.values.all { !it.isNullOrBlank() }
val requestedVersionCode = providers.gradleProperty("versionCode")
    .orElse(providers.environmentVariable("ANDROID_VERSION_CODE")).orElse("1").get()
val releaseVersionCode = requestedVersionCode.toIntOrNull()
    ?.takeIf { it in 1..2100000000 }
    ?: throw GradleException("versionCode / ANDROID_VERSION_CODE must be an integer between 1 and 2100000000")

android {
    namespace = "com.hebi.homecrew"
    compileSdk = 36

    sourceSets.named("main") {
        java.srcDir(if (hasFirebaseConfig) "src/firebaseAuth/java" else "src/localOnly/java")
    }
    if (hasFirebaseConfig) {
        sourceSets.named("androidTest") {
            java.srcDir("src/firebaseAndroidTest/java")
        }
    }

    defaultConfig {
        applicationId = "com.hebi.homecrew"
        minSdk = 26
        targetSdk = 36
        versionCode = releaseVersionCode
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("existingRelease") {
                storeFile = file(releaseSigningEnvironment.getValue("ANDROID_RELEASE_KEYSTORE_PATH")!!)
                storePassword = releaseSigningEnvironment.getValue("ANDROID_RELEASE_STORE_PASSWORD")
                keyAlias = releaseSigningEnvironment.getValue("ANDROID_RELEASE_KEY_ALIAS")
                keyPassword = releaseSigningEnvironment.getValue("ANDROID_RELEASE_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            if (hasReleaseSigning) signingConfig = signingConfigs.getByName("existingRelease")
            isMinifyEnabled = false
        }
    }

    buildFeatures { compose = true }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    testOptions { unitTests.isReturnDefaultValues = true }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
}

val verifyReleaseSigning by tasks.registering(Exec::class) {
    doFirst {
        val missing = releaseSigningNames.filter { releaseSigningEnvironment[it].isNullOrBlank() }
        if (missing.isNotEmpty()) {
            throw GradleException("Release signing requires the provisioned app signer: ${missing.joinToString()}")
        }
        commandLine(
            "python3", rootProject.file("scripts/release_signing.py").absolutePath,
            "verify-keystore", "--keystore", releaseSigningEnvironment.getValue("ANDROID_RELEASE_KEYSTORE_PATH")!!
        )
    }
}
tasks.configureEach {
    if (name == "preReleaseBuild") dependsOn(verifyReleaseSigning)
}

dependencies {
    if (hasFirebaseConfig) {
        implementation(platform("com.google.firebase:firebase-bom:34.6.0"))
        implementation("com.google.firebase:firebase-auth")
        implementation("androidx.credentials:credentials:1.6.0")
        implementation("androidx.credentials:credentials-play-services-auth:1.6.0")
        implementation("com.google.android.libraries.identity.googleid:googleid:1.1.1")
        implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.10.2")
    }
    implementation(platform("androidx.compose:compose-bom:2025.10.01"))
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.activity:activity-compose:1.11.0")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.4")
    implementation("androidx.room:room-runtime:2.8.5")
    ksp("androidx.room:room-compiler:2.8.5")
    debugImplementation("androidx.compose.ui:ui-tooling")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.uiautomator:uiautomator:2.3.0")
    androidTestImplementation("androidx.room:room-testing:2.8.5")
}
