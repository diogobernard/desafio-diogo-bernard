# Evidências de QA — 2026-11-15

> Os resultados abaixo preservam a execução histórica de 15/11/2026. A revalidação mais recente está registrada em [qa.md](../qa.md) e resumida a seguir.

## Revalidação — 2026-11-16 (local) / 2026-11-17 UTC

- `./gradlew test :app:integrationTest :app:e2eTest koverHtmlReport koverVerify detekt ktlintCheck --rerun-tasks` — `BUILD SUCCESSFUL` em 3 min.
- JUnit: `app:test` 119, `domain:test` 47, `app:integrationTest` 53 e `app:e2eTest` 7; 226 testes, zero falhas, erros ou ignorados.
- Kover: `app` 1.391/1.391 linhas e 333/333 branches; `domain` 488/488 linhas e 228/228 branches. `koverVerify`, `detekt` e `ktlintCheck` passaram.
- Smoke: `bash scripts/smoke-e2e.sh` foi tentado duas vezes em projetos Compose isolados. O build não chegou a iniciar serviços: `services.gradle.org` respondeu HTTP 502 ao download de Gradle 9.3.1. O trap limpou os projetos temporários; após as tentativas permaneceram somente os quatro containers `desafio-diogo-bernard-*` que já estavam ativos. Nenhuma porta temporária ficou ocupada.
- Carga: foram conferidos os artefatos E2E-12/13/14 e E2E-08/09 da tarefa 16. POST isolado passou 1k/5k/10k em duas rodadas oficiais; consumo drenou 1k/5k/10k com 4/20/40 mil notificações e zero pendências; consulta na réplica teve 2/10/20 mil respostas 200, zero KO/404/5xx/fallback e lag WAL zero; combinado em 1k aceitou e emitiu 1.000 solicitações, completou 1.000 GETs 200 e drenou outbox/Kafka em 2 s.
- Os arquivos sanitizados por cenário estão em [`runtime-separados-tarefa16/`](runtime-separados-tarefa16/); os percentis, recursos e limites do host estão em [docs/carga/relatorio.md](../../../docs/carga/relatorio.md).

## Execução de testes

- `./gradlew test :app:integrationTest :app:e2eTest detekt ktlintCheck koverHtmlReport koverVerify --rerun-tasks` — `BUILD SUCCESSFUL`, suítes executadas sem cache.
- JUnit: `app:test` 59 testes; `domain:test` 46; `app:integrationTest` 32; `app:e2eTest` 7. Zero falhas, erros ou testes ignorados.
- Kover + `koverVerify`: `app` 1.095/1.095 linhas e 237/237 branches; `domain` 488/488 linhas e 228/228 branches.
- `bash scripts/smoke-e2e.sh` — passou: readiness `UP`, POST aceito e solicitação chegou a `EMITIDA`.
- `detekt` e `ktlintCheck` terminaram sem violações.

## Carga

Comando: `./gradlew :app:loadTest -PloadRequests=10000`. Compose isolado escolheu portas livres, subiu PostgreSQL, Kafka e app, executou aquecimento e foi removido pelo trap de limpeza.

- Gatling 10k: `app/build/reports/gatling/emissaocargasimulation-20261115200758777/index.html`.
- POST: 9.919/10.000 respostas 202, 81 falhas de transporte `Premature close`; p50/p95/p99/máx 15.075/21.084/22.934/24.852 ms.
- GET imediato: 9.919/9.919 HTTP 200, zero KO; p50/p95/p99/máx 24/5.441/7.143/8.259 ms.
- PostgreSQL: 9.919 solicitações aceitas, 9.919 `EMITIDA`, zero falhas e zero solicitações pendentes; tempo RECEBIDA→EMITIDA p50/p95/p99 43.846,64/68.474,81/74.526,68 ms.
- Numeração: 9.919 notas, zero duplicidades e zero lacunas (`app/build/reports/load/numeracao-10000.txt`).
- Prometheus respondeu; `notafiscal_leitura_404_falso_total=0`. A réplica não foi ativada, portanto lag/fallback com réplica não foi medido.
- No encerramento (`app/build/reports/load/ambiente-final.txt`): 9.922 notas incluindo três aquecimentos e 9.333 registros de outbox ainda sem `publicado_em`. O script esperou até 60 s apenas pelas solicitações pendentes, não por outbox/consumidores. Pico de notificações pendentes e tempo de escoamento até a entrega aos quatro consumidores não foram registrados.
- Snapshot final de recursos: app 1,952/2 GiB (97,60%); Kafka 931 MiB/1 GiB (90,92%); PostgreSQL 208,1 MiB/1 GiB (20,32%). Ver `app/build/reports/load/recursos-10000-fim.txt`.
- Rodadas anteriores mais recentes por volume: 1k teve 1.000/1.000 POST e GET bem-sucedidos; 5k teve 5.000/5.000 POST e GET bem-sucedidos. Os resultados estão em [`docs/carga/relatorio.md`](../../../docs/carga/relatorio.md), junto com os links dos relatórios Gatling.

## E2E, interface e recursos

- E2E-01 a E2E-07 foram reexecutados em `app:e2eTest`; sete jornadas HTTP passaram.
- O PRD define que o produto não tem interface gráfica. Acessibilidade de teclado, imagens, contraste, formulários visuais e breakpoints são não aplicáveis; não foi iniciada UI nem browser.
- `bash scripts/smoke-e2e.sh` e a rodada de carga usaram Compose isolado. Ao final, `docker ps` mostrou somente os quatro serviços preexistentes `desafio-diogo-bernard-*`; nenhuma porta 3000–3099 ficou escutando.
- A worktree não possui alterações rastreadas pelo Git (os arquivos existentes aparecem como não rastreados); `git diff --check` não cobre esses arquivos. O conteúdo deste relatório foi verificado quanto a whitespace.

## Avisos não bloqueantes

O Gradle avisou que o plugin Kotlin é carregado em mais de um subprojeto. O compilador também marcou como sempre verdadeira uma checagem de tipo em `RegistrarFalhaSolicitacaoUseCase.kt:22`. Nenhum aviso causou falha nos comandos executados.

## Diagnóstico de pool após a rodada 10k

Uma repetição instrumentada executou 10.000 POSTs e GETs sem KO. O pool Hikari de escrita (máximo 30) chegou a 30 conexões ativas, com pico amostrado de 5.424 aquisições pendentes e tempo máximo de aquisição de 17,91 s; não houve timeouts do pool. PostgreSQL chegou a 51 sessões totais e 7 ativas nas amostras, sem evidência de atingir o limite do servidor. `OOMKilled=false`. Os 81 `Premature close` da rodada anterior não se repetiram. Detalhes e limitações em [diagnostico-pool.md](diagnostico-pool.md).
