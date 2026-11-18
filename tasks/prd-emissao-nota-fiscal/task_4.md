# Tarefa 4.0: Infraestrutura local

## Visão geral

Criar o ambiente local com um único comando: `docker-compose.yml` com PostgreSQL primária, réplica opcional (`--profile replica`), Kafka KRaft, aplicação e observabilidade opcional (`--profile observabilidade`), além da migration Flyway inicial, do `DataSourceConfig` com primária e *read-only data source* (`LazyConnectionDataSourceProxy`), do `Dockerfile` e do `.env.example`. Pode ser feita em paralelo com as tarefas 2.0 e 3.0.

<skills>
### Conformidade com skills

- `executar-task`: implementação incremental desta tarefa. Execuções isoladas da aplicação fora do compose seguem as portas da faixa `30**` dessa skill.
</skills>

<rules>
### Conformidade com o AGENTS.md e as rules

Lidos o `AGENTS.md` e todas as rules em `.agents/rules/`. Relevantes:

- `code-standards.md` e `logs.md`: credenciais só por variável de ambiente (`DB_PASSWORD`), `.env` fora do Git, `.env.example` com `<SENHA_FICTICIA>`, sem dump de configuração ou string de conexão em logs.
- `folder-structure.md`: configuração em `app/config`, recursos em `app/src/main/resources`, scripts auxiliares na raiz. Desvio registrado: migrations em `migrations/postgresql/notafiscal/`, incluídas como recurso do `app` (`processResources`).
- `gradle.md`: configuração preguiçosa da inclusão das migrations como recurso.
- Desvio registrado: o `docker-compose.yml` atual do `AGENTS.md` (json-server e Numerator) não existe neste repositório; a TechSpec define o novo compose.
</rules>

<requirements>
- RF51: um único comando sobe banco, mensageria e aplicação.
- Persistência PostgreSQL sem expiração; leituras pela réplica opcional, escrita sempre na primária (restrições técnicas do PRD).
- Esquema da TechSpec: `solicitacao`, `contador_numeracao`, `nota_fiscal`, `nota_fiscal_item`, `outbox`, `notificacao_recebida`.
</requirements>

## Subtarefas

- [x] 4.1 Criar `migrations/postgresql/notafiscal/V1__esquema_inicial.sql` com o esquema e a semente do contador da série padrão.
- [x] 4.2 Incluir as migrations como recurso do `app` e configurar `spring.flyway.locations=classpath:db/migration`.
- [x] 4.3 Criar `DataSourceConfig` (pool HikariCP primário de 30 e réplica de 20, `LazyConnectionDataSourceProxy` com *read-only data source*) e `NotaFiscalProperties` em `config/properties`.
- [x] 4.4 Configurar `application.yaml` com os perfis `local` e `test`, virtual threads e `DB_READ_URL` apontando para a primária quando não houver réplica.
- [x] 4.5 Criar `docker-compose.yml` com profiles `replica` e `observabilidade` e limites de CPU e memória declarados.
- [x] 4.6 Criar `docker/postgres-replica/` com os scripts de *streaming replication*.
- [x] 4.7 Criar o `Dockerfile` da aplicação e o `.env.example`.

## Detalhes de implementação

Ver na TechSpec: "Configuração por ambiente", "Esquema PostgreSQL", "Parâmetros fixos", "Pontos de integração" (PostgreSQL e pools), "Considerações técnicas" (réplica com `LazyConnectionDataSourceProxy`) e "Desvios registrados" (local das migrations).

## Critérios de aceitação relacionados

- CA-29 (parcial: comando único de ambiente; o smoke completo está no E2E-10 da tarefa 9.0)

## Testes da tarefa

A TechSpec não define casos de teste para esta tarefa; os testes de integração das tarefas 5.0 e 6.0 exercitam o esquema e os datasources. Validação por comandos:

- [x] `docker compose up -d --build --wait` com PostgreSQL, Kafka e aplicação saudáveis.
- [x] `docker compose --profile replica up -d --wait` com a réplica recebendo a replicação (`pg_is_in_recovery() = true`, receiver `streaming`).
- [x] Flyway aplica `V1` sem erro ao iniciar a aplicação (versão `1` em `flyway_schema_history`).

Status da execução: os smoke checks do Compose passaram com credenciais locais
temporárias (não gravadas em `.env`), incluindo a réplica PostgreSQL e a inicialização
da aplicação. Também passaram `:app:compileKotlin`, `:app:test`, `:app:ktlintCheck`,
`:domain:test` e `:domain:ktlintCheck`. `:app:ktlintFormat` e `:domain:ktlintFormat`
foram aplicados antes da validação final. A pasta compartilhada não contém metadados
Git; por isso não foi possível executar `git diff --check`.

## Arquivos relevantes

- `migrations/postgresql/notafiscal/V1__esquema_inicial.sql`
- `app/build.gradle.kts` (inclusão das migrations)
- `app/src/main/kotlin/com/itau/desafio_diogo_bernard/config/DataSourceConfig.kt`
- `app/src/main/kotlin/com/itau/desafio_diogo_bernard/config/properties/NotaFiscalProperties.kt`
- `app/src/main/resources/application.yaml`
- `docker-compose.yml`, `docker/postgres-replica/**`, `Dockerfile`, `.env.example`
