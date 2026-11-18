# Tarefa 6.0: Publicador do outbox e worker de emissão

## Visão geral

Implementar o processamento assíncrono: `PublicadorOutboxJob` (`FOR UPDATE SKIP LOCKED`, seguro com várias instâncias) publicando no Kafka, `KafkaConfig` com os tópicos e error handlers, `SolicitacaoRecebidaListener` chamando `ProcessarSolicitacao`, `ProcessamentoPostgresGateway` (`iniciarProcessamento`, `emitir` com `ContadorNumeracaoRepository` e outbox `NotaEmitida`, `registrarFalha`), `FalhaProcessamentoRecoverer` com DLQ de solicitações e `LimpezaOutboxJob`.

<skills>
### Conformidade com skills

- `executar-task`: implementação incremental desta tarefa.
</skills>

<rules>
### Conformidade com o AGENTS.md e as rules

Lidos o `AGENTS.md` e todas as rules em `.agents/rules/`. Relevantes:

- `clean-architecture.md`: garantia de numeração no adaptador (`emitir` atômico), `@Transactional` só no `app`, listener em `entrypoint/processamento` apenas delegando ao caso de uso.
- `logs.md`: `WARN` por tentativa que será repetida, `ERROR` na falha definitiva e no envio para DLQ, com a mensagem original sanitizada, sem payload bruto.
- `code-standards.md`: SQL explícito, constantes para o limite 999.999.999, limites de tamanho.
- `unit-tests.md` e `e2e-tests.md`: Awaitility em vez de `Thread.sleep`, Testcontainers PostgreSQL e Kafka.
</rules>

<requirements>
- RF4, RF5: cálculo, numeração e geração da nota com transições de estado.
- RF6: somente os itens do próprio pedido.
- RF39 a RF41: numeração sequencial, única, sem lacunas, 1 a 999.999.999, com várias instâncias.
- RF42 a RF44: evento `NotaEmitida` por nota com a lista completa e valores idênticos aos da nota, entregue ao menos uma vez.
</requirements>

## Subtarefas

- [x] 6.1 Criar `KafkaConfig`: tópicos via `NewTopic` (10 partições, replicação configurável), produtor `acks=all` e idempotente, `DefaultErrorHandler` com backoff exponencial e exceções não repetíveis.
- [x] 6.2 Implementar `PublicadorOutboxJob` (lote, envio síncrono, marca `publicado_em`, rollback e nova tentativa em falha) e `LimpezaOutboxJob` (retenção).
- [x] 6.3 Implementar `ContadorNumeracaoRepository` (`UPDATE ... RETURNING`) e `ProcessamentoPostgresGateway` (`iniciarProcessamento`, `emitir` com `FOR UPDATE`, inserção em *batch* dos itens, `conteudo` serializado e outbox `NotaEmitida`, `registrarFalha`).
- [x] 6.4 Implementar `SolicitacaoRecebidaListener` e `FalhaProcessamentoRecoverer` (marca `FALHA` e envia para `notafiscal.solicitacoes.v1.dlq`).
- [x] 6.5 Garantir que o `conteudo` persistido seja a única representação usada pelo `GET` e pelo evento.
- [x] 6.6 Escrever os testes de integração TI-05 a TI-08 e TI-16, além do teste de rollback da numeração.

## Detalhes de implementação

Ver na TechSpec: "Fluxo de dados", "`NotaEmitidaEvento`", "Operações transacionais do adaptador" (`iniciarProcessamento`, `emitir`, publicador), "Pontos de integração" (Kafka) e "Principais decisões" (outbox, contador, duas transações).

## Critérios de aceitação relacionados

- CA-11
- CA-18
- CA-21
- CA-22
- CA-23

## Testes da tarefa

### Testes de integração

- [x] TI-05 — `numera sem duplicidade nem lacunas com varias instancias`
- [x] TI-06 — `publica outbox ao menos uma vez com duas instancias`
- [x] TI-07 — `evento NotaEmitida e identico a nota persistida`
- [x] TI-08 — `pedidos sequenciais e paralelos nao acumulam itens`
- [x] TI-16 — `marca FALHA apos esgotar tentativas do worker`

## Arquivos relevantes

- `app/src/main/kotlin/com/itau/desafio_diogo_bernard/config/KafkaConfig.kt`
- `app/src/main/kotlin/com/itau/desafio_diogo_bernard/entrypoint/job/{PublicadorOutboxJob,LimpezaOutboxJob}.kt`
- `app/src/main/kotlin/com/itau/desafio_diogo_bernard/entrypoint/processamento/**`
- `app/src/main/kotlin/com/itau/desafio_diogo_bernard/gateway/postgres/{ProcessamentoPostgresGateway,ContadorNumeracaoRepository}.kt`
- `app/src/test/kotlin/com/itau/desafio_diogo_bernard/**`

## Estado da execução

Implementação concluída. Passaram `:app:compileKotlin`, `:domain:test`, `:app:test`, `:app:integrationTest`, `:app:ktlintCheck` e `:domain:ktlintCheck`. Os testes de integração usaram PostgreSQL e Kafka reais via Testcontainers. O workspace não contém `.git`, então `git diff --check` foi substituído por uma verificação manual de whitespace.

`README-pt-br.md` não existe neste workspace; os requisitos da tarefa foram conferidos no PRD, na TechSpec e na definição `task_6.md`.
