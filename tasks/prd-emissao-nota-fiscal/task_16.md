# Tarefa 16.0: Testes de carga separados por serviço com instâncias dedicadas de 4 CPUs

## Visão geral

A tarefa 15.0 mostrou que a latência do `POST` a 1.000 req/s é limitada pela CPU da instância, não pelo banco nem pelo bulkhead. As duas rodadas oficiais com 2 CPUs não cumpriram a meta de 3 s (10k: máximo de 15,5 s e 25,3 s). Elas rodaram com o Mac ocupado (média de carga de 15 a 20), e com 2 CPUs a API não tem margem para isso.

Com a instância de escrita em 4 CPUs, o 10k passou em 2 de 3 rodadas:

| Rodada | Máximo | p50 | Média de carga do Mac |
|---|---|---|---|
| 2 | 1,03 s | 45 ms | 4 a 10 |
| 3 | 0,38 s | 24 ms | 20 a 29 |

A rodada que falhou teve uma parada única da JVM de ~6 a 8 s no meio da rajada, com causa não confirmada (a rodada foi sem JFR). Nas outras, o heap não passou de 423 MiB e a maior pausa de GC foi de 101 ms.

Esta tarefa segue a análise que o usuário fez depois dos testes de carga, que não está no PRD. Em produção, escrita, consumo e consulta rodam em instâncias dedicadas com mais recurso computacional. Por isso, cada serviço é medido em um teste separado, nesta ordem:

1. `POST` isolado (escrita);
2. consumo de eventos (worker, publicador do outbox e os quatro consumidores);
3. consulta (`GET`).

No perfil de carga, a instância medida usa 4 CPUs. O `docker-compose.yml` de referência continua com 2 CPUs por serviço.

O cenário combinado (`POST` com `GET` imediato) deixa de rodar 5k e 10k. Ele fica só com 1k e 4 CPUs, para validar os fallbacks de leitura com a réplica ativa.

**Metas:**

- **`POST`:** 100% aceitos e latência máxima de cada requisição até 3 s nas rajadas de 1k, 5k e 10k a 1.000 req/s, em duas rodadas oficiais.
- **Consumo e consulta:** sem limite de latência predefinido (CA-26 e CA-27). As rodadas registram os valores observados e verificam a consistência. Se o usuário definir uma meta depois da linha de base, ela vira asserção do Gatling, como no `POST`.
- **Combinado em 1k:** 100% dos `POST` aceitos, zero `404` falso, fallbacks contados por motivo e todas as solicitações emitidas.

**Fora do escopo:**

- mudar os limites do `docker-compose.yml`, o contrato HTTP, as regras de negócio ou o banco de dados;
- corrigir o risco de deadlock no encerramento dos consumidores Kafka do worker real no JDK 21 (registrado nas tarefas 13.0 e 15.0).

<skills>
### Conformidade com skills

- `executar-task`: implementar esta tarefa de forma incremental, marcando as subtarefas ao concluir.
- `executar-review`: revisar a conformidade com as rules e a TechSpec ao final.
- `executar-qa`: revalidar CA-12, CA-23, CA-26, CA-27 e CA-30 com as novas rodadas.
</skills>

<rules>
### Conformidade com o AGENTS.md e as rules

- `CLAUDE.md` ("não altere infraestrutura sem necessidade"): os 4 CPUs da instância medida vêm de um override do Compose isolado de carga. O `docker-compose.yml` não muda.
- `code-standards.md` e `kotlin.md`: as simulações Gatling novas seguem os limites de arquivo, função e parâmetros e reaproveitam `CargaComum.kt`, sem genéricos próprios.
- `gradle.md`: `-PloadScenario` aceita `post`, `consumo`, `consulta` e `combinado`, com validação do valor na configuração da tarefa `executarCarga`.
- `logs.md`: o feeder da consulta só contém identificadores e números de nota, sem documento nem payload. Os relatórios e as evidências não guardam variáveis de ambiente nem senhas.
- `e2e-tests.md`: carga continua na tarefa `:app:loadTest`, fora da suíte E2E.
- `unit-tests.md`: a cobertura de 100% de linhas e branches continua valendo se houver mudança em código de produção.

Decisões do usuário (registrar na TechSpec e no relatório):

- Testes separados por serviço, na ordem `POST` → consumo → consulta.
- Instância medida com 4 CPUs no perfil de carga.
- Cenário combinado só em 1k, com 4 CPUs, para validar os fallbacks.
- A cobertura de 5k e 10k exigida pelo CA-26 vem dos cenários isolados: latência do `POST` no E2E-12, tempo de processamento e integridade da numeração no E2E-13, latência do `GET` no E2E-14.
</rules>

