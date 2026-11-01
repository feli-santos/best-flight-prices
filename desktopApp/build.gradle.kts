plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.compose)
}
kotlin { jvmToolchain(21) }
dependencies {
    implementation(project(":ui"))
    implementation(project(":client"))
    implementation(compose.desktop.currentOs)
    implementation(libs.compose.material3)
    implementation(libs.ktor.client.okhttp)
    // ViewModel scopes dispatch on Dispatchers.Main; on the JVM that provider lives here.
    implementation(libs.kotlinx.coroutines.swing)
    testImplementation(kotlin("test"))
}
tasks.test { useJUnitPlatform() }
compose.desktop {
    application {
        mainClass = "bfp.desktop.MainKt"
        jvmArgs("-Dbfp.catalog=${rootProject.layout.projectDirectory.dir("data/catalog").asFile}", "-Dbfp.local=${rootProject.layout.projectDirectory.dir(".local").asFile}")
    }
}
