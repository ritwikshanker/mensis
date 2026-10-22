import java.net.URI

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.detekt)
}

/**
 * Downloads the bundled fonts (Roboto Flex, Source Serif 4; SIL OFL 1.1) and the Material Symbols
 * Rounded icons listed in material-symbols.txt (Apache-2.0) into the build directory on the first
 * build. Keeps generated assets out of the repository; needs network once.
 */
abstract class FetchDesignAssets : DefaultTask() {
    @get:InputFile
    abstract val iconList: RegularFileProperty

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun fetch() {
        val res = outputDir.get().asFile
        val fonts = res.resolve("font").apply { mkdirs() }
        val drawables = res.resolve("drawable").apply { mkdirs() }
        val raw = res.resolve("raw").apply { mkdirs() }
        val fontBase = "https://raw.githubusercontent.com/google/fonts/main/ofl"
        val robotoFlex =
            "RobotoFlex%5BGRAD%2CXOPQ%2CXTRA%2CYOPQ%2CYTAS%2CYTDE%2CYTFI%2CYTLC%2CYTUC" +
                "%2Copsz%2Cslnt%2Cwdth%2Cwght%5D.ttf"
        download("$fontBase/robotoflex/$robotoFlex", fonts.resolve("roboto_flex.ttf"))
        download("$fontBase/robotoflex/OFL.txt", raw.resolve("ofl_roboto_flex.txt"))
        download("$fontBase/sourceserif4/SourceSerif4%5Bopsz%2Cwght%5D.ttf", fonts.resolve("source_serif_4.ttf"))
        download("$fontBase/sourceserif4/OFL.txt", raw.resolve("ofl_source_serif_4.txt"))
        // The icons' and most libraries' licence, shown under About → Open-source libraries.
        download("https://www.apache.org/licenses/LICENSE-2.0.txt", raw.resolve("apache_2_0.txt"))
        val iconBase = "https://raw.githubusercontent.com/google/material-design-icons/master/symbols/android"
        iconList
            .get()
            .asFile
            .readLines()
            .map { it.substringBefore('#').trim() }
            .filter { it.isNotEmpty() }
            .forEach { line ->
                val name = line.substringBefore('|')
                downloadIcon(
                    "$iconBase/$name/materialsymbolsrounded/${name}_24px.xml",
                    drawables.resolve("ms_$name.xml"),
                )
                if (line.endsWith("|fill")) {
                    downloadIcon(
                        "$iconBase/$name/materialsymbolsrounded/${name}_fill1_24px.xml",
                        drawables.resolve("ms_${name}_fill.xml"),
                    )
                }
            }
    }

    private fun download(
        url: String,
        target: File,
    ) {
        if (target.exists() && target.length() > 0) return
        val tmp = File(target.path + ".tmp")
        URI(url).toURL().openStream().use { input -> tmp.outputStream().use { input.copyTo(it) } }
        check(tmp.renameTo(target)) { "could not write ${target.name}" }
    }

    /** Vector drawables from the icons repo carry android:tint; it is removed so Compose's Icon tint is the only colouring. */
    private fun downloadIcon(
        url: String,
        target: File,
    ) {
        if (target.exists() && target.length() > 0) return
        val tmp = File(target.path + ".tmp")
        download(url, tmp)
        target.writeText(tmp.readText().replace(Regex(" android:tint=\"[^\"]*\""), ""))
        tmp.delete()
    }
}

val fetchDesignAssets by tasks.registering(FetchDesignAssets::class) {
    iconList.set(layout.projectDirectory.file("material-symbols.txt"))
    outputDir.set(layout.buildDirectory.dir("generated/designAssets/res"))
}

/** Bundles the repository's LICENSE and THIRD_PARTY_NOTICES.md as raw resources for the About screens. */
abstract class CopyLegalTexts : DefaultTask() {
    @get:InputFile
    abstract val licence: RegularFileProperty

    @get:InputFile
    abstract val notices: RegularFileProperty

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun copy() {
        val raw =
            outputDir
                .get()
                .asFile
                .resolve("raw")
                .apply { mkdirs() }
        licence.get().asFile.copyTo(raw.resolve("licence_mit.txt"), overwrite = true)
        notices.get().asFile.copyTo(raw.resolve("third_party_notices.md"), overwrite = true)
    }
}

