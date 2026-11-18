import org.gradle.language.jvm.tasks.ProcessResources

plugins {
    id("convention.spring-application")
    alias(libs.plugins.kotlin.jpa)
    alias(libs.plugins.gatling)
    alias(libs.plugins.asciidoctor)
}

val asciidoctorExt: Configuration by configurations.creating
val snippetsDir = layout.buildDirectory.dir("generated-snippets")

asciidoctorj {
    setVersion(libs.versions.asciidoctorj.get())
}

allOpen {
    annotations(
        "jakarta.persistence.Entity",
        "jakarta.persistence.Embeddable",
        "jakarta.persistence.MappedSuperclass",
    )
}

tasks.named<ProcessResources>("processResources") {
    from(rootProject.layout.projectDirectory.dir("migrations/postgresql/notafiscal")) {
        into("db/migration")
    }
}

dependencies {
    implementation(project(":domain"))
    implementation(libs.spring.boot.starter)
    implementation(libs.spring.boot.starter.webmvc)
    implementation(libs.spring.boot.starter.actuator)
    implementation(libs.spring.boot.starter.opentelemetry)
    implementation(libs.spring.boot.starter.kafka)
    implementation(libs.spring.boot.starter.jdbc)
    implementation(libs.spring.boot.starter.data.jpa)
    implementation(libs.spring.boot.starter.flyway)
    implementation(libs.flyway.database.postgresql)
    runtimeOnly(libs.postgresql)
    runtimeOnly(libs.micrometer.registry.prometheus)
    implementation(libs.kotlin.reflect)
    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.spring.boot.webmvc.test)
    testImplementation(libs.spring.boot.restdocs)
    testImplementation(libs.spring.boot.starter.webmvc.test)
    testImplementation(libs.spring.boot.starter.restdocs)
    testImplementation(libs.spring.restdocs.mockmvc)
    asciidoctorExt(libs.spring.restdocs.asciidoctor)
    testImplementation(libs.kotlin.test.junit5)
    testImplementation(libs.testcontainers.postgresql)
    testImplementation(libs.testcontainers.kafka)
    testImplementation(libs.testcontainers.junit.jupiter)
    testImplementation(libs.awaitility)
    testImplementation(libs.opentelemetry.sdk.testing)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.named<Test>("test") {
    outputs.dir(snippetsDir)
}

tasks.named<org.asciidoctor.gradle.jvm.AsciidoctorTask>("asciidoctor") {
    inputs.dir(snippetsDir)
    configurations(asciidoctorExt.name)
    dependsOn(tasks.named("test"))
}

val executarCarga =
    tasks.register<Exec>("executarCarga") {
        group = "verification"
        description = "Sobe o ambiente isolado e executa um cenário Gatling isolado por serviço."
        workingDir = rootProject.projectDir
        val scriptsPorCenario =
            mapOf(
                "combinado" to "scripts/carga.sh",
                "post" to "scripts/carga-post.sh",
                "consumo" to "scripts/carga-consumo.sh",
                "consulta" to "scripts/carga-consulta.sh",
            )
        val cenario = providers.gradleProperty("loadScenario").orNull ?: "combinado"
        val script =
            scriptsPorCenario[cenario]
                ?: throw GradleException("-PloadScenario deve ser um de ${scriptsPorCenario.keys}")
        commandLine("bash", script, "--gradle-invocation")

        providers.gradleProperty("loadRequests").orNull?.let { value ->
            val requests =
                value.toLongOrNull()
                    ?: throw GradleException("-PloadRequests deve ser um inteiro maior ou igual a 1000")
            if (requests < 1_000) {
                throw GradleException("-PloadRequests deve ser maior ou igual a 1000")
            }
            environment("LOAD_REQUESTS", requests.toString())
        }
    }

tasks.named("loadTest") {
    dependsOn(executarCarga)
}