<requirements>
- CA-12, RF16 e RF17: zero `404` falso; as leituras continuam fora do pool de escrita.
- CA-23: cada nota emitida é notificada uma vez a cada um dos quatro consumidores, sem mensagens nas DLQs.
- CA-26 e CA-27: vazão, percentis, erros por código, tempos de processamento, recursos e integridade da numeração por volume. Na consulta e no combinado, registrar também o lag da réplica e a taxa de fallback por motivo.
- CA-30 e RF54: os pools de cada instância continuam limitados e registrados.
- RF40: numeração sem duplicidade nem lacuna.
- Meta do usuário para o `POST`: 100% aceitos e máximo de 3 s por requisição.
</requirements>

## Subtarefas

- [x] 16.1 Perfil de instância dedicada:
  - criar `LOAD_INSTANCIA_CPUS` (padrão 4), aplicado por override do Compose isolado à instância medida: API no `POST` e na consulta, worker no consumo, as duas no combinado;
  - limitar o valor às CPUs disponíveis no Docker (`docker info`), para o CI não quebrar em runners com menos núcleos, e registrar o valor efetivo no relatório;
  - fazer com que esse perfil não marque a rodada como experimento (`LOAD_API_CPUS` continua sendo chave de experimento);
  - amostrar a média de carga do host durante cada rodada, registrar o pico no relatório e avisar quando ela já começa acima do número de núcleos;
  - guardar o log de GC e safepoints da instância medida (`-Xlog` via `JAVA_TOOL_OPTIONS`) com a rodada, para explicar uma eventual parada da JVM.
- [x] 16.2 `POST` isolado (E2E-12) com 4 CPUs:
  - `scripts/carga-post.sh` passa a usar o perfil da 16.1;
  - executar duas rodadas oficiais de 1k, 5k e 10k;
  - se uma rodada falhar, registrar a causa (GC ou safepoint, carga do host, fila ou pool) antes de repetir.
- [x] 16.3 Consumo de eventos isolado (E2E-13): `scripts/carga-consumo.sh`, `-PloadScenario=consumo` e `scripts/verificar-consumo.sql`.
  - Backlog: N solicitações (1k, 5k e 10k) criadas pelo `POST` com o worker congelado (`docker compose pause`), para a JVM continuar aquecida. Conferir N solicitações e N eventos no outbox antes de liberar o worker. A pausa fica abaixo do `session.timeout` dos consumidores.
  - Aquecimento: um backlog de 3.000 solicitações antes das medições.
  - Medição, a partir do `unpause`:
    - vazão de emissão (notas/s);
    - tempo até a última solicitação `EMITIDA`;
    - tempo até as quatro notificações de cada nota e o lag zero;
    - p50/p95/p99 de `notafiscal.emissao.duracao` e espera por lock da numeração;
    - outbox pendente e lag ao longo do tempo;
    - CPU, memória e pools do worker.
  - Consistência:
    - N notas emitidas;
    - numeração sem duplicidade nem lacuna;
    - quatro notificações por nota (uma por consumidor);
    - DLQs vazias;
    - nenhum evento repetido.
- [x] 16.4 Consulta isolada (E2E-14): `ConsultaCargaSimulation`, `scripts/carga-consulta.sh` e `-PloadScenario=consulta`.
  - Base: N solicitações emitidas antes da medição (escoamento completo), depois o worker parado. Os ids e números de nota são exportados para o feeder do Gatling.
  - Réplica ativa (`--profile replica`, `DB_READ_URL` apontando para a réplica), como numa instância de consulta.
  - Rajada de N usuários a 1.000/s, depois do aquecimento da API. Cada usuário consulta o status (`GET /issuance-requests/{id}`) e a nota (`GET /invoices/{numero}?serie=1`) com o `X-Cliente-Id` de quem criou a solicitação.
  - Registro: p50/p95/p99/máx por endpoint, erros por código, uso do pool de leitura, fallbacks por motivo (esperado zero, porque tudo já está replicado) e lag da réplica.
  - Critério: zero `5xx` e zero `404`.
- [x] 16.5 Combinado em 1k (E2E-08 e E2E-09):
  - `scripts/carga.sh` passa a rodar só 1k por padrão, com API e worker em 4 CPUs e a réplica ativa;
  - registrar os fallbacks de leitura por motivo, o lag da réplica e os `404` falsos;
  - o CI continua chamando `./gradlew :app:loadTest -PloadRequests=1000`.
