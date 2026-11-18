# Tarefa 13.0: Ajustes de desempenho do ingresso e do outbox

## Visão geral

Atacar os gargalos confirmados pelas rodadas de 10.000 solicitações, sem mudar regras de negócio nem garantias transacionais. A linha de base é a rodada `20261116023731227`, registrada depois da tarefa 12: 7.803 POSTs aceitos e 2.197 KO (1.678 fechamentos de conexão e 519 HTTP 500), com 4.679 eventos ainda pendentes no outbox ao final.

A causa foi identificada cruzando `evidences/diagnostico-pool.md` e seus dados brutos com o código:

- **O publicador do outbox limita o escoamento.** `PublicadorOutboxRepository` faz `kafka.send(...).get()` e um `UPDATE` por evento. Nos clientes Kafka 4.x, o `linger.ms` padrão é 5 ms, então cada evento custa cerca de 8 ms. O resultado é um teto de ~125 eventos/s, medido em 10.770 eventos publicados em ~86 s. A fila é única e ordenada por `id`, então os eventos `NotaEmitida` esperam atrás de todo o backlog de solicitações. Isso também diverge da TechSpec, que já previa confirmação e marcação por lote.
- **Nada limita o ingresso.** Com virtual threads, o único limitador do `POST` é o pool Hikari. Na rajada, a espera média foi de ~9,5 s por aquisição, com milhares de requisições em memória e a CPU da API saturada. Depois da rajada, a espera do worker foi praticamente zero: o problema é o ingresso, não a disputa entre API e worker.
- **A transação do `POST` segura a conexão entre comandos.** Ela faz três idas e voltas e, entre elas, trabalho de CPU (id do evento, JSON e `Observation`) com a conexão reservada.
- **A coleta perde o pico.** Os primeiros ~25 s de métricas Hikari ficaram indisponíveis porque o actuator dividia o conector HTTP saturado com a carga.

O escopo segue os itens 1, 3, 5 e 6 de [`docs/carga/melhorias-desempenho.md`](../../docs/carga/melhorias-desempenho.md), mais a medição e a correção do diagnóstico.

**Fora do escopo** (avaliar depois desta rodada): janela de lock do contador de numeração (item 4), projeção do GET de solicitação (item 2), ajustes de JVM/GC e PostgreSQL (item 7) e `server.tomcat.max-connections`.

<skills>
### Conformidade com skills

- `executar-task`: implementar esta tarefa de forma incremental, marcando as subtarefas ao concluir.
- `executar-review`: revisar a conformidade com as rules e a TechSpec, incluindo `koverVerify`, ao final.
- `executar-qa`: revalidar CA-26 e CA-30 com a nova rodada de carga.
</skills>

<rules>
### Conformidade com o AGENTS.md e as rules

Lidos o `AGENTS.md` e todas as rules em `.agents/rules/`. Relevantes:

- `clean-architecture.md` e `folder-structure.md`: o limite de ingresso é proteção técnica, não regra de negócio. Por isso fica em `app/config/resilience` como decorador da porta `SolicitarEmissao`, aplicado na composição (`UseCaseConfig`), e nada muda no `domain`. A exceção de capacidade fica em `app/exception`, e a tradução HTTP, em `entrypoint/handlers`. `@Transactional` continua só nas operações de gateway.
- `code-standards.md`: arquivos com até 100 linhas, funções com até 30 e no máximo três parâmetros; constantes nomeadas para limites e prazos; dependências por interface; sem generics próprios. O decorador recebe e expõe `SolicitarEmissao`.
- `kotlin.md`: sem `synchronized` no caminho quente (virtual threads); usar `java.util.concurrent.Semaphore` com `tryAcquire(prazo)` e liberar a vaga em `finally`.
- `logs.md`: falha de envio ao Kafka gera `WARN` por tentativa, com tópico, `evento_id` e mensagem original sanitizada, sem payload. Rejeições `503` não geram log por requisição (só métrica). O health check continua sem log também na porta de gerenciamento.
- `spring-rest-docs.md`: o `503` passa a fazer parte do contrato do `POST` e é documentado por teste (TI-26), incluído em `index.adoc`. Nenhuma anotação Swagger.
- `unit-tests.md` e `e2e-tests.md`: 100% de linhas e branches no Kover do `app`; Testcontainers nos testes de integração; Awaitility em vez de `Thread.sleep`; `Clock` fixo.
- `gradle.md`: sem dependências novas; validar com `./gradlew help --warning-mode=all` se o build for tocado.
- `CLAUDE.md` (“não altere infraestrutura sem necessidade”): esta tarefa precisa alterar `docker-compose.yml` e `scripts/lib-compose-isolated.sh`, para expor a porta de gerenciamento e passar o timeout e a amostragem por ambiente. As alterações ficam restritas a isso.

