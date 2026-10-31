plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.compose)
}
val syncCatalog by tasks.registering(Sync::class) {
    from(rootProject.layout.projectDirectory.dir("data/catalog"))
    into(layout.buildDirectory.dir("generated/catalogResources/files"))
}
compose.resources {
    publicResClass = false
    packageOfResClass = "bfp.ui.generated"
    customDirectory(sourceSetName = "commonMain", directoryProvider = syncCatalog.map { it.destinationDir.parentFile.let { d -> layout.dir(provider { d }).get() } })
}
kotlin {
    jvm("desktop")
    jvmToolchain(21)
    sourceSets {
        commonMain.dependencies {
            api(project(":client"))
            implementation(project(":core"))
            implementation(libs.compose.resources)
            implementation(libs.compose.runtime)
            implementation(libs.compose.runtime.saveable)
            implementation(libs.compose.foundation)
            implementation(libs.compose.ui)
            implementation(libs.compose.material3)
            implementation(libs.lifecycle.viewmodel.compose)
            implementation(libs.kotlinx.serialization.json)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
        }
        val desktopTest by getting {
            dependencies {
                implementation(libs.compose.ui.test)
                implementation(compose.desktop.currentOs)
                implementation(libs.compose.ui.test.junit4)
            }
        }
    }
}

tasks.withType<Test>().configureEach { System.getProperty("bfp.gallery")?.let { systemProperty("bfp.gallery", it) } }
