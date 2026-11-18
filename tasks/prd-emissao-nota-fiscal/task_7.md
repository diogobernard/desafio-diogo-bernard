# Tarefa 7.0: Consumidores simulados

## Visão geral

Implementar os quatro sistemas consumidores simulados (estoque, registro, entrega e financeiro) como *consumer groups* independentes do tópico `notafiscal.notas-emitidas.v1`, cada um com retry, DLQ própria e latência simulada que não bloqueia threads (`CompletableFuture` concluído por `ScheduledExecutorService`, *ack* assíncrono e limite `max-em-voo`). Cada entrega é registrada em `notificacao_recebida` de forma idempotente.

<skills>
### Conformidade com skills

- `executar-task`: implementação incremental desta tarefa.
</skills>

<rules>
### Conformidade com o AGENTS.md e as rules

Lidos o `AGENTS.md` e todas as rules em `.agents/rules/`. Relevantes:

- `folder-structure.md`: listeners em `entrypoint/consumidores`, simulador em `gateway/mock`.
- `logs.md`: `WARN` por tentativa, `ERROR` no envio para DLQ, mensagem original sanitizada.
- `code-standards.md`: interfaces para o simulador, sem generics próprios, constantes nomeadas, limites de tamanho.
- `unit-tests.md`: scheduler controlado no teste, sem `Thread.sleep`, sem depender do relógio real.
</rules>

<requirements>
- RF42, RF43: toda nota notificada aos quatro sistemas ao menos uma vez, uma notificação por nota com a lista completa.
- RF45, RF46: sistemas independentes; latência artificial mantida e sem bloquear outras notas.
- RF47: falha persistente isolada em DLQ para reprocessamento, sem perda.
</requirements>

## Subtarefas

- [x] 7.1 Implementar `SimuladorConsumidor` em `gateway/mock` (latência uniforme e taxa de falha configuráveis por consumidor, sem bloquear threads).
- [x] 7.2 Implementar `EstoqueListener`, `RegistroListener`, `EntregaListener` e `FinanceiroListener`, cada um no próprio *consumer group*, com retorno `CompletableFuture`.
- [x] 7.3 Configurar por consumidor o `DefaultErrorHandler`, o `DeadLetterPublishingRecoverer` para `<topico>.<consumidor>.dlq`, as tentativas e o `max-em-voo`.
- [x] 7.4 Gravar `notificacao_recebida` com `ON CONFLICT (consumidor, evento_id) DO UPDATE SET entregas = entregas + 1`.
- [x] 7.5 Escrever os testes TU-21, TI-10 e TI-11.

## Detalhes de implementação

Ver na TechSpec: "Fluxo de dados" (grupos e DLQs), "Parâmetros fixos" (`consumidores.<nome>.*`), "Esquema PostgreSQL" (`notificacao_recebida`), "Pontos de integração" (consumidores simulados e Kafka) e "Principais decisões" (consumidores assíncronos).

## Critérios de aceitação relacionados

- CA-23
- CA-24
- CA-25

## Testes da tarefa

### Testes de unidade

- [x] TU-21 — `simulador de consumidor conclui apos latencia sem bloquear` (inclui limite de concorrência e falha no agendamento)

### Testes de integração

- [x] TI-10 — `falha persistente em um consumidor vai para sua DLQ sem afetar os outros`
- [x] TI-11 — `cada consumidor recebe uma notificacao por nota com todos os itens`

## Execução

- Quatro listeners independentes recebem os eventos de nota; a latência é agendada sem bloquear threads, com falha, retentativas, limite por consumidor e DLQ configuráveis.
- Entregas são registradas por consumidor/evento com upsert que incrementa duplicatas.
- `:app:test`, `:app:integrationTest` e `ktlintCheck` passaram; relatório Kover gerado em `app/build/reports/kover/html/index.html`.
- `koverVerify` ainda não passa para a aplicação: o relatório global registra 94,5% de linhas e 53,5% de ramificações em `app`, abaixo da meta do projeto. As classes desta tarefa estão cobertas por linhas; o relatório aponta ramificações parciais no handler para campos Kafka anuláveis, além de lacunas de cobertura no código anterior.
- `git diff --check` não estava disponível porque o workspace não contém `.git`; foi feita uma verificação manual de whitespace.

## Arquivos relevantes

- `app/src/main/kotlin/com/itau/desafio_diogo_bernard/entrypoint/consumidores/**`
- `app/src/main/kotlin/com/itau/desafio_diogo_bernard/gateway/mock/SimuladorConsumidor.kt`
- `app/src/main/kotlin/com/itau/desafio_diogo_bernard/config/KafkaConfig.kt`
- `app/src/main/kotlin/com/itau/desafio_diogo_bernard/config/properties/NotaFiscalProperties.kt`
- `app/src/test/kotlin/com/itau/desafio_diogo_bernard/**`
