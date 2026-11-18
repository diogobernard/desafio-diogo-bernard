# Tarefa 12.0: Migrar a persistência para Spring Data JPA

## Visão geral

Substituir os adaptadores PostgreSQL baseados em `JdbcClient` e SQL explícito por entidades JPA e repositórios Spring Data, conforme a decisão revisada da TechSpec (ADR-006, que substitui o ADR-005). As portas do `domain`, o contrato HTTP, o schema funcional e as garantias transacionais não mudam: idempotência por `(cliente_id, idempotency_key)`, outbox na mesma transação, numeração sem lacunas nem duplicidade, leitura na réplica com fallback filtrado e publicação *at-least-once*. Onde o JPQL não oferece o recurso (`ON CONFLICT`, `UPDATE ... RETURNING`), a consulta nativa fica no próprio repositório Spring Data.

A tarefa não tem como objetivo resolver a lentidão da carga de 10k; as melhorias de desempenho estão propostas separadamente em [`docs/carga/melhorias-desempenho.md`](../../docs/carga/melhorias-desempenho.md). A rodada de carga desta tarefa serve para registrar o efeito da troca, sem meta de latência.

<skills>
### Conformidade com skills

- `executar-task`: implementar esta tarefa de forma incremental, marcando as subtarefas ao concluir.
- `executar-review`: revisar a conformidade com as rules e a TechSpec, incluindo `koverVerify`, ao final.
</skills>

<rules>
### Conformidade com o AGENTS.md e as rules

Lidos o `AGENTS.md` e todas as rules em `.agents/rules/`. Relevantes:

- `clean-architecture.md` e `folder-structure.md`: entidades em `app/repository/entity` e repositórios em `app/repository`; os gateways em `app/gateway/postgres` continuam implementando as portas do `domain` e nunca devolvem entidades gerenciadas. `@Transactional` só no `app`. O `domain` não importa JPA, Hibernate nem Spring Data.
- `kotlin.md`: entidades JPA em classes regulares, sem `data class`, avaliando identidade, `equals`/`hashCode` e `toString` (sem expor `pedido`, `conteudo` ou documento). Preservar os plugins Kotlin/Spring e adicionar `plugin.jpa` e all-open para `@Entity`, `@Embeddable` e `@MappedSuperclass`.
- `code-standards.md`: `toDomain`/`fromDomain` nas próprias entidades, sem Mapper; sem generics próprios além dos exigidos por `JpaRepository`; arquivos com até 100 linhas e funções com até 30; no máximo três parâmetros; constantes nomeadas; dependências por interface.
- `gradle.md`: `spring-boot-starter-data-jpa` com versão gerenciada pelo BOM, declarado no catálogo `gradle/libs.versions.toml`; plugin JPA pelo bloco `plugins {}`; validar com `./gradlew help --warning-mode=all`.
- `logs.md`: não habilitar log de SQL com parâmetros (`org.hibernate.orm.jdbc.bind`) fora de testes; `toString` das entidades não pode expor documento nem payload.
- `unit-tests.md` e `e2e-tests.md`: 100% de linhas e branches no Kover do `app` (agregando `test` e `integrationTest`, conforme desvio aprovado); testes de integração com Testcontainers; sem `Thread.sleep`.
- `spring-rest-docs.md`: não se aplica diretamente (sem mudança de contrato HTTP); não acrescentar anotações Swagger nos arquivos tocados.

Desvio: nenhum.
</rules>

<requirements>
- RF2, RF3, RF6, RF8, RF9, RF10, RF14, RF16, RF17, RF39, RF40, RF42 e RF54 continuam atendidos, sem mudança de comportamento observável.
- Toda a persistência do `app` passa por repositórios Spring Data; `JdbcClient` deixa de ser usado no código de produção.
- Ids atribuídos não geram `SELECT` antes do `INSERT` (`Persistable`), e os itens da nota são gravados em lote.
- O Flyway continua dono do schema (`ddl-auto=validate`), e `open-in-view` fica desligado.
</requirements>

## Subtarefas

