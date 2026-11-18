# Tarefa 14.0: Fila limitada em memória e gravação em lote do ingresso

## Visão geral

A tarefa 13.0 limitou o ingresso com um `Semaphore` justo. A rodada oficial de 10.000 solicitações (`20261116043744421`) aceitou 1.897 POSTs (19%): os demais receberam `503`. O limite recusa com rapidez, mas não aumenta a vazão, e a fila de espera era implícita e sem tamanho máximo.

Esta tarefa troca o `Semaphore` por **bulkhead com backpressure**: uma fila em memória com capacidade e espera máxima configuráveis, consumida por poucos escritores que gravam as solicitações em lotes.

- Cada `POST` continua sendo tratado individualmente, na sua thread, e só recebe `202` depois do commit da solicitação, da chave de idempotência e do evento de outbox. Isso preserva "consulta imediata sem falso 404" (CA-12).
- O caso de uso `SolicitarEmissao`, o contrato `SolicitacaoGateway.registrarUmaVez` e o `domain` não mudam. O lote é um serviço interno do `app`, entre o gateway PostgreSQL e o banco.
- Os escritores usam `take` e `drainTo`, sem tempo de espera fixo: sob carga o lote enche sozinho, e em carga baixa o lote tem um item e a latência não piora.
- A fila cheia e a espera excedida respondem `503` com `Retry-After`, com o motivo como tag da métrica.

**Meta:** na configuração de carga (capacidade 10.000, espera de ~50 s e `server.tomcat.max-connections` de ~12.000, definidas por ambiente no `scripts/carga.sh` e no Compose), as rodadas de 1k, 5k e 10k a 1.000 req/s devem ter **zero KO no POST** (nem `503`, nem falha de transporte, nem timeout). Os padrões da aplicação são capacidade 1.000, espera de 8 s, lote máximo de 100 e 2 escritores; com eles, uma rajada de 10k a 1.000 req/s só cabe inteira se a vazão de gravação passar de ~900 req/s, e o excedente recebe `503`. O relatório registra as duas configurações com clareza.

**Fora do escopo:** mover o limite para antes da leitura do corpo da requisição e perfilar a CPU da API (JFR) só entram se a meta não for atingida; o ajuste do encerramento concorrente de consumidores Kafka no JDK 21 (risco pré-existente, registrado na tarefa 13.0).

<skills>
### Conformidade com skills

- `executar-task`: implementar esta tarefa de forma incremental, marcando as subtarefas ao concluir.
- `executar-review`: revisar a conformidade com as rules e a TechSpec, incluindo `koverVerify`, ao final.
- `executar-qa`: revalidar CA-26 e CA-30 com as novas rodadas de carga.
</skills>

<rules>
### Conformidade com o AGENTS.md e as rules

Lidos o `AGENTS.md` e todas as rules em `.agents/rules/`. Relevantes:

- `clean-architecture.md` e `folder-structure.md`: a fila e os escritores são mecanismo de persistência do adaptador, não regra de negócio. Ficam em `app/gateway/postgres/lote` e implementam a porta `SolicitacaoGateway` por composição; `@Transactional` fica só no gravador de lote. O `domain` e o `SolicitarEmissao` não mudam. A exceção de capacidade fica em `app/exception` e a tradução HTTP, em `entrypoint/handlers`. O `Semaphore` e o decorador `LimiteIngressoSolicitarEmissao` deixam de existir: a fila limitada e os escritores são o único mecanismo de limite.
- `code-standards.md`: arquivos com até 100 linhas, funções com até 30 e no máximo três parâmetros; constantes nomeadas; dependências por interface (`GravacaoSolicitacao`, `GravadorLoteSolicitacoes`, `EscritorLote`); sem generics próprios e sem classes Mapper.
- `kotlin.md`: sem `synchronized` no caminho quente (virtual threads); `LinkedBlockingQueue`, `AtomicBoolean` e `CompletableFuture`; escritores em threads de plataforma, para não disputar os carriers das virtual threads com as requisições.
- `logs.md`: nenhuma requisição rejeitada gera log por requisição (só métrica); falha inesperada de escritor gera `ERROR` com o tipo da exceção, sem payload nem documento.
- `spring-rest-docs.md`: o `503` continua documentado por teste; nenhuma anotação Swagger.
- `unit-tests.md` e `e2e-tests.md`: 100% de linhas e branches no Kover do `app`; Awaitility em vez de `Thread.sleep`; `Clock` fixo; Testcontainers nos testes de integração.
- `CLAUDE.md` ("não altere infraestrutura sem necessidade"): `docker-compose.yml` troca `NOTA_FISCAL_INGRESSO_MAX_SIMULTANEAS` pelos novos parâmetros e passa `SERVER_TOMCAT_MAX_CONNECTIONS`; nada além disso.

