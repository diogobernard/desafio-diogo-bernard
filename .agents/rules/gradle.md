# Regras para Gradle

## Escopo

Aplicar a `../../build.gradle.kts`, `settings.gradle.kts`, `gradle.properties`, arquivos em `gradle/` e lógica de build. Para Kotlin em plugins ou scripts, consultar também `kotlin.md`. Para o plugin Asciidoctor e a geração de snippets da documentação da API, consultar também `spring-rest-docs.md`.

Os exemplos são trechos ilustrativos, não instruções para substituir o build inteiro. Preservar os plugins e versões já configurados no projeto.

## Wrapper e compatibilidade

- Executar tarefas com `../../gradlew` ou `gradlew.bat`, mantendo a versão definida pelo projeto.
- Manter os scripts, o JAR e as propriedades do Wrapper no controle de versão.
- Atualizar o Wrapper pela tarefa `wrapper`, com versão explícita. Executá-la novamente com a nova distribuição para atualizar todos os arquivos e revisar o resultado.
- Em uma atualização solicitada, avaliar a versão menor mais recente compatível e consultar os guias de migração de Gradle e plugins.
- Validar a combinação de Gradle, JDK, Kotlin e Spring Boot antes de alterar versões.
- Preservar a toolchain Java e alinhar o alvo JVM de Kotlin e Java. Não depender apenas do JDK instalado na máquina.

Exemplo de toolchain em `../../build.gradle.kts`, usando o nível Java atualmente adotado pelo projeto:

```kotlin
java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(26)
    }
}
```

Exemplo de atualização do Wrapper, apenas quando a atualização fizer parte da tarefa. Substituir o marcador por uma versão previamente validada:

```sh
./gradlew wrapper --gradle-version <versao-validada>
./gradlew wrapper --gradle-version <versao-validada>
```

A segunda execução utiliza a distribuição atualizada e regenera os arquivos do Wrapper.

## Organização e APIs

- Usar Kotlin DSL e aplicar plugins pelo bloco `plugins {}`.
- Manter `rootProject.name` explícito em `../../settings.gradle.kts`.
- Registrar flags compartilhadas em `gradle.properties` na raiz; não criar esse arquivo em subprojetos nem repetir valores padrão sem necessidade.
- Usar APIs públicas. Avaliar estabilidade e compatibilidade antes de adotar APIs `@Incubating`.
- Evitar `afterEvaluate`, dependências da ordem de aplicação de plugins e configuração cruzada com `allprojects`/`subprojects`. Quando necessário, reagir à aplicação com `pluginManager.withPlugin`.
- Extrair convenções reutilizadas para plugins de convenção quando houver repetição real.
- Em classes auxiliares e plugins, usar `Logging.getLogger(MinhaClasse::class.java)`; em tarefas e scripts, usar o `logger` disponível no contexto.

Exemplo de aplicação declarativa de um plugin nativo em `../../build.gradle.kts`:

Evitar como padrão em scripts de build:

```kotlin
apply(plugin = "java")
```

Preferir, substituindo a aplicação acima:

```kotlin
plugins {
    java
}
```

Exemplo de identidade explícita em `../../settings.gradle.kts`:

```kotlin
rootProject.name = "study-kubernetes"
```

Exemplo de configuração que reage à presença de um plugin em lógica de convenção, sem `afterEvaluate`:

```kotlin
pluginManager.withPlugin("java") {
    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
    }
}
```

## Dependências e repositórios

- Declarar cada dependência na configuração adequada: `implementation`, `compileOnly`, `runtimeOnly`, `testImplementation` ou `testRuntimeOnly`. Usar `api` apenas em módulos de biblioteca quando a dependência integrar sua API pública.
- Preferir versões explícitas ou gerenciadas por plataforma/BOM; evitar seletores dinâmicos como `+` e `latest.release`.
- Preservar o gerenciamento de dependências do Spring Boot e justificar qualquer sobrescrita de versão.
- Usar catálogos de versões quando houver centralização útil; eles não substituem o alinhamento de versões fornecido por plataformas.
- Manter somente repositórios necessários e confiáveis. Evitar `mavenLocal()` no build compartilhado, pois seu conteúdo varia por máquina.
- Em projetos com vários módulos, centralizar repositórios em `dependencyResolutionManagement` e separar repositórios de plugins em `pluginManagement`.
- Investigar conflitos com `dependencies` e `dependencyInsight`; evitar exclusões amplas ou versões forçadas sem identificar a causa.
- Fornecer credenciais externamente, sem gravá-las nos scripts ou em arquivos versionados.