- [x] 12.1 Adicionar `spring-boot-starter-data-jpa` ao catálogo e ao `app/build.gradle.kts`, aplicar `kotlin("plugin.jpa")` com all-open para as anotações JPA e configurar `application.yaml`: `open-in-view=false`, `ddl-auto=validate`, `hibernate.jdbc.batch_size`, `order_inserts` e `order_updates`. Confirmar que o `EntityManagerFactory` e o `JpaTransactionManager` usam o `dataSource` `@Primary` (`LazyConnectionDataSourceProxy`) e que os pools Hikari por papel continuam os mesmos.
- [x] 12.2 Criar a migração `V2__outbox_sequence.sql`, que ajusta `outbox_id_seq` ao `allocationSize` usado pelo `@SequenceGenerator` (`ALTER SEQUENCE ... INCREMENT BY`), preservando os valores existentes.
- [x] 12.3 Criar as entidades em `repository/entity` (`SolicitacaoEntity`, `NotaFiscalEntity`, `NotaFiscalItemEntity`, `ContadorNumeracaoEntity`, `OutboxEntity`, `NotificacaoRecebidaEntity` e os `@Embeddable` das chaves compostas), com `jsonb` como `String` e `@JdbcTypeCode(SqlTypes.JSON)`, `Persistable` nos ids atribuídos e `toDomain`/`fromDomain`. Sem associações bidirecionais.
- [x] 12.4 Criar os repositórios em `repository`, com métodos derivados, JPQL e as consultas nativas descritas em “Operações transacionais do adaptador” da TechSpec (`inserirSeAusente`, `reservar`, `registrar` de notificação e a seleção do outbox com `SKIP LOCKED`).
- [x] 12.5 Migrar `SolicitacaoPostgresGateway` (`registrarUmaVez`), `ProcessamentoPostgresGateway` e `SolicitacaoProcessamento` (iniciar, bloquear, emitir, marcar falha), `NotaFiscalPersistencia`, `ContadorNumeracao` e `RegistroEventoOutbox` para os repositórios, mantendo a ordem dos bloqueios (contador por último) e o cálculo fora da transação.
- [x] 12.6 Migrar `LeituraPostgresGateway` para leituras `@Transactional(readOnly = true)` na réplica e transação de escrita em `buscarConsistente` e no último número, preservando o cache *single-flight* e a contagem de statements usada pelo TI-09.
- [x] 12.7 Migrar `PublicadorOutboxRepository`, `LimpezaOutboxJob`, `NotificacaoRecebidaRepository` e os gauges de `ObservabilidadeConfig`. Remover `JdbcClientConfig` e qualquer uso restante de `JdbcClient` em `src/main`.
- [x] 12.8 Implementar TI-20 e TI-21 e ajustar os testes existentes que dependiam de `JdbcClient` como detalhe de implementação, sem enfraquecer as asserções de comportamento.
- [x] 12.9 Escrever `docs/adr/ADR-006-spring-data-jpa.md`, marcar o ADR-005 como substituído e atualizar `docs/adr/README.md` e o `README.md` quando citarem a persistência.
- [x] 12.10 Executar a regressão completa e uma rodada `./gradlew :app:loadTest -PloadRequests=10000`, registrando o resultado em `docs/carga/relatorio.md` sem meta de latência; executar `git diff --check`.

## Detalhes de implementação

Ver na TechSpec: “Visão dos componentes” (linhas `gateway/postgres`, `repository`, `repository/entity` e `config`), “Esquema PostgreSQL”, “Mapeamento JPA”, “Operações transacionais do adaptador”, “Pontos de integração” (PostgreSQL), “Principais decisões” (Spring Data JPA com consultas nativas pontuais), “Riscos conhecidos” (uso incorreto do JPA; Hibernate 7 com Kotlin e Jackson 3) e “Arquivos relevantes e dependentes”.

## Critérios de aceitação relacionados

- CA-01
- CA-03
- CA-05
- CA-06
- CA-07
- CA-11
- CA-12
- CA-13
- CA-15
- CA-18
- CA-21
- CA-22
- CA-23
- CA-26
- CA-30

## Testes da tarefa

### Testes de integração

- [x] TI-01 a TI-19 — regressão completa da suíte de integração existente
- [x] TI-20 — `persiste entidades com id atribuido sem SELECT previo`
- [x] TI-21 — `insere itens da nota em lote`

### Testes E2E

- [x] E2E-01 a E2E-07, E2E-10 e E2E-11 — regressão das jornadas
- [x] E2E-08 — rodada de 10k executada e registrada após a migração, sem meta de latência; a rodada teve 7.803 POSTs aceitos e 2.197 KO, detalhados em `docs/carga/relatorio.md`.

### Resultado da validação

- `./gradlew test :app:integrationTest`: passou.
- `./gradlew :app:e2eTest`: passou.
- `./gradlew detekt ktlintCheck koverVerify`: passou, incluindo 100% de linhas e branches.
- `./gradlew :app:loadTest -PloadRequests=10000`: executou e atualizou o relatório; houve saturação no ingresso (2.197 KO) e 4.679 eventos pendentes no outbox no momento da coleta final. Não havia meta de latência no PRD.
- `git diff --check`: passou.

## Arquivos relevantes

- `gradle/libs.versions.toml`, `app/build.gradle.kts`, `app/src/main/resources/application.yaml`
- `app/src/main/kotlin/com/itau/desafio_diogo_bernard/repository/**` (novo)
- `app/src/main/kotlin/com/itau/desafio_diogo_bernard/repository/entity/**` (novo)
- `app/src/main/kotlin/com/itau/desafio_diogo_bernard/gateway/postgres/**`
- `app/src/main/kotlin/com/itau/desafio_diogo_bernard/config/DataSourceConfig.kt`, `config/JdbcClientConfig.kt` (removido), `config/ObservabilidadeConfig.kt`
- `app/src/main/kotlin/com/itau/desafio_diogo_bernard/entrypoint/job/LimpezaOutboxJob.kt`
- `migrations/postgresql/notafiscal/V2__outbox_sequence.sql` (novo)
- `app/src/test/kotlin/com/itau/desafio_diogo_bernard/**`
- `docs/adr/ADR-005-jdbcclient.md`, `docs/adr/ADR-006-spring-data-jpa.md` (novo), `docs/adr/README.md`, `README.md`, `docs/carga/relatorio.md`
