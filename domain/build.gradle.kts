plugins {
    id("convention.kotlin-jvm")
}

dependencies {
    testImplementation(libs.kotlin.test.junit5)
    testRuntimeOnly(libs.junit.platform.launcher)
}
