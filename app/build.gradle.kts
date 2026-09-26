plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "io.github.codenextdoor.wealth"
    compileSdk = 36

    defaultConfig {
        applicationId = "io.github.codenextdoor.wealth"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"

        // Starts the app with an in-memory database so on-device tests never
        // touch real data.
        testInstrumentationRunner = "io.github.codenextdoor.wealth.WealthTestRunner"
    }

    sourceSets {
        // Room's migration tests read the exported schema history.
        getByName("androidTest").assets.srcDir("$projectDir/schemas")
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
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
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.sqlite)
    implementation(libs.sqlcipher.android)
    implementation(libs.pdfbox.android)
    implementation(libs.androidx.biometric)
    // biometric 1.1.0 pulls in fragment 1.2.x, whose FragmentActivity rejects the
    // request codes used by the Activity Result API (file pickers crash). Pin a current one.
    implementation(libs.androidx.fragment)
    implementation(libs.androidx.lifecycle.process)

    testImplementation(libs.junit)
    // Desktop PDFBox (same 2.0.27 code as PdfBox-Android) to test PDF text extraction on the JVM.
    testImplementation(libs.pdfbox)
    // Android's org.json is only a stub in JVM unit tests; use the real library there.
    testImplementation(libs.org.json)
    // Robolectric runs Android code (Room, SharedPreferences, resources) on the JVM.
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.kotlinx.coroutines.test)

    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.room.testing)
    androidTestImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