Desvios e decisões (registrados nos documentos citados):

- **Calibração de `ingresso.*` (13.10):** os padrões da TechSpec (espera de 2 s, timeout do pool de 5 s) aceitaram só 4% a 5% da rajada de 10k; a espera de 8 s com timeout de 10 s aceitou 19% a 22%. Os novos padrões estão na TechSpec, no ADR-007 e em `docs/carga/relatorio.md`.
- **Compose:** além da porta de gerenciamento, do timeout e da amostragem, o serviço `api` passa `NOTA_FISCAL_INGRESSO_MAX_SIMULTANEAS` e `NOTA_FISCAL_INGRESSO_ESPERA_MAXIMA` (com os mesmos padrões da aplicação), necessários para calibrar o ingresso sem editar o arquivo.
- **Pool de leitura:** `APP_DB_READ_CONNECTION_TIMEOUT` também é configurável, mas mantém o padrão de 30 s do Hikari; um padrão de 5 s, testado por engano, converteu esperas por CPU em `500` nos `GET`.
- **`404` para rotas inexistentes:** o `ProblemDetailsHandler` passou a mapear `NoResourceFoundException` para `404`; antes o handler genérico respondia `500`, o que impedia o TI-25 (actuator ausente na porta da API).
- **Testes de integração:** o PostgreSQL de teste passou a `max_connections=400`, porque a quantidade de contextos Spring em cache esgotava as 100 conexões padrão.
- **Tamanho de arquivos:** `ProblemDetailsHandler.kt` já excedia 100 linhas antes da tarefa e cresceu com o `503`; não foi dividido para manter o escopo.
</rules>

<requirements>
- RF3, RF8, RF10 e RF18: um pedido válido aceito continua recebendo `202` com `Location` e `Retry-After`; a idempotência continua atômica com o `POST` em um único comando; o `503` por capacidade esgotada usa Problem Details com `Retry-After` e não persiste nada.
- RF16 e RF17: zero `404` falso sob carga; as leituras não passam a usar o pool de escrita.
- RF40: numeração sem duplicidade nem lacuna, sem mudança no lock do contador.
- RF42: publicação *at-least-once*; um evento não confirmado continua pendente e é republicado sem perda.
- RF49: `notafiscal.outbox.pendentes` por tópico e `notafiscal.ingresso.rejeitadas`.
- RF54: os pools continuam dentro do orçamento agregado (30 de escrita e 20 de leitura); só o `connection-timeout` passa a ser configurável.
- Parâmetros novos com os padrões da TechSpec: `outbox.espera-confirmacao`, `ingresso.max-simultaneas`, `ingresso.espera-maxima` e `APP_DB_WRITE_CONNECTION_TIMEOUT`.
</requirements>

## Subtarefas

- [x] 13.1 Corrigir `evidences/diagnostico-pool.md` com base nos dados brutos de `app/build/reports/diagnostico/`:
  - CPU de 338% a 449% durante a rajada (os 137,70% citados são a amostra final);
  - inconsistência entre o limite do Método (2 CPUs) e o do Resultado (4 CPUs);
  - primeiros ~25 s de Hikari como `metrics_unavailable`, o que torna os 5.424 pendentes um limite inferior;
  - espera média de ~9,5 s por aquisição durante a rajada e espera ~0 do worker depois dela;
  - só 6 amostras de `pg_stat_activity`;
  - o teto de ~125 eventos/s do publicador.

  Registrar também que a hipótese de separar API e worker foi testada na tarefa 11, e atualizar o estado dos itens em `docs/carga/melhorias-desempenho.md`.
- [x] 13.2 Criar `V3__outbox_pendente_por_topico.sql`, com o índice parcial `(topico, id) WHERE publicado_em IS NULL`. Em `repository/OutboxRepository`:
  - adicionar a seleção pendente por tópico com `SKIP LOCKED`;
  - adicionar `marcarPublicados(ids, agora)`, com um único `UPDATE ... WHERE id = ANY(:ids)`.
