# Resumo das tarefas de implementação da API de Emissão de Nota Fiscal

Referências: [`prd.md`](prd.md) e [`techspec.md`](techspec.md).

## Tarefas

- [x] 1.0 Build multimódulo e estrutura (`app`/`domain`, catálogo, `build-logic`, Kover, detekt, ktlint, tarefas de teste)
- [x] 2.0 Domínio de cálculo (`Dinheiro`, tabelas, estratégias `POR_FORA`/`POR_DENTRO`, `CalcularNotaFiscal`) — depende de 1.0
- [x] 3.0 Validação, modelo de solicitação e casos de uso de emissão, processamento e consulta — depende de 2.0
- [x] 4.0 Infraestrutura local (`docker-compose.yml`, Flyway, `DataSourceConfig` com réplica) — smoke checks e validação Gradle concluídos — depende de 1.0
- [x] 5.0 API HTTP: `POST /issuance-requests`, `GET /issuance-requests/{id}` e `GET /invoices/{numero}` — implementação, testes unitários e de integração concluídos — depende de 3.0 e 4.0
- [x] 6.0 Publicador do outbox e worker de emissão com numeração fiscal — testes unitários, de integração e ktlint concluídos — depende de 5.0
- [x] 7.0 Consumidores simulados (estoque, registro, entrega e financeiro) com DLQs — implementação, testes direcionados e ktlint concluídos; `koverVerify` segue abaixo da meta global — depende de 6.0
- [x] 8.0 Observabilidade: traces, métricas de negócio e logs estruturados com máscara — TI-14 e TI-15 concluídos — depende de 7.0
- [x] 9.0 Testes E2E e de carga com relatório — E2E-08/09 executados, relatório e coletores registrados; métricas indisponíveis sob saturação documentadas — depende de 8.0
- [x] 10.0 Documentação, CI e correção dos desvios do `AGENTS.md` e da `e2e-tests.md` — build, testes, E2E, smoke Compose, `detekt`, `ktlintCheck` e `koverVerify` concluídos; `app` e `domain` com 100% de linhas e branches (detalhes em `task_10.md`) — depende de 9.0
- [x] 11.0 Separar os runtimes de API e workers e medir o isolamento — API e worker com 2 CPUs/2 GiB cada, pools Hikari dentro dos limites agregados, smoke separado aprovado e rodada de carga 10k documentada sem comparação por orientação do usuário — depende de 10.0
- [x] 12.0 Migrar a persistência para Spring Data JPA (entidades, repositórios com consultas nativas pontuais, migração `V2` da sequência do outbox e ADR-006), sem mudar contrato HTTP, `domain` ou garantias transacionais — depende de 11.0
- [x] 13.0 Ajustes de desempenho do ingresso e do outbox (publicador por tópico com envio assíncrono do lote, limite de ingresso com `503`, `POST` em um único comando, custo de tracing, actuator na porta de gerenciamento, escoamento medido e correção do diagnóstico de pool) — depende de 12.0
- [x] 14.0 Fila limitada em memória e gravação em lote do ingresso (backpressure com `503`, escritores que agrupam as solicitações em um comando por lote, meta de aceitar 100% das rajadas de 10k na configuração de carga) — depende de 13.0
- [x] 15.0 Teste do `POST` isolado e diagnóstico da latência (JFR, `accept-count` de 4.096, aquecimento, menos CPU por `POST`, coletores compartilhados e `-PloadScenario`) — meta de 3 s não atendida com 2 CPUs nas rodadas oficiais e transferida para a 16.0 — depende de 14.0
- [x] 16.0 Testes de carga separados por serviço (`POST`, consumo de eventos e consulta) com instâncias dedicadas de 4 CPUs, e cenário combinado só em 1k para validar os fallbacks de leitura — depende de 15.0

**Progresso da tarefa 6.0:** compilação, testes unitários, todos os cenários de integração da tarefa e ktlint passaram no ambiente principal. `git diff --check` não estava disponível porque o workspace não contém `.git`; foi feita uma verificação manual de whitespace.

**Progresso da tarefa 5.0:** compilação, testes unitários, testes de integração e ktlint passaram no ambiente principal. `git diff --check` não estava disponível porque o workspace não contém `.git`; foi feita uma verificação manual de whitespace.

**Progresso da tarefa 7.0:** testes unitários e de integração da tarefa e `ktlintCheck` passaram; relatório Kover gerado. A verificação global de cobertura segue abaixo de 100% em `app` (94,5% de linhas e 53,5% de ramificações), incluindo código anterior a esta tarefa. `git diff --check` não estava disponível porque o workspace não contém `.git`; foi feita uma verificação manual de whitespace.

**Progresso da tarefa 8.0:** TI-14 e TI-15, `:app:test` e `ktlintCheck` passaram. `git diff --check` continua indisponível porque o workspace não contém `.git`; o whitespace dos arquivos alterados foi conferido manualmente.

