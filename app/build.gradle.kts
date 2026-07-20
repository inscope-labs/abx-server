plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.secrets)
  alias(libs.plugins.google.services)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.google.devtools.ksp)
}

import java.util.Properties
import java.io.FileInputStream

val versionProps = Properties().apply {
  load(FileInputStream(rootProject.file("version.properties")))
}
val verMajor = versionProps.getProperty("versionMajor").trim()
val verMinor = versionProps.getProperty("versionMinor").trim()
val verDebug = versionProps.getProperty("versionDebug").trim()

secrets {
  defaultPropertiesFileName = "app/local.defaults.properties"
}

android {
  namespace = "com.inscopelabs.abx.server"
  compileSdk = 36

  buildFeatures {
    compose = true
    buildConfig = true
  }

  defaultConfig {
    applicationId = "com.inscopelabs.abx.server"
    minSdk = 24
    targetSdk = 36
    versionCode = versionProps.getProperty("versionCode").trim().toInt()
    versionName = "$verMajor.$verMinor.$verDebug"

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  signingConfigs {
    create("release") {
      val keystorePath = System.getenv("KEYSTORE_PATH") ?: ""
      val keystoreFile = if (keystorePath.isNotEmpty()) file(keystorePath) else null
      if (keystoreFile != null && keystoreFile.exists()) {
        storeFile = keystoreFile
        storePassword = System.getenv("STORE_PASSWORD") ?: ""
        keyAlias = "upload"
        keyPassword = System.getenv("KEY_PASSWORD") ?: ""
      } else {
        val debugKeystore = signingConfigs.getByName("debug")
        storeFile = debugKeystore.storeFile
        storePassword = debugKeystore.storePassword
        keyAlias = debugKeystore.keyAlias
        keyPassword = debugKeystore.keyPassword
      }
    }
  }

  buildTypes {
    release {
      isMinifyEnabled = true
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      isShrinkResources = false
      signingConfig = signingConfigs.getByName("release")
    }
    debug {
      isMinifyEnabled = false
      isShrinkResources = false
      signingConfig = signingConfigs.getByName("debug")
    }
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }
}

dependencies {
  implementation(this.project(":core:keystore"))
  implementation(this.project(":core:audit"))
  implementation(this.project(":core:session"))
  implementation(this.project(":core:tunnel"))
  implementation(this.project(":core:policy"))
  implementation(this.project(":core:filesystem"))
  implementation(this.project(":core:mcp"))
  implementation("com.google.zxing:core:3.5.3")
  implementation(libs.retrofit)
  implementation(libs.converter.moshi)
  implementation(libs.moshi.kotlin)
  implementation(libs.okhttp)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.core)
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.documentfile)
  implementation(libs.androidx.webkit)
  implementation(libs.androidx.appcompat)
  implementation(libs.androidx.material)
  implementation(libs.androidx.constraintlayout)
  implementation(libs.androidx.fragment.ktx)
  implementation(libs.androidx.activity.compose)
  implementation(platform(libs.androidx.compose.bom))
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.material.icons.extended)
  implementation(libs.androidx.lifecycle.runtime.ktx)
}
