# Tarefa 5.0: API HTTP — POST e GETs

## Visão geral

Implementar no `app` o protocolo HTTP: `POST /issuance-requests`, `GET /issuance-requests/{id}` e `GET /invoices/{numero}`, os DTOs com `toDomain`/`fromDomain`, a configuração do Jackson 3, o `ProblemDetailsHandler` (RFC 9457), o `UuidV7Gerador` e os adaptadores PostgreSQL de registro idempotente com outbox (`registrarUmaVez`) e de leitura na réplica com fallback filtrado. Compor os casos de uso em `UseCaseConfig`.

<skills>
### Conformidade com skills

- `executar-task`: implementação incremental desta tarefa.
</skills>

<rules>
### Conformidade com o AGENTS.md e as rules

Lidos o `AGENTS.md` e todas as rules em `.agents/rules/`. Relevantes:

- `clean-architecture.md`: controllers restritos ao protocolo, tradução de exceções em `entrypoint/handlers`, `@Transactional` só nas operações de gateway, composição em `app/config`.
- `code-standards.md`: `toDomain`/`fromDomain` nos DTOs, sem classe Mapper; SQL explícito com `JdbcClient`; anotações Jackson em `com.fasterxml.jackson.annotation`, demais componentes em `tools.jackson`.
- `logs.md`: sem documento, payload bruto ou `Idempotency-Key` completa em logs; validações `400`/`422` sem `ERROR` nem stack trace; `500` genérico sem mensagem interna.
- `unit-tests.md`: unidades isoladas para DTOs, handler e montagem de headers; integração com `@Tag("integration")` e Testcontainers.
- Desvio registrado: Kover do `app` agrega `test` e `integrationTest`.
</rules>

<requirements>
- RF1 a RF3: `POST` com `Idempotency-Key`, validação síncrona, `202` com id, `Location` e `Retry-After`.
- RF7 a RF11: idempotência atômica por `(cliente_id, idempotency_key)`.
- RF12 a RF17: consultas com isolamento, link estável, sem `404` falso e sem carga na primária para ids antigos.
- RF18 a RF21: Problem Details com erros por campo, nunca `500` para entrada inválida.
- RF22 a RF26, RF28: contrato de entrada e normalização do unitário.
</requirements>

## Subtarefas

- [x] 5.1 Configurar o Jackson 3 (`JacksonConfig`): `snake_case`, `USE_BIG_DECIMAL_FOR_FLOATS`, `ACCEPT_FLOAT_AS_INT` desabilitado, `WRITE_BIGDECIMAL_AS_PLAIN`, `FAIL_ON_UNKNOWN_PROPERTIES` desabilitado (confirmar os equivalentes em `tools.jackson`).
- [x] 5.2 Criar os DTOs de `entrypoint/model` com `toDomain`/`fromDomain` e campos anuláveis.
- [x] 5.3 Implementar `UuidV7Gerador` (`GeradorSolicitacaoId`) com `Clock` injetado.
- [x] 5.4 Implementar `SolicitacaoPostgresGateway.registrarUmaVez` (`ON CONFLICT DO NOTHING RETURNING` + outbox `SolicitacaoRecebida` na mesma transação) e `OutboxRepository`.
- [x] 5.5 Implementar `LeituraPostgresGateway` (`buscar` na réplica, `buscarConsistente` na primária, `ultimoNumeroEmitido` com cache e releitura *single-flight* via `ReentrantLock`).
- [x] 5.6 Implementar `SolicitacaoController` e `NotaFiscalController` (`Location`, `Retry-After`, `Link`, `ETag`, `Cache-Control`, `304`, validação de `X-Cliente-Id`, id malformado → `404` sem banco).
- [x] 5.7 Implementar `ProblemDetailsHandler` com `errors[]`, precedência do `400` e `500` genérico com `trace_id`.
- [x] 5.8 Criar `UseCaseConfig` compondo os casos de uso com as portas e a estratégia de tributação escolhida por propriedade.
- [x] 5.9 Escrever os testes TU-18 a TU-20, TU-22 e TI-01 a TI-04, TI-09, TI-12, TI-13 e TI-17.

## Detalhes de implementação

Ver na TechSpec: "Visão dos componentes" (pacotes do `app`), "Modelos de dados" (convenções JSON, DTOs, `ProblemDetails`, mapeamento DTO → domínio), "Operações transacionais do adaptador" (`registrarUmaVez`), "Endpoints da API" (respostas e algoritmos de leitura) e "Riscos conhecidos" (Jackson 3 e cache do último número).

## Critérios de aceitação relacionados

- CA-01
- CA-02
- CA-03
- CA-04
- CA-05
- CA-06
- CA-07
- CA-08
- CA-09
- CA-10
- CA-11
- CA-12
- CA-13
- CA-14
- CA-15

## Testes da tarefa

### Testes de unidade

- [x] TU-18 — `converte request para dominio preservando BigDecimal`
- [x] TU-19 — `traduz excecoes para problem details`
- [x] TU-20 — `monta Location Retry-After e links por status`
- [x] TU-22 — `gera UUIDv7 ordenavel e extrai o instante`

### Testes de integração

- [x] TI-01 — `aceita pedido e grava solicitacao e outbox na mesma transacao`
- [x] TI-02 — `rejeita pedido invalido sem persistir`
- [x] TI-03 — `cria uma unica solicitacao para chaves simultaneas`
- [x] TI-04 — `rejeita chave reutilizada com outro payload`
- [x] TI-09 — `roteia leitura para replica e aplica fallback filtrado` (GET com réplica vazia consulta na primária)
- [x] TI-12 — `isola solicitacoes e notas por cliente`
- [x] TI-13 — `responde problem details para entradas malformadas`
- [x] TI-17 — `GET da nota responde ETag e 304`

## Arquivos relevantes

- `app/src/main/kotlin/com/itau/desafio_diogo_bernard/entrypoint/{solicitacoes,notas,model,handlers}/**`
- `app/src/main/kotlin/com/itau/desafio_diogo_bernard/gateway/postgres/{SolicitacaoPostgresGateway,LeituraPostgresGateway,OutboxRepository}.kt`
- `app/src/main/kotlin/com/itau/desafio_diogo_bernard/gateway/id/UuidV7Gerador.kt`
- `app/src/main/kotlin/com/itau/desafio_diogo_bernard/config/{JacksonConfig,UseCaseConfig}.kt`
- `app/src/test/kotlin/com/itau/desafio_diogo_bernard/**`

## Estado da execução

Implementação concluída. Passaram `:app:compileKotlin`, `:app:test`, `:app:integrationTest`, `:app:ktlintCheck`, `:domain:test` e `:domain:ktlintCheck`. Os testes de integração usam PostgreSQL Testcontainers com réplica de leitura vazia e confirmam o fallback para a primária. O ambiente compartilhado não contém `.git`; `git diff --check` não estava disponível e foi substituído por uma verificação manual de whitespace.
