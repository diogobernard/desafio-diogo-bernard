# Tarefa 15.0: Teste do POST isolado e latência máxima de 3 s por requisição

## Visão geral

Depois da tarefa 14, o cenário combinado de carga (POST com GET imediato e o worker emitindo na mesma máquina) aceitava 100% das rajadas, mas com `POST` p50 de 12 s a 16 s no 10k. O diagnóstico com JFR mostrou que a latência era computacional: a API com 2 CPUs ficava limitada pela cota do cgroup durante a rajada, disputando CPU com os `GET` e com a compilação da JVM. O banco não era o limite (lotes de ~33 ms, no máximo 2 conexões de escrita), e trocar o PostgreSQL por outro banco não mudaria o resultado.

Em produção, escrita, consulta e consumo rodariam em instâncias dedicadas. Esta tarefa cria um teste que mede **só o `POST`**, com o worker parado e sem consultas, e persegue a meta definida pelo usuário: **100% dos `POST` aceitos e latência máxima de cada requisição até 3 s** nas rajadas de 1k, 5k e 10k a 1.000 req/s, com consistência da escrita verificada no banco.

**Fora do escopo:** testes isolados de consumo e de `GET` (o usuário pediu só o `POST` nesta análise), mudar o limite de CPU do ambiente de referência e trocar o banco de dados.

<rules>
### Conformidade com o AGENTS.md e as rules

- `code-standards.md` e `kotlin.md`: as simulações Gatling seguem os limites de arquivo e função; o protocolo comum fica em `CargaComum.kt`, sem genéricos próprios.
- `clean-architecture.md`: nenhuma regra de negócio muda; o hash do pedido continua no `domain`, e o rastreamento continua no `app`.
- `unit-tests.md`: teste de valor de referência para o hash (TU-32) e teste do registro do outbox (TU-33); 100% de linhas e branches no Kover.
- `logs.md`: o log "Solicitação recebida" mantém o `solicitacaoId` no MDC e no span; nenhum dado sensível entra nos relatórios de carga.
- `gradle.md`: `-PloadScenario` é lido na configuração da tarefa `executarCarga`, com validação do valor.
- `CLAUDE.md` ("não altere infraestrutura sem necessidade"): o `docker-compose.yml` só passa a repassar `SERVER_TOMCAT_ACCEPT_COUNT`, com o padrão do Spring (100); os limites de CPU e memória não mudam.

Desvios e decisões:

- O teste mede a instância aquecida (3.000 `POST` antes das rodadas, `LOAD_POST_AQUECIMENTO`). Com 2 CPUs, a JVM fria não sustenta 1.000 `POST`/s enquanto compila o código, e a meta só foi cumprida a frio com 4 CPUs. Em produção, isso pede readiness depois do aquecimento ou *slow start* no balanceador.
- `server.tomcat.accept-count` de 4.096 entra no perfil de carga (como o `max-connections` da tarefa 14), e o Compose mantém o padrão de 100; o README e o ADR-007 recomendam o valor maior em produção.
- O gravador em lote gasta CPU no Hibernate e no SpEL (~17% das amostras do JFR), mas trocar a query nativa do repositório por `JdbcClient` contraria o ADR-006 e não foi necessário para a meta.
- A primeira execução da suíte de integração travou no deadlock conhecido entre o reinício de listeners Kafka e o appender do Logback no JDK 21 (contexto pausado e reiniciado pelo cache de testes do Spring 7). A pedido do usuário, as suítes de integração e E2E passaram a usar `spring.test.context.cache.pause=never` (`spring.properties` de cada source set): nenhum contexto é pausado nem religado. Como os contextos em cache continuam consumindo do mesmo Kafka, os que têm consumidores e configuração própria (`FalhaConsumidor`, `ActuatorPortaGerenciamento`, `WorkerRuntime` e `ModoPorDentroE2ETest`) usam tópicos exclusivos, e o exportador de traces em memória foi para a `PostgresIntegrationBase`, o que tira o contexto próprio da `ObservabilidadeIntegrationTest`. Sem o isolamento, 3 testes falhavam por eventos processados no contexto errado.
</rules>

## Subtarefas

- [x] 15.1 Cenário de POST isolado: `IngressoCargaSimulation` (asserções de 100% aceito e máximo de `LOAD_POST_MAX_MS`, padrão 3.000 ms), `CargaComum.kt`, `scripts/carga-post.sh` (worker parado, aquecimento, consistência da escrita com `scripts/verificar-ingresso.sql`), `-PloadScenario=combinado|post` e tabela própria no relatório.
- [x] 15.2 Coletores compartilhados (`scripts/lib-carga-coleta.sh`), chaves de experimento (`scripts/lib-carga-experimento.sh`: JFR, CPUs, memória, opções da JVM, aquecimento, `LOAD_EXPERIMENTO`), seleção da simulação com `--simulation` e limpeza dos volumes do Compose isolado.
- [x] 15.3 Diagnóstico com JFR e contadores de transbordo da fila de conexões (`ListenOverflows`/`ListenDrops` do container da API).
- [x] 15.4 Menos CPU por `POST`: `HexFormat` no hash do pedido (com valor de referência), correlação do log no span HTTP atual (`CorrelacaoObservabilidade.anotar`) e `traceparent` do span atual no outbox (sem `OutboxTracing.preparar`).
- [x] 15.5 Fila de conexões: `SERVER_TOMCAT_ACCEPT_COUNT` no Compose (padrão 100) e 4.096 no perfil de carga.
- [x] 15.6 Rodadas oficiais do POST isolado (duas, com 2 CPUs), regressão do cenário combinado em 1k e documentação (README, TechSpec, ADR-007, `AGENTS.md` e seção do POST isolado no relatório). As duas rodadas oficiais aceitaram 100% dos `POST`, sem transbordo da fila de conexões e com a escrita íntegra, mas não cumpriram a meta de 3 s (10k: máximo de 15,5 s e 25,3 s) com o Mac ocupado (média de carga de 15 a 20). A meta, a análise detalhada no relatório e as evidências passam para a tarefa 16.0, com a instância de escrita em 4 CPUs.
- [x] 15.7 Validação: unitários, integração, `koverVerify`, `ktlintCheck`, `detekt` e E2E.

## Testes

- [x] TU-32 `mantem o hexadecimal das impressoes ja gravadas`
- [x] TU-33 `executa o registro do outbox dentro de uma observacao`
- [x] TI-14 `propaga trace da requisicao ate os quatro consumidores` (regressão com o `traceparent` do span HTTP)
- [x] TI-01 a TI-29 (regressão), E2E-01 a E2E-07
- [x] E2E-12 `post isolado em 1k 5k e 10k`: executado nas duas rodadas oficiais com 2 CPUs; meta de 3 s não atendida, repetido na tarefa 16.0 com 4 CPUs

## Critérios de sucesso

- Rajadas de 1k, 5k e 10k a 1.000 req/s com 100% dos `POST` aceitos e nenhuma requisição acima de 3 s. **Não atendido com 2 CPUs nas rodadas oficiais.** Cumprido nos experimentos com o host livre (2 de 2), mas sem margem para um host ocupado. Transferido para a tarefa 16.0, com 4 CPUs.
- Cada solicitação aceita com exatamente um evento no outbox, sem chave de idempotência nem evento repetidos.
- Nenhuma mudança de contrato HTTP, de regra de negócio ou dos limites do ambiente de referência.