val copyLegalTexts by tasks.registering(CopyLegalTexts::class) {
    licence.set(rootProject.layout.projectDirectory.file("LICENSE"))
    notices.set(rootProject.layout.projectDirectory.file("THIRD_PARTY_NOTICES.md"))
    outputDir.set(layout.buildDirectory.dir("generated/legalTexts/res"))
}

/** Output of a git command in the project directory; empty when git fails or is not installed (source archive). */
fun git(vararg args: String): String =
    runCatching {
        providers
            .exec {
                commandLine("git", *args)
                isIgnoreExitValue = true
            }.standardOutput.asText
            .get()
            .trim()
    }.getOrDefault("")

data class AppVersion(
    val name: String,
    val code: Int,
)

/**
 * Release versions come from the tag on HEAD: `vX.Y.Z` gives versionName `X.Y.Z` and versionCode
 * `X * 10000 + Y * 100 + Z` (RELEASING.md). Every other build is `0.0.0-dev+<commit>` with versionCode 1,
 * so a development build never looks newer than a release.
 */
val appVersion: AppVersion =
    Regex("""v(\d+)\.(\d+)\.(\d+)""")
        .matchEntire(git("describe", "--tags", "--exact-match", "--match", "v*"))
        ?.destructured
        ?.toList()
        ?.map(String::toInt)
        ?.let { (major, minor, patch) ->
            require(minor < 100 && patch < 100) { "minor and patch must stay below 100 for the versionCode scheme" }
            val code = major * 10_000 + minor * 100 + patch
            require(code >= 1) { "v0.0.0 has no valid versionCode" }
            AppVersion("$major.$minor.$patch", code)
        }
        ?: git("rev-parse", "--short=7", "HEAD").let { commit ->
            AppVersion(if (commit.isEmpty()) "0.0.0-dev" else "0.0.0-dev+$commit", 1)
        }

/** Prints "versionName versionCode"; the release workflow compares it with the pushed tag. */
val printVersion by tasks.registering {
    val name = appVersion.name
    val code = appVersion.code
    doLast { println("$name $code") }
}

/**
 * The upload key signs release builds only when the release workflow provides it through the
 * environment (RELEASING.md); it never sits in the repository or in a properties file. Without it,
 * release builds are unsigned, as in CI and for the GitHub-release APK, which is signed locally.
 */
val uploadKeystore: String? = providers.environmentVariable("MENSIS_UPLOAD_KEYSTORE").orNull

// detekt 1.23.x embeds an older Kotlin compiler that rejects the daemon's JDK 25 as a JVM target.
tasks.withType<io.gitlab.arturbosch.detekt.Detekt>().configureEach {
    jvmTarget = "17"
}
tasks.withType<io.gitlab.arturbosch.detekt.DetektCreateBaselineTask>().configureEach {
    jvmTarget = "17"
}

detekt {
    config.setFrom(rootProject.files("config/detekt/detekt.yml"))
    buildUponDefaultConfig = true
}

android {
    namespace = "com.deutschdreamers.mensis"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.deutschdreamers.mensis"
        minSdk = 26
        targetSdk = 37
        versionCode = appVersion.code
        versionName = appVersion.name

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (uploadKeystore != null) {
            create("upload") {
                storeFile = file(uploadKeystore)
                storePassword = providers.environmentVariable("MENSIS_UPLOAD_KEYSTORE_PASSWORD").get()
                keyAlias = providers.environmentVariable("MENSIS_UPLOAD_KEY_ALIAS").get()
                keyPassword = providers.environmentVariable("MENSIS_UPLOAD_KEY_PASSWORD").get()
            }
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.findByName("upload")
            // Full R8 (no packageScope: scoping would leave our own Log calls in place, see rules.keep).
            optimization {
                enable = true
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

androidComponents {
    onVariants { variant ->
        variant.sources.res?.addGeneratedSourceDirectory(fetchDesignAssets, FetchDesignAssets::outputDir)
        variant.sources.res?.addGeneratedSourceDirectory(copyLegalTexts, CopyLegalTexts::outputDir)
    }
}

dependencies {
    implementation(project(":backup-drive"))

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.androidx.biometric)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)
    implementation(libs.androidx.hilt.work)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)

    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.espresso.core)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.core)
    // Tests give stores their own preference files; the app's singletons hold the real ones open.
    androidTestImplementation(libs.androidx.datastore.preferences)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}

tasks.withType<Test> {
    useJUnitPlatform()
}
