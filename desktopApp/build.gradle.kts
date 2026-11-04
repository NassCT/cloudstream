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
            implementation(libs.coil.network.ktor3)
            implementation(libs.ktor.client.java)
            implementation(libs.newpipeextractor) // YouTube extension and trailers

            // Converts Android extensions (.cs3 -> classes.dex) into JVM bytecode
            implementation(libs.dex2jar.translator)
        }
    }
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
            packageVersion = "1.0.0"
            description = "CloudStream for Windows"
            vendor = "CloudStream Plus"
            // Modules needed at runtime by the bundled JRE (networking, scripting, crypto, ...)
            includeAllModules = true

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
