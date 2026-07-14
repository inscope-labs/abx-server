plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.secrets)
  alias(libs.plugins.google.services)
}

secrets {
  defaultPropertiesFileName = "app/local.defaults.properties"
}

android {
  namespace = "com.inscopelabs.abx.server"
  compileSdk = 36

  defaultConfig {
    applicationId = "com.inscopelabs.abx.server"
    minSdk = 24
    targetSdk = 36
    versionCode = 1
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
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.appcompat)
}