Desvios e decisões (registrados nos documentos citados):

- **Local do mecanismo:** a fila e os escritores ficam em `gateway/postgres/lote`, e não em `config/resilience` como decorador da porta `SolicitarEmissao` (a sugestão das rules para proteções técnicas). Um decorador não consegue agrupar as gravações, e a regra de decisão da `clean-architecture.md` (o mecanismo mudaria ao trocar a persistência) o mantém no `app`. O `domain` e o `SolicitarEmissao` não mudaram. Registrado no ADR-007.
- **Padrões e perfil de carga:** os padrões são capacidade 1000 e espera de 8 s, conforme orientação do usuário. Os valores 10.000, 50 s e `SERVER_TOMCAT_MAX_CONNECTIONS=12000` só existem no `scripts/carga.sh`, substituíveis por ambiente e registrados no relatório (`ingresso-config.txt`).
- **Resultado da meta:** zero KO no `POST` em 1k, 5k e duas rodadas de 10k. Os `GET` imediatos tiveram 97 `500` em uma das rodadas de 10k (pool de leitura de 10 conexões com ~7.200 pendentes e `connection-timeout` de 30 s), fora da meta; o ponto ficou registrado como próximo passo em `docs/carga/melhorias-desempenho.md`.
- **Memória:** com a fila de 10.000, a API chegou a 1,92 GiB de um limite de 2 GiB. O limite do Compose não foi alterado (infraestrutura fornecida); o risco está no relatório, no ADR-007 e na TechSpec.
- **Remoções:** `ingresso.max-simultaneas`, `LimiteIngressoSolicitarEmissao` e o `TU-23` original (`limita solicitacoes simultaneas...`), este reescrito para a fila cheia.
- **Testes ajustados:** os testes WebMvc passaram a mockar `MetricasIngresso`; TI-01 e TI-24 verificam o comando de lote pelos statements de todas as threads, porque a gravação passou para as threads dos escritores.
- **Rodada (a) de 10k:** os artefatos brutos locais foram sobrescritos pela rodada seguinte; as cópias sanitizadas das quatro rodadas estão em `evidences/runtime-10k-tarefa14/`.
</rules>

<requirements>
- RF3, RF8, RF10 e RF18: um pedido aceito continua recebendo `202` com `Location` e `Retry-After` só depois do commit; a idempotência continua atômica; um pedido rejeitado (`503`) não persiste nada, nem depois que o prazo expira.
- RF11 e RF12: duplicatas da mesma chave dentro do mesmo lote resultam em uma solicitação criada e as demais existentes (`200`, `409` ou `422` pela regra atual).
- RF16 e RF17: zero `404` falso sob carga; as leituras continuam fora do pool de escrita.
- RF40: numeração sem duplicidade nem lacuna.
- RF49: `notafiscal.ingresso.rejeitadas` com a tag `motivo`; novas métricas `notafiscal.ingresso.fila.tamanho`, `notafiscal.ingresso.lote.tamanho` e `notafiscal.ingresso.lote.duracao`.
- RF54: os pools continuam dentro do orçamento agregado; com poucos escritores, a gravação usa 2 conexões de escrita em vez de dezenas.
- Parâmetros novos: `ingresso.capacidade` (1000), `ingresso.espera-maxima` (8s), `ingresso.lote-maximo` (100) e `ingresso.escritores` (2). `ingresso.max-simultaneas` deixa de existir.
</requirements>

