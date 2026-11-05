import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
}

kotlin {
    jvm {}
    sourceSets {
        all {
            languageSettings {
                optIn("com.lagradost.cloudstream3.InternalAPI")
                optIn("com.lagradost.cloudstream3.Prerelease")
            }
        }
        jvmMain.dependencies {
            implementation(libs.bundles.compose)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.coroutines.swing)
            implementation(libs.kotlinx.collections.immutable)
            implementation(compose.desktop.currentOs) {
                // compose.desktop.currentOs imports the wrong material 2, so we exclude it
                exclude(group = "org.jetbrains.compose.material", module = "material")
            }
            implementation(project(":shared"))
            implementation(project(":library"))

            // Used directly by the desktop plugin/repository manager
            implementation(libs.jackson.module.kotlin)
            implementation(libs.nicehttp)
            implementation(libs.jsoup) // HTML plots/descriptions to text
            implementation(libs.coil.network.okhttp)
            implementation(libs.newpipeextractor) // YouTube extension and trailers
            implementation(libs.vlcj) // Built-in player (libVLC 3)

            // Converts Android extensions (.cs3 -> classes.dex) into JVM bytecode
            implementation(libs.dex2jar.translator)

            // Bundled by the Android app and used directly by some extensions (e.g. HiAnime)
            implementation("com.google.code.gson:gson:2.11.0")
        }
    }
}

compose.resources {
    publicResClass = false
    packageOfResClass = "com.lagradost.cloudstream4.desktop.res"
    generateResClass = always
}

// ---- Bundle libVLC (LGPL) with the app so the built-in player works without installing VLC.
// Taken from an installed VLC 3 (VLC_DIR, or the default install folder). Skipped if not found.
val bundledVlcRoot = layout.buildDirectory.dir("vlc-resources")
val prepareVlc by tasks.registering(Sync::class) {
    description = "Copies libVLC and its plugins into the app resources"
    // Locals only: lambdas must not capture the build script (configuration cache)
    val vlcSourceDir = File(System.getenv("VLC_DIR") ?: "C:/Program Files/VideoLAN/VLC")
    val hasVlc = vlcSourceDir.resolve("libvlc.dll").isFile
    onlyIf { hasVlc }
    from(vlcSourceDir) {
        include("libvlc.dll", "libvlccore.dll", "COPYING.txt", "plugins/**")
        // Not needed by an embedded player: VLC's own UI, visualizations, scripting, control interfaces
        exclude(
            "plugins/gui/**", "plugins/visualization/**", "plugins/lua/**", "plugins/control/**",
            "plugins/stream_out/**", "plugins/access_output/**", "plugins/services_discovery/**", "plugins/plugins.dat",
        )
    }
    into(bundledVlcRoot.map { it.dir("windows/vlc") })
}

// java.lang.System::load has been called by org.jetbrains.skiko.LibraryLoader in an unnamed module
tasks.withType<JavaExec> {
    jvmArgs("--enable-native-access=ALL-UNNAMED")
}

compose.desktop {
    application {
        mainClass = "com.lagradost.cloudstream4.MainKt"
        jvmArgs += listOf("--enable-native-access=ALL-UNNAMED", "-Xmx1g")

        nativeDistributions {
            targetFormats(TargetFormat.Exe, TargetFormat.Msi, TargetFormat.Dmg, TargetFormat.Deb)
            packageName = "CloudStreamPlus"
            packageVersion = "1.0.1"
            description = "CloudStream for Windows"
            vendor = "CloudStream Plus"
            // Modules needed at runtime by the bundled JRE (networking, scripting, crypto, ...)
            includeAllModules = true
            // libVLC for the built-in player, see prepareVlc
            appResourcesRootDir.set(bundledVlcRoot)

            val iconsRoot = project.file("src/desktop-icons")
            windows {
                iconFile.set(iconsRoot.resolve("icon-windows.ico"))
                menuGroup = "CloudStream Plus"
                shortcut = true
                dirChooser = true
                perUserInstall = true
                upgradeUuid = "6f1d2a8e-3c4b-4e0a-9b7d-5a2f1c8e9d40"
            }
            linux {
                iconFile.set(iconsRoot.resolve("icon-linux.png"))
            }
        }
    }
}

tasks.matching { it.name == "prepareAppResources" }.configureEach { dependsOn(prepareVlc) }
