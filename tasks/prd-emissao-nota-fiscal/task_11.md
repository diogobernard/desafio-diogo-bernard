# Tarefa 11.0: Separar os runtimes de API e workers e medir o isolamento

## Visão geral

Permitir iniciar a API HTTP e os workers em processos independentes do mesmo artefato, com pools Hikari limitados por processo. Preservar o orçamento agregado do PostgreSQL (até 30 conexões de escrita e 20 de leitura), atribuir 2 CPUs e 2 GiB para cada processo e registrar os resultados observados em uma rodada de 10.000 solicitações. Por orientação explícita do usuário, esta tarefa não exige comparação com `evidences/diagnostico-pool.md`.

<skills>
### Conformidade com skills

- `executar-task`: implementar esta tarefa após aprovação do planejamento.
</skills>

<rules>
### Conformidade com o AGENTS.md e as rules

Lidos o `AGENTS.md` e todas as rules em `.agents/rules/`. Relevantes:

- `clean-architecture.md`: separar composição e adaptadores por papel sem mover mecanismos de infraestrutura para `domain`; preservar `app → domain`.
- `code-standards.md`, `folder-structure.md` e `kotlin.md`: manter nomes, organização e limites atuais do projeto.
- `gradle.md`: usar o Wrapper e validar as tarefas afetadas.
- `e2e-tests.md` e `unit-tests.md`: testar inicialização dos papéis, jornada HTTP e comportamento com infraestrutura isolada; não usar esperas arbitrárias.
- `logs.md`: métricas, registros e artefatos da rodada não podem expor payloads, documentos ou credenciais.

Não alterar o contrato HTTP, regras fiscais, semântica de idempotência ou garantias transacionais.
</rules>

<requirements>

- RF49, RF51 e RF54.
- Preservar o orçamento agregado da configuração de referência: no máximo 30 conexões de escrita e 20 de leitura, somados os processos API e worker.
- Registrar a rodada de 10.000 solicitações com 2 CPUs e 2 GiB para cada processo API/worker, sem estabelecer metas de latência ou exigir comparação com outra rodada.
- Coletar uso e espera Hikari por processo, sessões `idle in transaction`, retenção/duração das conexões e transações, backlog de outbox e lag Kafka, recursos, timeouts e integridade da numeração.
</requirements>

## Subtarefas

- [x] 11.1 Definir e configurar os papéis de runtime `api` e `worker` no mesmo artefato: API com controllers HTTP; worker com publicação de outbox, processamento de solicitações, consumidores Kafka e jobs associados. Preservar o modo combinado usado pelos testes existentes.
- [x] 11.2 Configurar pools Hikari próprios, limitados e configuráveis por papel; dimensionar os limites de forma que sua soma não ultrapasse os orçamentos de escrita e leitura da referência, considerando também quando a leitura usa a primária.
- [x] 11.3 Adaptar `docker-compose.yml`, os scripts isolados de smoke/carga e documentação operacional para iniciar os papéis independentes e manter o comando único local. API e worker têm 2 CPUs e 2 GiB cada.
- [x] 11.4 Implementar TI-18 e TI-19 e o smoke E2E-11, verificando isolamento dos componentes, limites dos pools e uma emissão completa entre processos.
- [x] 11.5 Executar a rodada de 10k, coletar as métricas definidas no CA-30 e registrar método, resultados e limitações em `docs/carga/relatorio.md`, mantendo evidências brutas sanitizadas. A comparação foi dispensada por orientação do usuário.

## Detalhes de implementação

Ver na TechSpec: “Resumo”, “Visão dos componentes”, “Configuração por ambiente”, “Pontos de integração” (PostgreSQL), “Testes de integração” (TI-18/TI-19), “Testes E2E” (E2E-11), “Monitoramento e observabilidade” e “Riscos conhecidos”. O diagnóstico e a linha de base ficam em [`evidences/diagnostico-pool.md`](evidences/diagnostico-pool.md).

## Critérios de aceitação relacionados

- CA-26
- CA-29
- CA-30

## Testes da tarefa

### Testes de integração

- [x] TI-18 — `ativa componentes conforme o papel do processo`
- [x] TI-19 — `limita pools por papel dentro do orçamento agregado`

### Testes E2E

- [x] E2E-08 — `carga de emissao em 1k 5k e 10k` (rodada de 10k registrada; 9.422 POSTs aceitos, com falhas e backlog documentados)
- [x] E2E-11 — `emite nota com API e worker em processos separados`

## Arquivos relevantes

- `app/src/main/resources/application.yaml`
- `app/src/main/kotlin/com/itau/desafio_diogo_bernard/config/**`
- `app/src/main/kotlin/com/itau/desafio_diogo_bernard/entrypoint/**`
- `app/src/test/kotlin/com/itau/desafio_diogo_bernard/**`
- `docker-compose.yml`, `Dockerfile`
- `scripts/lib-compose-isolated.sh`, `scripts/carga.sh`, `scripts/smoke-e2e.sh`
- `README.md`, `docs/carga/relatorio.md`
- `tasks/prd-emissao-nota-fiscal/evidences/diagnostico-pool.md`

## Resultado da execução

- API e worker iniciam separadamente, cada um com limite de 2 CPUs e 2 GiB. Os pools máximos são API 25/10 e worker 5/10 (escrita/leitura), dentro do orçamento agregado 30/20.
- TI-18/TI-19, `PoolMetricsLogJobTest`, suíte completa de unidade/integração, `detekt`, `ktlintCheck` e smoke E2E-11 passaram. O smoke confirmou uma nota, quatro notificações e numeração íntegra.
- A rodada 10k aceitou e emitiu 9.422 solicitações, registrou 578 falhas de POST, pico de 667 esperas Hikari na API, um timeout de pool, 7.083 registros de outbox pendentes e lag acumulado de 3.743 mensagens nos quatro consumidores. Método, latências, recursos e limitações estão em [`docs/carga/relatorio.md`](../../docs/carga/relatorio.md), com evidências sanitizadas em [`evidences/runtime-10k/`](evidences/runtime-10k/).
- A comparação com a evidência anterior foi dispensada por orientação explícita do usuário. `koverVerify` permaneceu abaixo do gate global de 100% de linhas (99,9127%), apesar das suítes de testes passarem.
