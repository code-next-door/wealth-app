// Black-box tests of the release build. R8 shrinks and renames the app's code for release,
// which can break things that work in debug (e.g. a library missing a keep rule). These
// tests install the app's "minified" build (shrunk exactly like release, debug-signed,
// separate package so real data is untouched) and drive it through the screen with
// UI Automator, from their own process, like a person would.
//
// Run: ./gradlew :releasetest:connectedMinifiedAndroidTest
plugins {
    alias(libs.plugins.android.test)
}

android {
    namespace = "io.github.codenextdoor.wealth.releasetest"
    compileSdk = 37

    defaultConfig {
        minSdk = 31
        targetSdk = 37
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        create("minified") {
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += "release"
        }
    }

    targetProjectPath = ":app"
    // Run in a separate process instead of inside the app, so the tests can't lean on
    // (or be broken by) the app's shrunk classes.
    experimentalProperties["android.experimental.self-instrumenting"] = true

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    jvmToolchain(17)
}

androidComponents {
    // Only the shrunk build is worth testing this way; the debug build has the full suite in :app.
    beforeVariants(selector().all()) { it.enable = it.buildType == "minified" }
}

dependencies {
    implementation(libs.junit)
    implementation(libs.androidx.test.runner)
    implementation(libs.androidx.test.ext.junit)
    implementation(libs.androidx.test.uiautomator)
}
