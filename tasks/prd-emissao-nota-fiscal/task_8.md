# Tarefa 8.0: Observabilidade

## Visão geral

Implementar o rastreamento de ponta a ponta com OpenTelemetry (HTTP → transação → outbox com `traceparent` gravado e restaurado → Kafka → worker → quatro consumidores), as métricas de negócio no Micrometer, os logs estruturados ECS em UTC com CPF e CNPJ mascarados e a exclusão dos health checks de logs, filtros e interceptadores.

<skills>
### Conformidade com skills

- `executar-task`: implementação incremental desta tarefa.
</skills>

<rules>
### Conformidade com o AGENTS.md e as rules

Lidos o `AGENTS.md` e todas as rules em `.agents/rules/`. Relevantes:

- `logs.md`: nunca registrar documento, payload bruto ou `Idempotency-Key` completa (só hash curto); health checks fora dos logs, inclusive com query string; correlação por `solicitacaoId` e `traceId` no MDC, limpo ao fim da execução; neutralizar quebras de linha de entradas externas; níveis `INFO`/`WARN`/`ERROR` conforme a TechSpec.
- `clean-architecture.md`: nenhuma dependência de logging de infraestrutura no `domain`.
- `unit-tests.md`: `OutputCaptureExtension` e exportador em memória, valores fictícios.
</rules>

<requirements>
- RF48: rastreamento de ponta a ponta correlacionado pelo id da solicitação.
- RF49: métricas de notas emitidas, falhas, fila pendente, atraso dos consumidores e tempo até a emissão.
- RF50: logs estruturados com CPF e CNPJ mascarados.
</requirements>

## Subtarefas

- [x] 8.1 Configurar `spring-boot-starter-opentelemetry`, `observation-enabled` no Kafka e o exportador OTLP (perfil `observabilidade`) e em memória (testes).
- [x] 8.2 Gravar o `traceparent` no outbox e restaurá-lo no publicador; propagar `solicitacao-id` como header Kafka, atributo de span e MDC.
- [x] 8.3 Criar `ObservabilidadeConfig` com as métricas da TechSpec (counters, timers, gauges de outbox e lag da réplica, `leitura.fallback` e `leitura.404_falso`).
- [x] 8.4 Configurar `logging.structured.format.console=ecs` em UTC e revisar os logs `INFO`/`WARN`/`ERROR` de todas as camadas.
- [x] 8.5 Excluir `/actuator/health/**` de logs de acesso, filtros e interceptadores; configurar `liveness` e `readiness` (primária e Kafka).
- [x] 8.6 Escrever os testes de integração TI-14 e TI-15.

## Detalhes de implementação

Ver na TechSpec: "Monitoramento e observabilidade" (traces, métricas, health e logs), "`NotaEmitidaEvento`" (headers Kafka), "Esquema PostgreSQL" (`outbox.traceparent`) e "Pontos de integração" (tratamento de erros).

## Critérios de aceitação relacionados

- CA-28

## Testes da tarefa

### Testes de integração

- [x] TI-14 — `propaga trace da requisicao ate os quatro consumidores`
- [x] TI-15 — `logs nao expoem documento sem mascara`

## Arquivos relevantes

- `app/src/main/kotlin/com/itau/desafio_diogo_bernard/config/ObservabilidadeConfig.kt`
- `app/src/main/kotlin/com/itau/desafio_diogo_bernard/entrypoint/job/PublicadorOutboxJob.kt`
- `app/src/main/kotlin/com/itau/desafio_diogo_bernard/gateway/postgres/OutboxRepository.kt`
- `app/src/main/resources/application.yaml`
- `app/build.gradle.kts`, `gradle/libs.versions.toml`
- `app/src/test/kotlin/com/itau/desafio_diogo_bernard/**`
