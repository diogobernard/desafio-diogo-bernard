# Tarefa 10.0: Documentação, CI e correção dos desvios

## Visão geral

Registro original: esta tarefa entregou OpenAPI via springdoc. Em 2026-11-15, esse contrato foi substituído por Spring REST Docs para cumprir `.agents/rules/spring-rest-docs.md`; o restante da entrega (README, ADRs, desenho AWS, plano de deploy, exemplos `.http` e CI) permanece válido. A tarefa também corrigiu o `AGENTS.md` e a `.agents/rules/e2e-tests.md` para PostgreSQL, Kafka, Testcontainers e Java 21.

<skills>
### Conformidade com skills

- `executar-task`: implementação incremental desta tarefa.
- `executar-review`: verificação de conformidade com as rules e a TechSpec, incluindo `koverVerify`, antes de encerrar a funcionalidade.
</skills>

<rules>
### Conformidade com o AGENTS.md e as rules

Lidos o `AGENTS.md` e todas as rules em `.agents/rules/`. Relevantes:

- `AGENTS.md`: não replicar os requisitos do PRD e da TechSpec; registrar só os comandos, portas e tarefas que existem; executar `git diff --check` antes de concluir.
- `logs.md` e `code-standards.md`: exemplos e documentação somente com placeholders (`<SENHA_FICTICIA>`), sem documento real.
- `gradle.md`: comandos documentados conferidos contra as tarefas existentes.
- Desvios a corrigir (decisão do usuário registrada na TechSpec): serviços fornecidos, comandos e toolchain no `AGENTS.md`; ambiente e jornadas na `e2e-tests.md`.
</rules>

<requirements>
- RF52: documentação navegável do contrato HTTP com Spring REST Docs, README, ADRs, exemplos de requisição, relatórios de carga, desenho de arquitetura do entorno e plano de deploy.
- RF53: CI com build, testes unitários, de integração e de carga, cobertura mínima e análise estática.
- Qualidade (objetivos do PRD): CI bloqueando merge abaixo de 100% de linhas e branches em qualquer módulo.
</requirements>

## Subtarefas

- [x] 10.1 (substituída em 2026-11-15) Remover o springdoc e substituir OpenAPI por testes MVC com Spring REST Docs, snippets versionáveis e geração Asciidoctor.
- [x] 10.2 Criar `docs/exemplos/*.http` para os cenários de sucesso e de erro.
- [x] 10.3 Escrever `docs/adr/ADR-001` a `ADR-005` (outbox, numeração, idempotência, réplica com fallback, `JdbcClient`).
- [x] 10.4 Escrever `docs/arquitetura.md` (API Gateway, Cognito, VPC Link, ECS Fargate/EKS, RDS/Aurora com réplica, MSK, ADOT, CDC como evolução) e `docs/deploy.md` (ambientes, blue/green ou canary, migrations *expand/contract*, rollback e checklist).
- [x] 10.5 Escrever o `README.md` (como executar, testar, carregar e consultar os relatórios).
- [x] 10.6 Criar `.github/workflows/ci.yml` com o bloqueio por `koverVerify` e a carga em execução manual ou agendada.
- [x] 10.7 Atualizar o `AGENTS.md` e a `.agents/rules/e2e-tests.md` para PostgreSQL, Kafka, Testcontainers, Java 21 e os comandos reais.
- [x] 10.8 Executar `git diff --check`.

## Detalhes de implementação

Ver na TechSpec: "Endpoints da API", "Pontos de integração" (API Gateway e Cognito), "Configuração por ambiente" (coluna AWS), "Principais decisões" (base dos ADRs), "Conformidade com o AGENTS.md e as rules" (desvios registrados) e "Arquivos relevantes e dependentes".

## Critérios de aceitação relacionados

- CA-29 (bloqueio de merge no CI)

## Testes da tarefa

A TechSpec não define casos de teste para esta tarefa. Validação:

- [x] Pipeline completo aprovado: build, testes unitários, integração, E2E, `detekt`, `ktlintCheck`, smoke do Compose e `koverVerify`.
- [x] `:app:koverVerify` e `:domain:koverVerify` aprovados com 100% de linhas e branches. `app`: 1.095/1.095 linhas e 237/237 branches; `domain`: 488/488 linhas e 228/228 branches. Os relatórios agregam testes unitários e de integração.
- [x] Testes MVC geram snippets do contrato dos três endpoints e do erro de cliente ausente; `:app:asciidoctor` gera `app/build/docs/asciidoc/index.html`.
- [x] Comandos do `README.md` e do `AGENTS.md` conferidos contra as tarefas Gradle existentes.
- [x] `git diff --check` sem erros; também foi feita varredura de whitespace nos arquivos novos, que não são considerados pelo Git enquanto não rastreados.

## Resultado da validação

O pipeline local passou integralmente: `./gradlew help --warning-mode=all build -x koverVerify`; `./gradlew test :app:integrationTest :app:e2eTest detekt ktlintCheck koverHtmlReport`; `bash scripts/smoke-e2e.sh`; e `./gradlew koverVerify`. O gate Kover está em 100% de linhas e branches nos dois módulos. O `detekt` foi configurado para reconhecer o pacote-base obrigatório `com.itau.desafio_diogo_bernard`, e as violações de comprimento de linha foram corrigidas. `git diff --check` passou.

#### Migração REST Docs — validação em 2026-11-15

- `./gradlew help --warning-mode=all`: passou após renomear o alias de versão `kotlin-jpa`, que colidia com o accessor `libs.versions.kotlin` no build-logic.
- `./gradlew :app:ktlintTestSourceSetCheck`: passou para os testes Kotlin.
- Os cinco testes MVC de documentação passaram; `:app:asciidoctor` também passou após fixar AsciidoctorJ 3.0.0, requisito da versão 4 do Spring REST Docs. Os snippets foram gerados para `solicitacoes/criar`, `solicitacoes/consultar`, `solicitacoes/criar-cliente-ausente`, `notas-fiscais/consultar` e `notas-fiscais/consultar-cliente-ausente`; o HTML inclui as seções e headers documentados.

## Arquivos relevantes

- `README.md`, `AGENTS.md`, `.agents/rules/e2e-tests.md`
- `docs/adr/**`, `docs/arquitetura.md`, `docs/deploy.md`, `docs/exemplos/*.http`
- `.github/workflows/ci.yml`, `config/detekt/detekt.yml`, `build-logic/src/main/kotlin/convention.kotlin-jvm.gradle.kts`
- `app/build.gradle.kts`, `gradle/libs.versions.toml` (Spring REST Docs e Asciidoctor)