- [x] 16.6 Gradle e documentação:
  - `-PloadScenario=post|consumo|consulta|combinado`, README e `AGENTS.md`;
  - TechSpec: E2E-08 e E2E-09 em 1k, E2E-12 com 4 CPUs, E2E-13 e E2E-14 novos, perfil de instância dedicada, risco do host compartilhado e o parágrafo do CA-30;
  - ADR-007: corrigir a "Atualização (tarefa 15)", que hoje diz que a meta foi cumprida com 2 CPUs. Isso só aconteceu nos experimentos; as rodadas oficiais falharam com o host ocupado. Registrar a decisão pelos 4 CPUs;
  - `docs/carga/relatorio.md`: uma seção por cenário e a análise da tarefa 15 (matriz de experimentos, `accept-count`, aquecimento e contenção do host);
  - `docs/carga/melhorias-desempenho.md`;
  - evidências sanitizadas em `evidences/runtime-separados-tarefa16/`, incluindo as rodadas oficiais da tarefa 15 e as três rodadas exploratórias de 10k com 4 CPUs.
- [x] 16.7 Validação:
  - unitários, integração, `koverVerify`, `ktlintCheck`, `detekt` e E2E;
  - rodadas de carga na ordem `POST` → consumo → consulta → combinado, conferindo a carga do host antes de cada uma;
  - `git diff --check`.

## Detalhes de implementação

Ver na TechSpec:

- "Testes": parágrafo da carga, E2E-08, E2E-09 e E2E-12;
- "Riscos conhecidos": rajada de conexões e JVM fria no `POST` com 2 CPUs;
- "Monitoramento e observabilidade": métricas de emissão, fallback e ingresso.

Decisões anteriores: [`docs/adr/ADR-007-limite-ingresso.md`](../../docs/adr/ADR-007-limite-ingresso.md) e [`task_15.md`](task_15.md).

## Critérios de aceitação relacionados

- CA-12
- CA-23
- CA-26
- CA-27
- CA-30

## Testes da tarefa

### Testes E2E e de carga

- [x] E2E-12 — `post isolado em 1k 5k e 10k`, com 4 CPUs (duas rodadas oficiais)
- [x] E2E-13 — `consumo isolado em 1k 5k e 10k`
- [x] E2E-14 — `consulta isolada em 1k 5k e 10k`
- [x] E2E-08 e E2E-09 — cenário combinado só em 1k, com 4 CPUs e réplica ativa
- [x] E2E-01 a E2E-07, E2E-10 e E2E-11 — regressão das jornadas

### Testes de unidade e integração

- [x] Regressão completa, com 100% de linhas e branches no Kover se houver mudança em código de produção

## Progresso

16.1 e 16.6 implementadas. `LOAD_INSTANCIA_CPUS` é limitado por `docker info`, aplica-se via override temporário aos serviços medidos, não marca a execução como experimento e grava GC/safepoints e carga média do host. Gradle aceita os quatro cenários. As simulações e scripts novos estão implementados; `docker compose config -q` validou o override com 4 CPUs e `max-em-voo=200`, sem alterar o Compose de referência. O resumo de experimentos reconhece a chave `cpus_api_experimento`, e `carga-consumo.sh` arquiva métricas sem logs completos do worker.

Histórico das tentativas exploratórias iniciais de E2E-13, antes da correção abaixo: uma notificou 761/12.000 eventos do aquecimento em 300 s; outra pausou o worker antes da atribuição das partições e precisou reiniciá-lo, após o que os consumidores repetiram falhas. Uma execução confirmatória, já aguardando as atribuições e respeitando a pausa, emitiu as 3.000 notas de aquecimento, mas registrou só 8.446/12.000 notificações no limite de 300 s. A rodada medida não começou. Evidências sanitizadas e GC/safepoints em `evidences/runtime-separados-tarefa16/consumo-inicial/`, `consumo-pausa-inicial/` e `consumo-confirmatorio/`. Esse estado foi superado pelo diagnóstico, correção e resultados finais registrados a seguir; a tarefa 16 está concluída.