## Subtarefas

- [x] 14.1 Configuração do ingresso:
  - substituir `NotaFiscalProperties.Ingresso.maxSimultaneas` por `capacidade`, `loteMaximo` e `escritores` (todos positivos), mantendo `esperaMaxima`;
  - atualizar `application.yaml` e `docker-compose.yml` (`NOTA_FISCAL_INGRESSO_CAPACIDADE`, `_ESPERA_MAXIMA`, `_LOTE_MAXIMO`, `_ESCRITORES` e `SERVER_TOMCAT_MAX_CONNECTIONS`);
  - remover `LimiteIngressoSolicitarEmissao` e o decorador de `UseCaseConfig`.
- [x] 14.2 Gravação em lote:
  - criar `LoteInsercao` e `SolicitacaoRepository.inserirLote`, uma CTE com `unnest` que insere as solicitações com `ON CONFLICT DO NOTHING`, insere os eventos de outbox só das criadas e devolve os `evento_id` criados, tudo em um comando;
  - criar `GravadorLoteSolicitacoes` (interface) e `GravadorLotePostgres` (`@Transactional`), que informa, na ordem do lote, se cada item foi criado.
- [x] 14.3 Fila, escritores e rejeição:
  - criar `PedidoGravacao` (item com resultado e a decisão atômica "assumido ou cancelado"), `EscritorLoteSolicitacoes` (agrupa, grava e isola o item inválido gravando individualmente quando o lote falha por `DataIntegrityViolationException`) e `FilaGravacaoSolicitacoes` (`GravacaoSolicitacao`, `SmartLifecycle` e `MeterBinder`);
  - criar `MotivoRejeicaoIngresso` (`fila_cheia`, `espera_excedida` e `encerrando`), `MetricasIngresso` e ajustar o `ProblemDetailsHandler`;
  - adaptar `SolicitacaoPostgresGateway.registrarUmaVez`, que deixa de ser transacional, e ajustar a composição.
- [x] 14.4 Implementar TU-23, TU-25 a TU-31 e TI-27 a TI-29 e ajustar os testes existentes afetados (TI-01, TI-24, `NotaFiscalPropertiesTest`, `ProblemDetailsHandlerTest`, `SolicitacaoGatewayMappingIntegrationTest`) sem enfraquecer as asserções.
- [x] 14.5 Perfil de carga: `scripts/carga.sh` define capacidade 10.000, espera de 50 s e `SERVER_TOMCAT_MAX_CONNECTIONS` 12.000 (substituíveis por ambiente), e registra os valores usados no relatório.
- [x] 14.6 Documentação: atualizar `docs/adr/ADR-007-limite-ingresso.md` e `docs/adr/README.md`, a TechSpec, `README.md` e `docs/carga/melhorias-desempenho.md`.
- [x] 14.7 Executar a regressão completa e as rodadas de carga de 1k, 5k e 10k com o perfil de carga. Registrar em `docs/carga/relatorio.md`, sem meta de latência: KO por tipo, vazão de aceitação, p50/p95/p99/máx do POST, tamanho do lote e profundidade da fila, uso dos pools, escoamento, heap e CPU, integridade da numeração, quantidade de solicitações gravadas contra POSTs aceitos e `404` falsos. Se a meta de 0 KO não for atingida, perfilar com JFR e registrar a causa. Executar `git diff --check`.

## Detalhes de implementação

Ver na TechSpec:

- "Visão dos componentes" (`gateway/postgres/lote`, `config/properties` e `exception`) e "Fluxo de dados";
- "Parâmetros fixos" (`ingresso.*`);
- "Operações transacionais do adaptador" (`registrarUmaVez` e gravador de lote);
- "`POST /issuance-requests`" (resposta `503` e motivos);
- "Monitoramento e observabilidade" (métricas de ingresso);
- "Principais decisões" e "Riscos conhecidos" (limite de ingresso, `503` como erro no relatório);
- "Testes" (TU-23, TU-25 a TU-31, TI-27 a TI-29 e o parágrafo da carga).