**Progresso da tarefa 9.0:** E2E-01 a E2E-07 e o smoke E2E-10 passaram. Gatling repetiu 1k/5k/10k com GET exigindo status 200; percentis, erros e falhas de coleta por saturação estão registrados em `docs/carga/relatorio.md`, sem metas predefinidas de latência. O endpoint Prometheus ficou indisponível nas rodadas 5k/10k, registrado nos arquivos brutos, e a consulta final de outbox foi coletada com sucesso. A tarefa foi concluída; a indisponibilidade das métricas sob saturação é uma limitação documentada do ambiente local.

**Progresso da tarefa 11.0:** API e worker foram separados no Compose, cada qual com 2 CPUs e 2 GiB; pools somam até 30 conexões de escrita e 20 de leitura. TI-18/TI-19, E2E-11, testes unitários direcionados, suíte completa de unidade/integração, `detekt` e `ktlintCheck` passaram. A carga 10k aceitou e emitiu 9.422 solicitações; 578 POSTs falharam e o backlog do outbox/Kafka está documentado em `docs/carga/relatorio.md` e `evidences/runtime-10k/`. A comparação foi dispensada por orientação explícita do usuário. `koverVerify` ainda falha no gate global de 100% de linhas (99,9127% reportado), embora a suíte completa tenha passado; o smoke final separado passou. `git diff --check` foi executado; como os arquivos do workspace aparecem como não rastreados, whitespace também foi conferido diretamente nos arquivos alterados.

**Progresso da tarefa 13.0:** unitários (132), integração (49) e E2E (7) passaram; `koverVerify` (100% de linhas e branches em `app` e `domain`), `detekt`, `ktlintCheck`, `:app:asciidoctor` e o smoke Compose (E2E-10/11) também. A rodada de 10k comparada com `20261116023731227` está em `docs/carga/relatorio.md`: o outbox deixou de acumular (pico de 15 contra 6.537) e o tempo até `EMITIDA` caiu de 55 s para 2,3 s (p50), sem `500` por esgotamento do pool, mas a API aceitou 1.897 de 10.000 POSTs (19%; 8.098 `503`), contra 7.803 na linha de base. Os padrões de `ingresso.*` foram calibrados (espera de 8 s, timeout do pool de 10 s) porque 2 s aceitava só 4% a 5%. Hipótese ainda não medida: o limite fica depois da leitura do corpo, e a API continua em ~220% de CPU recusando ~80% dos pedidos; ver `docs/carga/melhorias-desempenho.md` e o ADR-007. `git diff --check` foi executado (sem `.git` com commits, whitespace conferido diretamente nos arquivos alterados).

**Progresso da tarefa 14.0:** unitários, integração (TI-27 a TI-29 e TI-01/TI-24 ajustados) e 7 E2E passaram; `koverVerify` (100% de linhas e branches em `app` e `domain`), `ktlintCheck`, `detekt` e o smoke Compose (E2E-10/11) também. No perfil de carga (fila 10.000, espera 50 s, Tomcat 12.000 conexões), as rodadas de 1k, 5k e duas de 10k aceitaram 100% dos `POST` (0 KO), com numeração íntegra e 10.000 notas emitidas no 10k; a API usou 2 conexões de escrita, com no máximo 0,72 s de uso e nenhum pendente. O custo é a latência do `POST` (p50 de 12,6 a 16,5 s no 10k) e o pico de 1,92 GiB de memória da API (limite de 2 GiB). Fora da meta: 97 `GET` imediatos viraram `500` em uma das duas rodadas de 10k (pool de leitura com ~7.200 pendentes e `connection-timeout` de 30 s), e o lag Kafka não zerou em 300 s. Os padrões da aplicação são capacidade 1000 e espera de 8 s; 10.000 e 50 s só valem para a carga. Detalhes em `docs/carga/relatorio.md`, no ADR-007 e em `evidences/runtime-10k-tarefa14/`. `git diff --check` foi executado (sem commits no repositório, whitespace conferido diretamente nos arquivos alterados).

