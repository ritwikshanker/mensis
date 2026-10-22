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
        // Derived from the git tag in build step 11; fixed until then.
        versionCode = 1
        versionName = "0.1.0-dev"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
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
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}

tasks.withType<Test> {
    useJUnitPlatform()
}
