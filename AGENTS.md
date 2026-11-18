# Contexto do projeto

Este repositório implementa uma API Kotlin/Spring Boot para emissão assíncrona de
notas fiscais. `README.md`, o PRD e a TechSpec são as fontes dos requisitos de
negócio. Consulte-os antes de alterar contratos ou comportamento.

## Padrões

- Leia as regras em `.agents/rules/` antes de alterar código, build ou testes.
- Preserve `app → domain` e o pacote `com.itau.desafio_diogo_bernard`.
- Nunca registre documentos completos, payloads sensíveis, credenciais ou chaves.
- Use placeholders como `<DB_PASSWORD_FICTICIA>` em exemplos.
- Não altere infraestrutura ou serviços fornecidos sem necessidade para a tarefa.
- Documente a API HTTP com Spring REST Docs, conforme
  [.agents/rules/spring-rest-docs.md](.agents/rules/spring-rest-docs.md). Nunca use
  anotações do Swagger/OpenAPI (`io.swagger.*`) no código.

## Aplicação e serviços

- `app`: API HTTP, PostgreSQL, Kafka e observabilidade.
- `domain`: modelos, validações, cálculo e casos de uso sem dependências Spring.
- PostgreSQL 17 e Kafka KRaft são definidos em `docker-compose.yml`.
- A réplica PostgreSQL e o stack Grafana/OTel são perfis opcionais (`replica` e
  `observabilidade`).
- Os testes de integração e E2E usam Testcontainers. Não dependem de json-server,
  Numerator nem de `docker-compose.e2e.yml`.
- A toolchain Java do Gradle é 21. O Wrapper fixa a versão do Gradle.

## Executar

Suba o ambiente completo com `bash scripts/subir-ambiente.sh`. O script cria o `.env`
a partir de `.env.example` com senhas locais aleatórias quando ele não existe, sobe
PostgreSQL, Kafka, API e worker e espera o readiness dos dois processos. Opções:
`--replica` (réplica de leitura), `--verificar` (emite uma nota de teste), `--sem-build`,
`--parar` e `--limpar` (apaga o volume do banco). Sem o script, crie o `.env`, preencha
os segredos locais e use `docker compose up --build`.

A API fica em `http://localhost:8080`. O actuator (`/actuator/health/readiness` e
`/actuator/prometheus`) fica na porta de gerenciamento de cada processo: `8081` para a
API e `8082` para o worker, que não atende rotas de negócio. O smoke E2E isolado pode ser
executado com `bash scripts/smoke-e2e.sh`.

Comandos disponíveis no Wrapper:

```bash
./gradlew help --warning-mode=all
./gradlew build
./gradlew test
./gradlew :app:integrationTest
./gradlew :app:e2eTest
./gradlew :app:loadTest
./gradlew koverHtmlReport koverVerify
./gradlew detekt ktlintCheck
```

As tarefas `integrationTest`, `e2eTest` e `loadTest` selecionam testes pelas tags
JUnit correspondentes. Testes de infraestrutura sobem PostgreSQL e Kafka descartáveis
com Testcontainers. `koverVerify` exige 100% de linhas e branches em cada módulo;
no `app`, a cobertura considera testes unitários e de integração conforme decisão da
TechSpec. `:app:loadTest` executa carga local isolada e grava resultados em
`app/build/reports/load/` e relatórios Gatling em `app/build/reports/gatling/`.
Use `-PloadRequests=1000` para executar uma rodada; o mínimo é 1.000. `-PloadScenario`
aceita `post`, `consumo`, `consulta` e `combinado`. `post` mede só a escrita (100% aceito,
máximo de 3 s), `consumo` mede o escoamento do worker, `consulta` mede só os dois GETs, com
apenas o PostgreSQL e a API (base de 5k notas semeada por SQL em `scripts/seed-consulta.sql`) e
`combinado` mantém POST com GET imediato somente em 1k. O perfil usa
`LOAD_INSTANCIA_CPUS=4` (API em todos os cenários; worker no consumo e no combinado), limitado
às CPUs disponíveis no Docker; `LOAD_API_CPUS` identifica experimentos explícitos.

Consulte [README.md](README.md), [MANUAL-TESTES.md](MANUAL-TESTES.md) e
[docs/carga/relatorio.md](docs/carga/relatorio.md) para instruções e evidências.

## Alterações

Antes de concluir, execute `git diff --check` e as validações aplicáveis da tarefa.