- [x] 13.3 Reescrever `PublicadorOutboxRepository` e `PublicadorOutboxJob`:
  - ciclo independente por tópico;
  - envio assíncrono de todos os registros do lote, com o `traceparent` restaurado por registro;
  - espera única pelos futures até `outbox.espera-confirmacao`;
  - marcação só dos confirmados;
  - `WARN` sanitizado por falha.
- [x] 13.4 Encurtar a transação do `POST`:
  - preparar o id do evento, o payload, o JSON do pedido e o `traceparent` antes do primeiro comando, com a `Observation` de `OutboxTracing` cobrindo só o preparo;
  - criar `SolicitacaoRepository.inserirComOutbox`, uma CTE nativa que grava solicitação e outbox em um único comando;
  - adaptar `SolicitacaoPostgresGateway.registrarUmaVez` e `RegistroEventoOutbox` (o `registrarNotaEmitida` do worker não muda);
  - ajustar a falha simulada do TI-01 ao comando único, sem enfraquecer a asserção.
- [x] 13.5 Limitar o ingresso:
  - adicionar `NotaFiscalProperties.Ingresso` (`maxSimultaneas` e `esperaMaxima`, validados como positivos);
  - criar `LimiteIngressoSolicitarEmissao` em `config/resilience` e `CapacidadeIngressoEsgotadaException` em `exception`, com a composição em `UseCaseConfig`;
  - fazer o `ProblemDetailsHandler` responder `503` com `Retry-After` (de `consulta.retry-after`) e incrementar `notafiscal.ingresso.rejeitadas`;
  - adicionar `connection-timeout` configurável aos pools em `application.yaml` (`APP_DB_WRITE_CONNECTION_TIMEOUT`, padrão `5s`) e passá-lo no serviço `api` do Compose.
- [x] 13.6 Reduzir o custo de observabilidade sob carga:
  - deixar a amostragem de traces configurável por `MANAGEMENT_TRACING_SAMPLING_PROBABILITY` (padrão `1.0`, mantendo o TI-14), com um valor reduzido definido em `scripts/carga.sh`;
  - fazer o gauge `notafiscal.outbox.pendentes` medir por tópico, com valor em cache curto e leitura fora do pool de escrita.
- [x] 13.7 Separar a medição:
  - expor o actuator da API na porta de gerenciamento (`MANAGEMENT_SERVER_PORT=8081` no Compose, com o mapeamento substituído por porta livre em `lib-compose-isolated.sh`);
  - apontar readiness e Prometheus de `carga.sh` e `smoke-e2e.sh` para essa porta;
  - fazer `carga.sh` aguardar, com prazo limitado, outbox pendente zero e lag Kafka zero, registrando o tempo de escoamento ou o que restou no prazo;
  - separar no relatório os `503` das falhas de transporte;
  - atualizar `README.md` onde ele cita o actuator.
- [x] 13.8 Implementar TU-23, TU-24 e TI-22 a TI-26 e ajustar os testes existentes afetados (TI-01, TI-06, TI-14, TI-15 e TI-20) sem enfraquecer as asserções de comportamento.
- [x] 13.9 Escrever `docs/adr/ADR-007-limite-ingresso.md`, atualizar `docs/adr/README.md` e incluir o snippet `solicitacoes/criar-capacidade-esgotada` em `app/src/docs/asciidoc/index.adoc`. Validar com `./gradlew :app:asciidoctor`.
- [x] 13.10 Executar a regressão completa e `./gradlew :app:loadTest -PloadRequests=10000`. Registrar em `docs/carga/relatorio.md` a comparação com a rodada `20261116023731227`, sem meta de latência:
  - KO por tipo (`503`, transporte, `500`);
  - pending, uso máximo e timeouts dos pools por processo;
  - sessões `idle in transaction`;
  - pico e escoamento do outbox, e lag Kafka;
  - tempo até `EMITIDA`;
  - CPU e memória;
  - integridade da numeração;
  - a amostragem de traces usada.

  Calibrar `ingresso.*` se a rodada indicar outro valor, registrando o motivo. Executar `git diff --check`.

## Detalhes de implementação

Ver na TechSpec:

