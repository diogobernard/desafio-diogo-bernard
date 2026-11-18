plugins {
	id("org.jetbrains.kotlin.jvm")
	id("org.jetbrains.kotlinx.kover")
	id("dev.detekt")
	id("org.jlleitschuh.gradle.ktlint")
}

detekt {
	config.setFrom(files(rootProject.file("config/detekt/detekt.yml")))
	buildUponDefaultConfig = true
}

kotlin {
	jvmToolchain(21)
	compilerOptions {
		freeCompilerArgs.addAll("-Xjsr305=strict", "-Xannotation-default-target=param-property")
	}
}

tasks.withType<Test>().configureEach {
	useJUnitPlatform()
}

ktlint {
	version.set("1.8.0")
}

kover {
	currentProject {
		sources {
			includedSourceSets.add("main")
		}
		instrumentation {
			disabledForTestTasks.addAll("e2eTest", "loadTest")
		}
	}
	reports {
		filters {
			excludes {
				classes(
					"com.itau.desafio_diogo_bernard.Application",
					"com.itau.desafio_diogo_bernard.ApplicationKt",
					"com.itau.desafio_diogo_bernard.usecase.validations.OrderInputValidator\$Companion",
					"com.itau.desafio_diogo_bernard.gateway.InvoiceReadGateway\$DefaultImpls",
					"com.itau.desafio_diogo_bernard.entrypoint.processamento.ProcessingFailureRecoverer*Companion",
					"com.itau.desafio_diogo_bernard.entrypoint.notas.InvoiceController*Companion",
					"com.itau.desafio_diogo_bernard.gateway.postgres.ReadPostgresGateway*Companion",
					"com.itau.desafio_diogo_bernard.gateway.postgres.OutboxEventSender*Companion",
					"com.itau.desafio_diogo_bernard.config.ConsumerErrorHandlerFactory*Companion",
					"com.itau.desafio_diogo_bernard.config.KafkaConfig*Companion",
					"com.itau.desafio_diogo_bernard.config.BusinessMetrics*Companion",
					"com.itau.desafio_diogo_bernard.entrypoint.solicitacoes.IssuanceRequestController*Companion",
				)
				if (project.name == "app") {
					classes(
						"com.itau.desafio_diogo_bernard.model.*",
						"com.itau.desafio_diogo_bernard.model.**",
						"com.itau.desafio_diogo_bernard.usecase.*",
						"com.itau.desafio_diogo_bernard.usecase.**",
						"com.itau.desafio_diogo_bernard.exception.*",
						"com.itau.desafio_diogo_bernard.exception.**",
					)
				}
			}
		}
		verify {
			rule {
				minBound(100)
				minBound(100, kotlinx.kover.gradle.plugin.dsl.CoverageUnit.BRANCH)
			}
		}
	}
}

val testSourceSet = extensions.getByType<org.gradle.api.tasks.SourceSetContainer>()

fun registerTaggedTestSuite(taskName: String, tag: String) {
	val sourceSetName = taskName.removeSuffix("Test")
	val sourceSet = testSourceSet.create(sourceSetName)
	val mainOutput = testSourceSet.getByName("main").output
	sourceSet.compileClasspath += mainOutput
	sourceSet.runtimeClasspath += mainOutput
	configurations.named("${sourceSetName}Implementation") {
		extendsFrom(configurations.getByName("testImplementation"))
	}
	configurations.named("${sourceSetName}CompileOnly") {
		extendsFrom(configurations.getByName("testCompileOnly"))
	}
	configurations.named("${sourceSetName}RuntimeOnly") {
		extendsFrom(configurations.getByName("testRuntimeOnly"))
	}
	tasks.register<Test>(taskName) {
		testClassesDirs = sourceSet.output.classesDirs
		classpath = sourceSet.runtimeClasspath
		useJUnitPlatform { includeTags(tag) }
	}
}

registerTaggedTestSuite("integrationTest", "integration")
registerTaggedTestSuite("e2eTest", "e2e")
registerTaggedTestSuite("loadTest", "load")

tasks.named<Test>("test") {
	useJUnitPlatform {
		excludeTags("integration", "e2e", "load")
	}
}
