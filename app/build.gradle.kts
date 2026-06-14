plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.google.devtools.ksp)
  alias(libs.plugins.roborazzi)
  alias(libs.plugins.secrets)
}

android {
  namespace = "com.example"
  compileSdk { version = release(36) { minorApiLevel = 1 } }

  defaultConfig {
    applicationId = "com.example"
    minSdk = 24
    targetSdk = 36
    versionCode = 3
    versionName = "v3"

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  signingConfigs {
    create("release") {
      val keystorePath = System.getenv("KEYSTORE_PATH") ?: ""
      val keyFile = if (keystorePath.isNotEmpty()) file(keystorePath) else null
      if (keyFile != null && keyFile.exists() && System.getenv("STORE_PASSWORD") != null) {
        storeFile = keyFile
        storePassword = System.getenv("STORE_PASSWORD")
        keyAlias = System.getenv("KEY_ALIAS") ?: "upload"
        keyPassword = System.getenv("KEY_PASSWORD")
      } else {
        storeFile = file("${rootDir}/debug.keystore")
        storePassword = "android"
        keyAlias = "androiddebugkey"
        keyPassword = "android"
      }
    }
    create("debugConfig") {
      storeFile = file("${rootDir}/debug.keystore")
      storePassword = "android"
      keyAlias = "androiddebugkey"
      keyPassword = "android"
    }
  }

  buildTypes {
    release {
      isCrunchPngs = false
      isMinifyEnabled = false
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      signingConfig = signingConfigs.getByName("release")
    }
    debug {
      signingConfig = signingConfigs.getByName("debugConfig")
    }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }
  buildFeatures {
    compose = true
    buildConfig = true
  }
  testOptions { unitTests { isIncludeAndroidResources = true } }
}

// Configure the Secrets Gradle Plugin to use .env and .env.example files
// to match the convention used in Web projects.
secrets {
  propertiesFileName = ".env"
  defaultPropertiesFileName = ".env.example"
}

// Some unused dependencies are commented out below instead of being removed.
// This makes it easy to add them back in the future if needed.
dependencies {
  implementation(platform(libs.androidx.compose.bom))
  implementation(platform(libs.firebase.bom))
  // implementation(libs.accompanist.permissions)
  implementation(libs.androidx.activity.compose)
  // implementation(libs.androidx.camera.camera2)
  // implementation(libs.androidx.camera.core)
  // implementation(libs.androidx.camera.lifecycle)
  // implementation(libs.androidx.camera.view)
  implementation(libs.androidx.compose.material.icons.core)
  implementation(libs.androidx.compose.material.icons.extended)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.core.ktx)
  // implementation(libs.androidx.datastore.preferences)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  // implementation(libs.androidx.navigation.compose)
  implementation(libs.androidx.room.ktx)
  implementation(libs.androidx.room.runtime)
  // implementation(libs.coil.compose)
  implementation(libs.converter.moshi)
  // implementation(libs.firebase.ai)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.core)
  implementation(libs.logging.interceptor)
  implementation(libs.moshi.kotlin)
  implementation(libs.okhttp)
  // implementation(libs.play.services.location)
  implementation(libs.retrofit)
  testImplementation(libs.androidx.compose.ui.test.junit4)
  testImplementation(libs.androidx.core)
  testImplementation(libs.androidx.junit)
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.robolectric)
  testImplementation(libs.roborazzi)
  testImplementation(libs.roborazzi.compose)
  testImplementation(libs.roborazzi.junit.rule)
  androidTestImplementation(platform(libs.androidx.compose.bom))
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  androidTestImplementation(libs.androidx.espresso.core)
  androidTestImplementation(libs.androidx.junit)
  androidTestImplementation(libs.androidx.runner)
  debugImplementation(libs.androidx.compose.ui.test.manifest)
  debugImplementation(libs.androidx.compose.ui.tooling)
  "ksp"(libs.androidx.room.compiler)
  "ksp"(libs.moshi.kotlin.codegen)
}

val buildDirFile = project.layout.buildDirectory.get().asFile
val rootDirFile = rootDir

tasks.register("copyApks") {
    val srcDebug = File(buildDirFile, "outputs/apk/debug/app-debug.apk")
    val srcRelease = File(buildDirFile, "outputs/apk/release/app-release.apk")
    val destDir = File(rootDirFile, "build-outputs")
    val dotDestDir = File(rootDirFile, ".build-outputs")

    doLast {
        destDir.mkdirs()
        dotDestDir.mkdirs()
        
        if (srcDebug.exists()) {
            println("Copying debug APK of size: ${srcDebug.length()} bytes")
            srcDebug.copyTo(File(destDir, "Kashati_App_Installer.apk"), overwrite = true)
            srcDebug.copyTo(File(destDir, "Kashati.apk"), overwrite = true)
            srcDebug.copyTo(File(destDir, "app-debug.apk"), overwrite = true)
            
            srcDebug.copyTo(File(dotDestDir, "Kashati_App_Installer.apk"), overwrite = true)
            srcDebug.copyTo(File(dotDestDir, "Kashati.apk"), overwrite = true)
            srcDebug.copyTo(File(dotDestDir, "app-debug.apk"), overwrite = true)
        } else {
            println("Debug APK not found at: ${srcDebug.absolutePath}")
        }
        
        if (srcRelease.exists()) {
            println("Copying release APK of size: ${srcRelease.length()} bytes")
            srcRelease.copyTo(File(destDir, "Kashati_App_Installer.apk"), overwrite = true)
            srcRelease.copyTo(File(destDir, "Kashati.apk"), overwrite = true)
            srcRelease.copyTo(File(destDir, "app-release.apk"), overwrite = true)
            
            srcRelease.copyTo(File(dotDestDir, "Kashati_App_Installer.apk"), overwrite = true)
            srcRelease.copyTo(File(dotDestDir, "Kashati.apk"), overwrite = true)
            srcRelease.copyTo(File(dotDestDir, "app-release.apk"), overwrite = true)
        } else {
            println("Release APK not found at: ${srcRelease.absolutePath}")
        }
    }
}

// Ensure copyApks runs automatically after app assemble stages safely
afterEvaluate {
    tasks.findByName("assembleDebug")?.finalizedBy("copyApks")
    tasks.findByName("assembleRelease")?.finalizedBy("copyApks")
}

