import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    kotlin("jvm") version "2.2.20"
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.20"
    id("org.jetbrains.compose") version "1.9.3"
}

group = "com.neontokyo"

// "0.2.0-nt.260" from CI; MSI versions must be plain numbers, so the build number goes last
val neonVersion: String = (findProperty("neonVersion") as String?) ?: "0.2.0-dev"
val buildNumber: String = (findProperty("buildNumber") as String?) ?: "1"
version = neonVersion

kotlin {
    jvmToolchain(21)
}

// The shared core's Kotlin interface and native library, made by build-core.sh
sourceSets.main {
    kotlin.srcDir("build/generated/uniffi")
    resources.srcDir("build/generated/native")
    resources.srcDir("build/generated/fonts")
}

val copyFonts by tasks.registering(Copy::class) {
    from("../app/src/main/res/font") { include("kanit_*.ttf") }
    into("build/generated/fonts/fonts")
}
tasks.processResources { dependsOn(copyFonts) }

val writeVersion by tasks.registering {
    val out = layout.buildDirectory.file("generated/native/neon-version.txt")
    outputs.file(out)
    doLast { out.get().asFile.apply { parentFile.mkdirs(); writeText(neonVersion) } }
}
tasks.processResources { dependsOn(writeVersion) }

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(compose.materialIconsExtended)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.10.2")
    implementation("net.java.dev.jna:jna:5.17.0")
    testImplementation(kotlin("test"))
    testImplementation(compose.desktop.uiTestJUnit4)
}

compose.desktop {
    application {
        mainClass = "com.neontokyo.desktop.MainKt"
        jvmArgs += listOf("-Dsun.java2d.uiScale.enabled=true")
        nativeDistributions {
            targetFormats(TargetFormat.Msi, TargetFormat.Exe)
            packageName = "Neon Tokyo"
            packageVersion = "0.2.$buildNumber"
            description = "Neon Tokyo: films and series from your Stremio addons"
            vendor = "Neon Tokyo"
            modules("java.net.http", "jdk.crypto.ec", "java.naming")
            windows {
                iconFile.set(project.file("art/neon-tokyo.ico"))
                menuGroup = "Neon Tokyo"
                shortcut = true
                dirChooser = true
                perUserInstall = true
                // a fixed id so a new version installs over the old one
                upgradeUuid = "7d9a3c2e-5b1f-4e8a-9c6d-2f4b8e1a7c30"
            }
        }
    }
}
