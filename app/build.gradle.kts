plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    // @Serializable: screen routes (type-safe navigation) and the backup file.
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

android {
    namespace = "io.github.codenextdoor.wealth"
    compileSdk = 37

    defaultConfig {
        applicationId = "io.github.codenextdoor.wealth"
        minSdk = 31
        targetSdk = 37
        // Release builds get these from the Git tag (see .github/workflows/release.yml).
        versionName = providers.gradleProperty("versionName").getOrElse("0.1.0")
        versionCode = providers.gradleProperty("versionCode").map(String::toInt).getOrElse(1)

        // Starts the app with an in-memory database so on-device tests never
        // touch real data.
        testInstrumentationRunner = "io.github.codenextdoor.wealth.WealthTestRunner"
    }

    sourceSets {
        // Room's migration test reads the exported schema history. It runs on the JVM (so CI checks
        // it), where Robolectric only sees the app's own assets: debug builds carry the schemas,
        // release builds don't.
        getByName("debug").assets.directories.add("$projectDir/schemas")
    }

    // The release key lives only in GitHub secrets (and the author's own backup), never in
    // the repo. CI passes it in through these environment variables; without them the
    // release APK is simply unsigned.
    val releaseKeystore = providers.environmentVariable("WEALTH_KEYSTORE_FILE").orNull
    signingConfigs {
        if (releaseKeystore != null) {
            create("release") {
                storeFile = file(releaseKeystore)
                // keytool's default (PKCS12) keystores use one password for the store and the key.
                storePassword = providers.environmentVariable("WEALTH_KEYSTORE_PASSWORD").get()
                keyPassword = storePassword
                keyAlias = providers.environmentVariable("WEALTH_KEY_ALIAS").get()
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = signingConfigs.findByName("release")
        }
        // Shrunk exactly like release, but signed with the debug key and installed as a
        // separate app, so the black-box tests in :releasetest can drive the R8 output
        // without touching the real app's data.
        create("minified") {
            initWith(getByName("release"))
            applicationIdSuffix = ".minified"
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += "release"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    lint {
        // Also check the test code (unit and device tests), not only the app.
        checkTestSources = true
    }

    testOptions {
        // Robolectric tests read real resources (e.g. default category names).
        unitTests.isIncludeAndroidResources = true
        unitTests.all {
            // Optional local check of the statement parsers against a real file:
            // ./gradlew testDebugUnitTest --tests '*LocalStatementCheck*' -PstatementFile=/path/to/statement.pdf
            // The file is only read on the developer's machine and never committed.
            project.findProperty("statementFile")?.let { path -> it.systemProperty("statementFile", path) }
            project.findProperty("statementDump")?.let { path -> it.systemProperty("statementDump", path) }
        }
    }
}

kotlin {
    jvmToolchain(17)
}

ksp {
    // Room writes a JSON snapshot of every schema version here. These files
    // are committed and used to write/verify migrations.
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    constraints {
        // Libraries bring older serialization versions; Room's migration tester needs 1.8+,
        // and device tests run on the app's copy. Only raises versions already in use.
        implementation(libs.kotlinx.serialization.core)
        implementation(libs.kotlinx.serialization.json)
    }

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)
    implementation(libs.kotlinx.serialization.json)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    debugImplementation(libs.androidx.compose.ui.tooling)

    // Declared so the app ships the same coroutines version the tests compile against
    // (otherwise AndroidX pulls an older one and device tests fail with NoSuchMethodError).
    implementation(libs.kotlinx.coroutines.android)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.sqlcipher.android)
    implementation(libs.androidx.biometric)
    // biometric 1.1.0 pulls in fragment 1.2.x, whose FragmentActivity rejects the
    // request codes used by the Activity Result API (file pickers crash). Pin a current one.
    implementation(libs.androidx.fragment)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.datastore.preferences)

    testImplementation(libs.junit)
    // Android's org.json is only a stub in JVM unit tests; use the real library there.
    testImplementation(libs.org.json)
    // Robolectric runs Android code (Room, SharedPreferences, resources) on the JVM.
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.androidx.room.testing)

    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    // Compose testing brings Espresso 3.5, which calls an input API removed in newer Android.
    androidTestImplementation(libs.androidx.test.espresso.core)
    // Stubs the system file pickers in UI tests (import, backup).
    androidTestImplementation(libs.androidx.test.espresso.intents)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