**Progresso da tarefa 15.0:** unitários, integração (52), `koverVerify` (100% de linhas e branches em `app` e `domain`), `detekt`, `ktlintCheck`, 7 E2E e a regressão do cenário combinado em 1k passaram. O diagnóstico com JFR mostrou que a latência do `POST` a 1.000 req/s é limitada pela CPU da API, não pelo banco: cada lote leva ~25–45 ms, com no máximo 2 conexões de escrita. Com `accept-count` de 4.096 e a instância aquecida, os experimentos com 2 CPUs cumpriram a meta (2 de 2, pior 0,4–1,5 s). As duas rodadas oficiais com 2 CPUs não cumpriram (10k: máximo de 15,5 s e 25,3 s), com o Mac ocupado (média de carga de 15 a 20). Nelas, 100% dos `POST` foram aceitos, a fila de conexões não transbordou e a escrita ficou íntegra. Rodadas exploratórias de 10k com 4 CPUs passaram em 2 de 3 (máximo de 1,0 s e 0,38 s); a que falhou teve uma parada única da JVM de ~6–8 s, sem causa confirmada. A meta de 3 s passa para a 16.0. As suítes de integração e E2E passaram a usar `spring.test.context.cache.pause=never`, com tópicos exclusivos para os contextos que têm consumidores e configuração própria: 3 execuções de integração e 2 de E2E, sem travar. Whitespace conferido diretamente nos arquivos alterados (sem commits no repositório).

**Progresso da tarefa 16.0:** duas rodadas oficiais E2E-12 com 4 CPUs passaram em 1k/5k/10k (100% POST aceitos, nenhum acima de 3 s, escrita íntegra); E2E-13 passou nos três volumes (1k/5k/10k emitidas, 4k/20k/40k notificações, DLQs vazias e numeração contínua); E2E-14 passou com 2k/10k/20k GETs HTTP 200, zero erros e WAL da réplica sem bytes pendentes; E2E-08/09 passou em 1k com zero `404` falso e seis fallbacks `recente`. Unitários, integração, E2E, Kover 100%, detekt e ktlint passaram. A carga do host excedeu 8 CPUs em várias medições; o relatório registra os picos e limita os percentis a valores descritivos. Evidências sanitizadas e resultados em `task_16.md`, `docs/carga/relatorio.md` e `evidences/runtime-separados-tarefa16/`.

## Rastreabilidade

| Tarefa | Casos de teste da TechSpec | Critérios de aceitação |
|---|---|---|
| 1.0 | — (validação por comandos Gradle) | CA-29 (parcial) |
| 2.0 | TU-01 a TU-07, TU-16 | CA-16, CA-17, CA-18, CA-19, CA-20, CA-21 |
| 3.0 | TU-08 a TU-15, TU-17 | CA-02, CA-03, CA-04, CA-05, CA-06, CA-09, CA-10, CA-11, CA-12, CA-13, CA-14, CA-15, CA-22, CA-28 |
| 4.0 | — (validação por `docker compose` e contexto) | CA-29 (parcial) |
| 5.0 | TU-18 a TU-20, TU-22, TI-01 a TI-04, TI-09, TI-12, TI-13, TI-17 | CA-01 a CA-15 |
| 6.0 | TI-05 a TI-08, TI-16 | CA-11, CA-18, CA-21, CA-22, CA-23 |
| 7.0 | TU-21, TI-10, TI-11 | CA-23, CA-24, CA-25 |
| 8.0 | TI-14, TI-15 | CA-28 |
| 9.0 | E2E-01 a E2E-10 | CA-01, CA-03, CA-05, CA-09, CA-10, CA-12, CA-16, CA-17, CA-18, CA-19, CA-20, CA-23, CA-26, CA-27, CA-29 |
| 10.0 | — (validação pelo pipeline de CI) | CA-29 (parcial) |
| 11.0 | TI-18, TI-19, E2E-08 (rodada 10k), E2E-11 | CA-26, CA-29, CA-30 |
| 12.0 | TI-01 a TI-21 (TI-20 e TI-21 novos), E2E-01 a E2E-08 (rodada 10k), E2E-10, E2E-11 | CA-01, CA-03, CA-05, CA-06, CA-07, CA-11, CA-12, CA-13, CA-15, CA-18, CA-21, CA-22, CA-23, CA-26, CA-30 |
| 13.0 | TU-23, TU-24, TI-22 a TI-26, TI-01 a TI-21 (regressão), E2E-01 a E2E-11 (rodada 10k comparada) | CA-01, CA-05, CA-07, CA-12, CA-23, CA-26, CA-27, CA-28, CA-30 |
| 14.0 | TU-23, TU-25 a TU-31, TI-27 a TI-29, TI-01 e TI-24 (ajustados), TI-01 a TI-26 (regressão), E2E-01 a E2E-11 (rodadas 1k, 5k e 10k) | CA-01, CA-05, CA-07, CA-11, CA-12, CA-23, CA-26, CA-27, CA-28, CA-30 |
| 15.0 | TU-32, TU-33, TI-14 (regressão), TI-01 a TI-29 (regressão), E2E-01 a E2E-07, E2E-12 (meta não atendida com 2 CPUs) | CA-05, CA-06, CA-26, CA-28 |
| 16.0 | E2E-12 (4 CPUs), E2E-13, E2E-14, E2E-08 e E2E-09 (só 1k), E2E-01 a E2E-07, E2E-10 e E2E-11 (regressão) | CA-12, CA-23, CA-26, CA-27, CA-30 |