Decisão anterior: [`docs/adr/ADR-007-limite-ingresso.md`](../../docs/adr/ADR-007-limite-ingresso.md). Diagnóstico: [`docs/carga/melhorias-desempenho.md`](../../docs/carga/melhorias-desempenho.md).

## Critérios de aceitação relacionados

- CA-01
- CA-05
- CA-07
- CA-11
- CA-12
- CA-23
- CA-26
- CA-27
- CA-28
- CA-30

## Testes da tarefa

### Testes de unidade

- [x] TU-23 — `rejeita sem esperar quando a fila de gravacao esta cheia`
- [x] TU-24 — `traduz capacidade esgotada para 503 com Retry-After e conta o motivo`
- [x] TU-25 — `cancela o item e rejeita quando a espera maxima excede antes de um escritor assumi-lo`
- [x] TU-26 — `aguarda o resultado quando o escritor assumiu o item antes do prazo`
- [x] TU-27 — `grava o lote em um comando e informa criada ou existente por item`
- [x] TU-28 — `descarta itens cancelados e nao chama o banco quando o lote fica vazio`
- [x] TU-29 — `grava individualmente quando o lote falha por dado invalido e isola o item defeituoso`
- [x] TU-30 — `falha todos os itens do lote quando o banco esta indisponivel`
- [x] TU-31 — `descarrega a fila no encerramento e rejeita novas requisicoes`

### Testes de integração

- [x] TI-27 — `agrupa gravacoes concorrentes em poucos comandos`
- [x] TI-28 — `resolve a mesma chave de idempotencia dentro do mesmo lote`
- [x] TI-29 — `item invalido nao falha os demais itens do lote`
- [x] TI-01 e TI-24 — ajustados ao gravador de lote, sem enfraquecer as asserções
- [x] TI-01 a TI-26 — regressão completa

### Testes E2E

- [x] E2E-01 a E2E-07, E2E-10 e E2E-11 — regressão das jornadas
- [x] E2E-08 e E2E-09 — rodadas de 1k, 5k e 10k com o perfil de carga, com zero KO no POST, registradas e comparadas com `20261116043744421`

## Arquivos relevantes

- `app/src/main/kotlin/com/itau/desafio_diogo_bernard/gateway/postgres/lote/` (novo: `GravacaoSolicitacao.kt`, `GravadorLoteSolicitacoes.kt`, `GravadorLotePostgres.kt`, `PedidoGravacao.kt`, `EscritorLote.kt`, `EscritorLoteSolicitacoes.kt`, `FilaGravacaoSolicitacoes.kt`)
- `app/src/main/kotlin/com/itau/desafio_diogo_bernard/gateway/postgres/SolicitacaoPostgresGateway.kt`
- `app/src/main/kotlin/com/itau/desafio_diogo_bernard/repository/SolicitacaoRepository.kt`, `repository/LoteInsercao.kt` (novo)
- `app/src/main/kotlin/com/itau/desafio_diogo_bernard/config/UseCaseConfig.kt`, `config/MetricasIngresso.kt` (novo), `config/MetricasNegocio.kt`, `config/properties/NotaFiscalProperties.kt`
- `app/src/main/kotlin/com/itau/desafio_diogo_bernard/exception/CapacidadeIngressoEsgotadaException.kt`, `exception/MotivoRejeicaoIngresso.kt` (novo)
- `app/src/main/kotlin/com/itau/desafio_diogo_bernard/entrypoint/handlers/ProblemDetailsHandler.kt`
- `app/src/main/resources/application.yaml`, `docker-compose.yml`, `scripts/carga.sh`
- `app/src/test/kotlin/com/itau/desafio_diogo_bernard/**`, `app/src/integration/kotlin/com/itau/desafio_diogo_bernard/entrypoint/**`
- `docs/adr/ADR-007-limite-ingresso.md`, `docs/adr/README.md`, `docs/carga/melhorias-desempenho.md`, `docs/carga/relatorio.md`, `README.md`, `tasks/prd-emissao-nota-fiscal/techspec.md`
