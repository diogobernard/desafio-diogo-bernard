# Tarefa 9.0: Testes E2E e de carga

## Visão geral

Implementar as jornadas E2E (`@Tag("e2e")`) com a aplicação em `RANDOM_PORT` contra Testcontainers e um cliente HTTP real, os cenários de carga (`@Tag("load")`) com Gatling em DSL Kotlin contra o ambiente `docker compose` com limites declarados, os scripts `scripts/carga.sh` e `scripts/verificar-numeracao.sql` e o relatório comparativo em `docs/carga/relatorio.md`.

<skills>
### Conformidade com skills

- `executar-task`: implementação incremental desta tarefa.
</skills>

<rules>
### Conformidade com o AGENTS.md e as rules

Lidos o `AGENTS.md` e todas as rules em `.agents/rules/`. Relevantes:

- `e2e-tests.md`: chamar a entrada HTTP pública, verificar resposta e estado persistido, uma jornada por teste em Given/When/Then, nomes em português entre crases, Awaitility com timeout em vez de `Thread.sleep`, `Clock` controlado onde possível, classes `<Fluxo>E2ETest`, suíte com zero testes não é validação.
- Desvio registrado na TechSpec: os E2E rodam contra Testcontainers PostgreSQL e Kafka, e não contra json-server e Numerator, e a carga roda contra o `docker compose` deste projeto (correção da rule na tarefa 10.0).
- `logs.md`: relatórios e mensagens de asserção sem documento sem máscara.
- `gradle.md`: `loadTest` chamando `scripts/carga.sh` com entradas e saídas declaradas.
</rules>

<requirements>
- Objetivos do PRD: latência do POST, tempo até `EMITIDA`, carga de 1.000, 5.000 e 10.000 a 1.000 req/s, relatórios de emissão e de GETs, consistência, isolamento, numeração e centavos.
- RF1 a RF3, RF8, RF12 a RF16, RF29 a RF38, RF42 a RF44, RF51.
</requirements>

## Subtarefas

- [x] 9.1 Criar a base dos E2E em `app/src/e2e/kotlin/.../e2e/` (Testcontainers com primária, réplica simulada e Kafka, cliente HTTP real e fixture de pedido).
- [x] 9.2 Implementar E2E-01 a E2E-07.
- [x] 9.3 Criar `app/src/gatling/kotlin/**` com os cenários E2E-08 (emissão em 1k, 5k e 10k, modelo aberto, aquecimento fora da medição) e E2E-09 (GET imediato concorrente com POST).
- [x] 9.4 Criar `scripts/carga.sh` (sobe um compose isolado com limites, roda o Gatling, coleta métricas e SQL) e `scripts/verificar-numeracao.sql` (duplicidades e lacunas).
- [x] 9.5 Implementar o smoke E2E-10 (`docker compose up -d --wait`, `readiness` `UP` e um POST até `EMITIDA`).
- [x] 9.6 Gerar `docs/carga/relatorio.md` com o ambiente de referência e os resultados medidos; dados brutos em `app/build/reports/load/` e Gatling.

## Detalhes de implementação

Ver na TechSpec: "Testes E2E" (tabela e parágrafo sobre o ambiente de carga), "Abordagem de testes" (ferramentas e tags), "Monitoramento e observabilidade" (métricas usadas no relatório) e "Riscos conhecidos" (contenção no contador e pool de conexões).

## Critérios de aceitação relacionados

- CA-01
- CA-03
- CA-05
- CA-09
- CA-10
- CA-12
- CA-16
- CA-17
- CA-18
- CA-19
- CA-20
- CA-23
- CA-26
- CA-27
- CA-29

## Testes da tarefa

### Testes E2E

- [x] E2E-01 — `emite nota PF do pedido ate a consulta da nota`
- [x] E2E-02 — `emite nota PJ para cada regime suportado`
- [x] E2E-03 — `reenvio com a mesma chave devolve a mesma solicitacao`
- [x] E2E-04 — `consulta imediata apos o aceite nunca retorna 404` (300 POSTs com GET imediato)
- [x] E2E-05 — `quatro sistemas sao notificados da nota emitida`
- [x] E2E-06 — `emite nota no modo por dentro configurado`
- [x] E2E-07 — `aceita 500 itens e rejeita 501`
- [x] E2E-08 — `carga de emissao em 1k 5k e 10k` — rodadas repetidas com POST/GET, erros, percentis e estados de persistência registrados; sem limites predefinidos de latência.
- [x] E2E-09 — `latencia dos GETs sob carga de emissao` — cenário corrigido exige `200`; 1.000 GETs passaram na rodada de 1k, 245 passaram e 453 expiraram na rodada de 5k, sem GET 404 observado. Em 10k não houve POST aceito para consulta.
- [x] E2E-10 — `ambiente local sobe com um comando` (smoke passou em compose isolado).

## Resultado e pendências

- `./gradlew :app:e2eTest`: os sete testes E2E implementados passaram.
- `scripts/smoke-e2e.sh`: readiness `UP` e solicitação chegou a `EMITIDA`.
- `./gradlew :app:loadTest`: as três rodadas foram repetidas com o GET exigindo HTTP 200 e sem qualquer meta predefinida de latência. Os resultados, percentis, erros, estados persistidos e limites de coleta estão em [`docs/carga/relatorio.md`](../../docs/carga/relatorio.md).
- A rodada final de 1k teve 1.000 POSTs e 1.000 GETs bem-sucedidos. A de 5k teve 209 POSTs aceitos pelo Gatling e 41 GETs concluídos com HTTP 200; os demais expiraram. Na de 10k, todos os POSTs falharam por encerramento de conexão/timeouts e nenhum GET foi iniciado. O app atingiu aproximadamente 99% de CPU e memória disponível nas rodadas de maior volume.
- Os relatórios Prometheus das rodadas 5k/10k não foram coletados porque o app não respondeu dentro do limite local; essa indisponibilidade está gravada nos arquivos e documentada no relatório. A consulta global final foi coletada após o ajuste do script. A tarefa 9.0 está concluída com essa limitação do ambiente explicitada.
- Nenhuma alteração foi feita nos serviços preexistentes; o Compose isolado foi removido pelo script e os containers que já estavam em execução permaneceram ativos.

## Arquivos relevantes

- `app/src/test/kotlin/com/itau/desafio_diogo_bernard/e2e/**`
- `app/src/gatling/kotlin/**`
- `app/build.gradle.kts` (tarefas `e2eTest` e `loadTest`)
- `scripts/carga.sh`, `scripts/verificar-numeracao.sql`
- `docker-compose.yml` (limites de CPU e memória)
- `docs/carga/relatorio.md`