Exemplo com dependências já usadas no projeto, cujas versões são fornecidas pelo gerenciamento existente:

```kotlin
repositories {
    mavenCentral()
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
```

Exemplo de diagnóstico de uma dependência de produção:

```sh
./gradlew dependencies --configuration runtimeClasspath
./gradlew dependencyInsight --dependency kotlin-reflect --configuration runtimeClasspath
```

## Tarefas e configuração preguiçosa

- Criar tarefas com `tasks.register` e configurar tarefas existentes com `tasks.named` ou `tasks.withType<T>().configureEach`.
- Evitar `tasks.create`, `getByName`, `tasks.all` e chamadas antecipadas a `TaskProvider.get()`.
- Manter referências como `TaskProvider`; passar essas referências às dependências entre tarefas sem materializá-las antecipadamente.
- Alterar somente a tarefa correspondente dentro de sua ação de configuração; configurar outras tarefas em suas próprias ações.
- Usar `Provider` e `Property` para conectar valores sem resolvê-los antes do necessário.
- Separar configuração de execução: processamento e efeitos colaterais pertencem às ações das tarefas.
- Declarar entradas e saídas de tarefas customizadas para permitir execução incremental correta. Habilitar cache somente para tarefas adequadas a esse mecanismo.

Exemplo de configuração preguiçosa de testes em `../../build.gradle.kts`:

Evitar a sobrecarga que configura tarefas antecipadamente:

```kotlin
tasks.withType<Test> {
    useJUnitPlatform()
}
```

Preferir, substituindo o bloco anterior:

```kotlin
tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
```

Exemplo de registro e dependência entre tarefas com `TaskProvider`. A tarefa nativa `Copy` já modela suas entradas e saídas; o diretório de destino permanece um provider:

```kotlin
val copiarRecursos = tasks.register<Copy>("copiarRecursos") {
    from(layout.projectDirectory.dir("src/main/resources"))
    into(layout.buildDirectory.dir("distribuicao/recursos"))
}

tasks.register("prepararDistribuicao") {
    group = "distribution"
    description = "Prepara os recursos da distribuição."
    dependsOn(copiarRecursos) // Sem chamar copiarRecursos.get().
}
```

## Verificação

- Após alterar scripts, verificar a configuração com `./gradlew help --warning-mode=all` e executar as tarefas afetadas.
- Para mudanças que atinjam compilação ou dependências, executar `./gradlew build`; para testes, usar `./gradlew test` ou um filtro específico.
- Validar compatibilidade antes de habilitar configuration cache ou outras opções de desempenho.
- Evitar `clean` em toda execução, pois remove resultados aproveitados pelo build incremental.
- Informar os comandos executados e eventuais limitações de ambiente; não afirmar que o build passou sem verificar o resultado.

Exemplos de comandos para selecionar conforme a alteração:

```sh
./gradlew help --warning-mode=all
./gradlew test --tests 'com.db.project_mister_study.ApplicationTests'
./gradlew build
```

## Referências

- [Boas práticas gerais do Gradle](https://docs.gradle.org/current/userguide/best_practices_general.html).
- [Gradle Wrapper](https://docs.gradle.org/current/userguide/gradle_wrapper.html).
- [Boas práticas para dependências](https://docs.gradle.org/current/userguide/best_practices_dependencies.html).
- [Configuração preguiçosa de tarefas](https://docs.gradle.org/current/userguide/task_configuration_avoidance.html).

A documentação em `current` acompanha a versão publicada do Gradle. Antes de usar uma API, confirmar sua disponibilidade na versão do Wrapper deste projeto.
