# Tarefa 1.0: Build multimódulo e estrutura

## Visão geral

Transformar o projeto de módulo único em `app` e `domain`, com dependência `app → domain`, catálogo de versões, plugin de convenção em `build-logic`, Kover (100% de linhas e branches por módulo), detekt, ktlint e as tarefas `integrationTest`, `e2eTest` e `loadTest` separadas por tag. Renomear o ponto de entrada para `Application.kt`. Todas as demais tarefas dependem desta base.

<skills>
### Conformidade com skills

- `executar-task`: implementação incremental desta tarefa.
</skills>

<rules>
### Conformidade com o AGENTS.md e as rules

Lidos o `AGENTS.md` e todas as rules em `.agents/rules/`. Relevantes:

- `gradle.md`: Kotlin DSL, bloco `plugins {}`, `rootProject.name` explícito, `tasks.register`/`configureEach`, sem `allprojects`/`subprojects`/`afterEvaluate`, versões pelo BOM do Spring Boot, repositórios centralizados em `dependencyResolutionManagement`, toolchain Java preservada.
- `folder-structure.md`: módulos `app` e `domain`, `Application.kt` como ponto de entrada.
- `unit-tests.md`: cobertura de 100% medida por módulo.
- Desvios registrados na TechSpec: toolchain Java 21 (o `AGENTS.md` cita Java 25); pacote `com.itau.desafio_diogo_bernard` preservado; Kover do `app` agrega `test` e `integrationTest` (aprovado em 2026-11-14).
</rules>

<requirements>
- RF53: base para o CI executar build, testes unitários, de integração e de carga, cobertura mínima e análise estática.
- Kotlin 2.3, Spring Boot 4.1, Java 21 (restrições técnicas do PRD).
- `domain` sem dependência de Spring, JDBC, Kafka ou Jackson.
- Cobertura de 100% de linhas e branches em cada módulo, excluindo somente `Application.kt`.
</requirements>

## Subtarefas

- [x] 1.1 Atualizar `settings.gradle.kts` com `include("app", "domain")`, `pluginManagement` e `dependencyResolutionManagement`.
- [x] 1.2 Criar `gradle/libs.versions.toml` e validar a compatibilidade das versões (Kotlin 2.3.21, Spring Boot 4.1.2, detekt, ktlint, Kover, Testcontainers, MockK, Awaitility, Gatling, springdoc).
- [x] 1.3 Criar `build-logic/` com plugins de convenção (Kotlin/JVM, toolchain 21, Kover, detekt, ktlint e tarefas de teste por tag).
- [x] 1.4 Criar `domain/build.gradle.kts` (biblioteca, sem frameworks) e `app/build.gradle.kts` (Spring Boot, `implementation(project(":domain"))`).
- [x] 1.5 Registrar `test` (sem as tags), `integrationTest` (`integration`), `e2eTest` (`e2e`) e `loadTest` (`load`).
- [x] 1.6 Configurar o Kover: 100% de linhas e branches por módulo, exclusão de `Application.kt` e agregação de `integrationTest` no `app`.
- [x] 1.7 Mover `DesafioDiogoBernardApplication.kt` para `app/.../Application.kt`, `application.yaml` para `app/src/main/resources` e remover o teste de contexto antigo.
- [x] 1.8 Esvaziar o `build.gradle.kts` da raiz e incluir `.env` no `.gitignore`.
- [x] 1.9 Se o detekt não for compatível com Kotlin 2.3.21, registrar a limitação e manter só o ktlint (risco da TechSpec).

## Detalhes de implementação

Ver na TechSpec: "Visão dos componentes" (módulos e pacotes), "Abordagem de testes" (ferramentas, tags e cobertura), "Sequenciamento do desenvolvimento" (item 1), "Riscos conhecidos" (compatibilidade de detekt e ktlint) e "Arquivos relevantes e dependentes".

## Critérios de aceitação relacionados

- CA-29 (parcial: base para o bloqueio de cobertura no CI)

## Testes da tarefa

A TechSpec não define casos de teste para esta tarefa. Validação por comandos:

- [x] `./gradlew help --warning-mode=all` sem avisos de depreciação dos scripts.
- [x] `./gradlew build` e `./gradlew :domain:test :app:test` executando.
- [x] `./gradlew koverVerify` configurado com a meta de 100% por módulo.
- [x] `./gradlew detekt ktlintCheck` (ou só `ktlintCheck`, se o detekt for incompatível).

## Arquivos relevantes

- `settings.gradle.kts`, `build.gradle.kts`, `.gitignore`
- `gradle/libs.versions.toml`
- `build-logic/**`
- `domain/build.gradle.kts`, `app/build.gradle.kts`
- `app/src/main/kotlin/com/itau/desafio_diogo_bernard/Application.kt`
- `app/src/main/resources/application.yaml`
- `src/main/kotlin/com/itau/desafio_diogo_bernard/DesafioDiogoBernardApplication.kt` (removido após mover)
- `src/test/kotlin/com/itau/desafio_diogo_bernard/DesafioDiogoBernardApplicationTests.kt` (removido)
