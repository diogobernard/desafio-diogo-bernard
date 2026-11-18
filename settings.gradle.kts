rootProject.name = "desafio-diogo-bernard"

pluginManagement {
	repositories {
		gradlePluginPortal()
		mavenCentral()
	}
}

dependencyResolutionManagement {
	repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
	repositories {
		mavenCentral()
	}
}

includeBuild("build-logic")
include("app", "domain")
