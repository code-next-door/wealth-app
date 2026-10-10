// Top-level build file. Plugins are declared here (not applied) so every
// module uses the same versions from gradle/libs.versions.toml.

// Security fixes for libraries the build tools (AGP) use on the build machine.
// None of these is in the app; GitHub's Dependabot flags the older versions.
// Remove a line once AGP brings that version (or newer) itself:
// `./gradlew buildEnvironment` shows what it asks for.
buildscript {
    dependencies {
        constraints {
            classpath("org.bouncycastle:bcprov-jdk18on:1.85")
            classpath("org.bouncycastle:bcpkix-jdk18on:1.85")
            classpath("org.bouncycastle:bcutil-jdk18on:1.85")
            classpath("org.bitbucket.b_c:jose4j:0.9.6")
            classpath("org.jdom:jdom2:2.0.6.1")
            classpath("org.apache.commons:commons-lang3:3.18.0")
            classpath("org.apache.httpcomponents:httpclient:4.5.14")
        }
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.test) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
}

// Lint downloads its own copy of some of the same libraries when it runs
// (configuration `androidLintTool` in each module), which the block above
// doesn't reach. Same fixes there; check with
// `./gradlew :app:dependencies --configuration androidLintTool`.
val lintToolFixes = listOf(
    "org.bouncycastle:bcprov-jdk18on:1.85",
    "org.bouncycastle:bcpkix-jdk18on:1.85",
    "org.bouncycastle:bcutil-jdk18on:1.85",
    "org.apache.commons:commons-lang3:3.18.0",
    "org.apache.httpcomponents:httpclient:4.5.14",
)
subprojects {
    val constraintsHere = dependencies.constraints
    configurations.matching { it.name == "androidLintTool" }.configureEach {
        lintToolFixes.forEach { dependencyConstraints.add(constraintsHere.create(it)) }
    }
}
