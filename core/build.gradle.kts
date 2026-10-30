plugins { alias(libs.plugins.kotlin.multiplatform) }
kotlin {
    jvm()
    jvmToolchain(21)
    sourceSets {
        commonMain.dependencies { api(libs.kotlinx.datetime) }
        commonTest.dependencies { implementation(kotlin("test")) }
    }
}
