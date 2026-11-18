plugins {
	`kotlin-dsl`
}

java {
	toolchain {
		languageVersion.set(JavaLanguageVersion.of(21))
	}
}

dependencies {
	implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:${libs.versions.kotlin.get()}")
	implementation("org.jetbrains.kotlin:kotlin-allopen:${libs.versions.kotlin.get()}")
	implementation("org.springframework.boot:org.springframework.boot.gradle.plugin:${libs.versions.spring.boot.get()}")
	implementation("io.spring.dependency-management:io.spring.dependency-management.gradle.plugin:${libs.versions.spring.dependency.management.get()}")
	implementation("org.jetbrains.kotlinx:kover-gradle-plugin:${libs.versions.kover.get()}")
	implementation("dev.detekt:dev.detekt.gradle.plugin:${libs.versions.detekt.get()}")
	implementation("org.jlleitschuh.gradle:ktlint-gradle:${libs.versions.ktlint.plugin.get()}")
}
