plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.secrets)
  alias(libs.plugins.google.services)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.google.devtools.ksp)
}

secrets {
  defaultPropertiesFileName = "app/local.defaults.properties"
}

android {
  namespace = "com.inscopelabs.abx.server"
  compileSdk = 36

  buildFeatures {
    compose = true
  }

  defaultConfig {
    applicationId = "com.inscopelabs.abx.server"
    minSdk = 24
    targetSdk = 36
    versionCode = 4
    versionName = "1.0"

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
      isMinifyEnabled = false
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
  implementation(project(":core:keystore"))
  implementation(project(":core:audit"))
  implementation(project(":core:session"))
  implementation(project(":core:tunnel"))
  implementation(project(":core:policy"))
  implementation(project(":core:filesystem"))
  implementation(project(":core:mcp"))
  implementation("com.google.zxing:core:3.5.3")
  implementation(libs.retrofit)
  implementation(libs.converter.moshi)
  implementation(libs.moshi.kotlin)
  implementation(libs.okhttp)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.core)
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.appcompat)
  implementation(libs.androidx.activity.compose)
  implementation(platform(libs.androidx.compose.bom))
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.material.icons.extended)
  implementation(libs.androidx.lifecycle.runtime.ktx)
}