Diagnóstico e correção do E2E-13 (16/11/2026): as falhas de consumo vinham de três defeitos do worker. (1) Os consumidores Kafka da factory padrão rodavam em virtual threads, e o *pinning* do JDK 21 (log do cliente Kafka dentro de `synchronized` esperando o lock do appender) travava o worker na atribuição de partições; `KafkaListenerThreadsConfig` devolve os consumidores a threads de plataforma. (2) Cada container buscava 500 registros por `poll`, acima dos 200 em voo do simulador, e os registros rejeitados se perdiam sem retry nem DLQ (3.655 na rodada instrumentada); `ConsumidoresKafkaConfig` aplica `max.poll.records = max-em-voo / concorrência` por `ContainerCustomizer`. (3) O simulador concluía o futuro antes de liberar a permissão; agora libera antes. Falhas em lote: com `setSeekAfterError(false)` e listener assíncrono, o spring-kafka 4.1.2 só reprocessava a última de várias falhas do mesmo lote; o TI-30 reproduziu (0 de 40 na DLQ). O retry com backoff e a DLQ passaram para `ReceberNotificacaoComRetentativas`, que só libera o offset após registro ou confirmação da DLQ; o `DefaultErrorHandler` ficou como contingência. Depois da correção, TI-30 e regressão (unitários 47 + 119, integração 53, E2E 7, `koverVerify` com 100% no `app`: 1.391 linhas e 333 branches, `detekt`, `ktlintCheck`) passaram.

Resultados da tarefa 16: E2E-12 passou nas duas rodadas oficiais, nos três volumes, com 100% aceito, máximo de POST abaixo de 3 s e consistência do outbox. E2E-13 passou em 1k/5k/10k: 1k/5k/10k emitidas, 4k/20k/40k notificações, nenhuma pendência, repetição, duplicidade/lacuna de numeração ou DLQ, escoamento em 2/2/3 s. E2E-14 passou com 2k/10k/20k respostas HTTP 200, zero KO, `404` ou `5xx`; lag WAL da réplica em 0 bytes e zero fallback por motivo. O combinado E2E-08/09 passou em 1k, com 1000 POSTs e GETs 200, zero `404` falso, seis fallbacks `recente`, outbox/lag escoados em 2 s e numeração íntegra. A carga média do host excedeu 8 CPUs em várias rodadas e está indicada nas evidências e no relatório; latências de consulta são descritivas. `./gradlew test :app:integrationTest :app:e2eTest koverVerify detekt ktlintCheck` passou. Evidências e percentis em `docs/carga/relatorio.md` e `evidences/runtime-separados-tarefa16/`.

Reexecução de consulta E2E-14 (17/11/2026, 22:40–23:01 UTC): A (pool 10/réplica 1 CPU e 768 MiB), B (pool 25/réplica 2 CPUs e 1 GiB), C (B mais otimizações de leitura) e D (repetição de 5k) foram executadas novamente. Os relatórios Gatling e as métricas auxiliares estão arquivados por volume e configuração em [docs/carga/relatorio.md](../../docs/carga/relatorio.md). B em 10k não chegou à medição GET: 1 dos POSTs de preparação falhou com `Connection reset by peer`, e o relatório da falha também foi preservado. A carga do host excedeu 8 durante as medições.

## Critérios de sucesso

- **`POST`:** nas duas rodadas oficiais, 1k, 5k e 10k com 100% aceitos, nenhuma requisição acima de 3 s e um evento de outbox por solicitação aceita.
- **Consumo:** em cada volume, todas as solicitações `EMITIDA`, quatro notificações por nota, DLQs vazias, numeração sem duplicidade nem lacuna e lag zero dentro do prazo, com vazão e tempos registrados.
- **Consulta:** zero `5xx` e zero `404`, com percentis registrados por endpoint e volume.
- **Combinado em 1k:** 100% dos `POST` aceitos, zero `404` falso, fallbacks contados por motivo e todas as solicitações emitidas.
- **Registro de cada rodada:** CPUs efetivas da instância, pico de carga do host e log de GC e safepoints.
- **Ambiente de referência:** o `docker-compose.yml`, o contrato HTTP e as regras de negócio não mudam.

## Arquivos relevantes

- `scripts/carga-post.sh`, `scripts/carga.sh`, `scripts/carga-consumo.sh` (novo), `scripts/carga-consulta.sh` (novo)
- `scripts/lib-carga-coleta.sh`, `scripts/lib-carga-experimento.sh`, `scripts/lib-compose-isolated.sh`
- `scripts/verificar-ingresso.sql`, `scripts/verificar-consumo.sql` (novo), `scripts/atualizar-relatorio-carga.py`
- `app/src/gatling/kotlin/com/itau/desafio_diogo_bernard/load/` (`CargaComum.kt`, `IngressoCargaSimulation.kt`, `ConsultaCargaSimulation.kt` novo)
- `app/build.gradle.kts` (`-PloadScenario`)
- `docs/carga/relatorio.md`, `docs/carga/melhorias-desempenho.md`, `docs/adr/ADR-007-limite-ingresso.md`
- `README.md`, `AGENTS.md`, `tasks/prd-emissao-nota-fiscal/techspec.md`
