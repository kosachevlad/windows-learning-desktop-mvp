plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "ua.school.windowsdesktop"
    compileSdk = 36
    val uploadStoreFile = rootProject.file("play-upload-key.jks")
    val uploadStorePassword = providers.gradleProperty("PLAY_UPLOAD_STORE_PASSWORD").orNull
    val uploadKeyPassword = providers.gradleProperty("PLAY_UPLOAD_KEY_PASSWORD").orNull
    signingConfigs {
        create("release") {
            if (uploadStoreFile.exists() && uploadStorePassword != null && uploadKeyPassword != null) {
                storeFile = uploadStoreFile
                storePassword = uploadStorePassword
                keyAlias = "play-upload"
                keyPassword = uploadKeyPassword
            }
        }
    }
    defaultConfig {
        applicationId = "ua.school.windowsdesktop"
        minSdk = 26
        targetSdk = 36
        versionCode = 8
        versionName = "0.1.7"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    buildFeatures { compose = true; buildConfig = true }
    buildTypes {
        release {
            isMinifyEnabled = false
            isShrinkResources = false
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.all {
            it.maxHeapSize = "2g"
            val testRoot = providers.environmentVariable("WINDOWS_LEARNING_TEST_ROOT").orNull
                ?.let { file(it) } ?: layout.buildDirectory.dir("test-runtime").get().asFile
            val testHome = testRoot.resolve("h")
            val testTemp = testRoot.resolve("t")
            it.systemProperty("user.home", testHome.absolutePath)
            it.systemProperty("java.io.tmpdir", testTemp.absolutePath)
            it.doFirst { testHome.mkdirs(); testTemp.mkdirs() }
            it.systemProperty("robolectric.dependency.repo.url", "https://repo.maven.apache.org/maven2")
        }
    }
}

ksp { arg("room.schemaLocation", "$projectDir/schemas") }

// Every debug build is also archived as the next numbered stage APK.
// For example, stage55.apk becomes stage56.apk on the next build.
val copyNextStageApk = tasks.register("copyNextStageApk") {
    doLast {
        val apkDirectory = project.file("$projectDir/apk")
        apkDirectory.mkdirs()
        val stagePattern = Regex("stage(\\d+)\\.apk")
        val nextStage = apkDirectory.listFiles()
            ?.mapNotNull { stagePattern.matchEntire(it.name)?.groupValues?.get(1)?.toIntOrNull() }
            ?.maxOrNull()?.plus(1) ?: 1
        val builtApk = layout.buildDirectory.file("outputs/apk/debug/app-debug.apk").get().asFile
        if (!builtApk.isFile) error("Debug APK was not found: ${builtApk.absolutePath}")
        val destination = apkDirectory.resolve("stage$nextStage.apk")
        builtApk.copyTo(destination, overwrite = true)
        logger.lifecycle("Archived APK: ${destination.absolutePath}")
    }
}

afterEvaluate {
    tasks.named("assembleDebug") { finalizedBy(copyNextStageApk) }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.room.runtime)
    implementation(libs.kotlinx.coroutines.android)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.lifecycle.runtime)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    debugImplementation(libs.androidx.compose.ui.tooling)
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