- “Visão dos componentes”: linhas `entrypoint/job`, `config/resilience` e `exception`, fluxo de dados e configuração por ambiente (API);
- “Parâmetros fixos” (`outbox.*` e `ingresso.*`);
- “Esquema PostgreSQL” (índice `V3`);
- “Operações transacionais do adaptador” (`registrarUmaVez` e publicador);
- “`POST /issuance-requests`” (resposta `503`);
- “Pontos de integração” (Kafka, PostgreSQL e tratamento de erros);
- “Testes E2E” (parágrafo da carga);
- “Monitoramento e observabilidade” (amostragem, gauge, contador e porta de gerenciamento);
- “Principais decisões” (limite de ingresso e publicação por tópico);
- “Riscos conhecidos” (`503` como erro no relatório e outbox nativo na mesma sequência);
- “Arquivos relevantes e dependentes” (Tarefa 13).

Diagnóstico e propostas: [`evidences/diagnostico-pool.md`](evidences/diagnostico-pool.md) e [`docs/carga/melhorias-desempenho.md`](../../docs/carga/melhorias-desempenho.md).

## Critérios de aceitação relacionados

- CA-01
- CA-05
- CA-07
- CA-12
- CA-23
- CA-26
- CA-27
- CA-28
- CA-30

## Testes da tarefa

### Testes de unidade

- [x] TU-23 — `limita solicitacoes simultaneas e rejeita apos a espera maxima`
- [x] TU-24 — `traduz capacidade esgotada para 503 com Retry-After`

### Testes de integração

- [x] TI-22 — `publica lote do outbox com uma confirmacao por lote`
- [x] TI-23 — `publica notas emitidas sem esperar o backlog de solicitacoes`
- [x] TI-24 — `grava solicitacao e outbox em um unico comando`
- [x] TI-25 — `expoe actuator so na porta de gerenciamento`
- [x] TI-26 — `documenta 503 de capacidade esgotada no POST` (REST Docs)
- [x] TI-01 a TI-21 — regressão completa, com atenção a TI-01, TI-03, TI-06, TI-14, TI-15 e TI-20

### Testes E2E

- [x] E2E-01 a E2E-07, E2E-10 e E2E-11 — regressão das jornadas
- [x] E2E-08 e E2E-09 — rodada de 10k registrada e comparada com `20261116023731227`, sem meta de latência

## Arquivos relevantes

- `tasks/prd-emissao-nota-fiscal/evidences/diagnostico-pool.md`, `docs/carga/melhorias-desempenho.md`, `docs/carga/relatorio.md`
- `migrations/postgresql/notafiscal/V3__outbox_pendente_por_topico.sql` (novo)
- `app/src/main/kotlin/com/itau/desafio_diogo_bernard/repository/OutboxRepository.kt`, `repository/SolicitacaoRepository.kt`
- `app/src/main/kotlin/com/itau/desafio_diogo_bernard/gateway/postgres/PublicadorOutboxRepository.kt`, `PublicacaoOutbox.kt`, `OutboxRepository.kt`, `RegistroEventoOutbox.kt`, `SolicitacaoPostgresGateway.kt`
- `app/src/main/kotlin/com/itau/desafio_diogo_bernard/entrypoint/job/PublicadorOutboxJob.kt`
- `app/src/main/kotlin/com/itau/desafio_diogo_bernard/config/resilience/LimiteIngressoSolicitarEmissao.kt` (novo), `exception/CapacidadeIngressoEsgotadaException.kt` (novo)
- `app/src/main/kotlin/com/itau/desafio_diogo_bernard/config/UseCaseConfig.kt`, `config/ObservabilidadeConfig.kt`, `config/OutboxTracing.kt`, `config/MetricasNegocio.kt`, `config/properties/NotaFiscalProperties.kt`
- `app/src/main/kotlin/com/itau/desafio_diogo_bernard/entrypoint/handlers/ProblemDetailsHandler.kt`
- `app/src/main/resources/application.yaml`, `docker-compose.yml`, `scripts/lib-compose-isolated.sh`, `scripts/carga.sh`, `scripts/smoke-e2e.sh`, `scripts/atualizar-relatorio-carga.py`
- `app/src/test/kotlin/com/itau/desafio_diogo_bernard/**` (incluindo `entrypoint/solicitacoes/SolicitacaoErroDocumentationTest.kt`)
- `app/src/docs/asciidoc/index.adoc`, `docs/adr/ADR-007-limite-ingresso.md` (novo), `docs/adr/README.md`, `README.md`
