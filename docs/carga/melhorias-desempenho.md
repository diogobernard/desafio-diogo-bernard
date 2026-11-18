# Melhorias propostas para a lentidão da carga

Proposta de melhorias, com o estado depois da tarefa 14. A base do diagnóstico é a rodada de 10.000 solicitações `20261116001800664`, com API e worker de 2 CPUs e 2 GiB cada (ver [relatório](relatorio.md) e [`evidences/runtime-10k/`](../../tasks/prd-emissao-nota-fiscal/evidences/runtime-10k/)). A hipótese de **separar API e worker** já foi testada na tarefa 11 e não resolveu a saturação (ver [`diagnostico-pool.md`](../../tasks/prd-emissao-nota-fiscal/evidences/diagnostico-pool.md)).

| Item | Estado | Resultado medido (rodada de 10k, [relatório](relatorio.md)) |
|---|---|---|
| 1. Transação do `POST` em um comando | Implementado (tarefa 13) | Um comando antes do commit (TI-24). O ganho isolado não aparece: sob sobrecarga a retenção de 3 a 7 s é dominada pela espera por CPU, e não pelas idas e voltas ao banco. |
| 2. `GET` de solicitação sem `SELECT *` | Pendente | Fora do escopo da tarefa 13. |
| 3. Publicador do outbox em lote | Implementado (tarefa 13) | Pico de outbox de 15 contra 6.537; tempo até `EMITIDA` p50 de 2,3 s contra 55 s. |
| 4. Janela de lock da numeração | Pendente | Sem sinal de gargalo nesta escala (emissão p50 de 2,3 s); reavaliar se a vazão aceita subir. |
| 5. Limite de ingresso | Implementado (tarefa 13), substituído na tarefa 14 | Na tarefa 13: zero `500` por esgotamento do pool, mas aceitação de 19% da rajada. Na tarefa 14 o `Semaphore` virou a fila limitada do item 8. |

| 6. Custo de observabilidade | Implementado (tarefa 13) | Amostragem configurável (10% na carga), gauge do outbox por tópico, em cache e fora do pool de escrita. O efeito do tracing na CPU não foi isolado das demais mudanças. |
| 7. Ajustes de JVM e PostgreSQL | Pendente | Fora do escopo da tarefa 13. |
| 8. Fila limitada com gravação em lote | Implementado (tarefa 14) | 100% dos `POST` aceitos em 1k, 5k e duas rodadas de 10k (0 KO); a API usa 2 conexões de escrita, com no máximo 0,72 s de uso e nenhum pendente. `POST` p50 de 12,6 a 16,5 s no 10k. Ver a conclusão abaixo. |

## Diagnóstico

O gargalo está na aplicação, não no banco: as conexões ficam presas enquanto a API espera CPU.

| Sinal no pico do ingresso (00:18:02 a 00:18:11) | Valor | Leitura |
|---|---|---|
| CPU do PostgreSQL | 4% a 12% (limite de 200%) | O banco quase não trabalhou |
| CPU da API | 203% a 211% (limite de 200%) | A API está saturada |
| Sessões `idle in transaction` | 16 a 19 de 25 conexões da API; a mais antiga com 6,3 s | O banco terminou o comando e espera o próximo da aplicação |
| Pool de escrita da API | active=25, pending até 8.130, uso máximo de 10,7 s | Uma transação de dois INSERTs (~1 a 2 ms no banco) segura a conexão por ~70 ms em média |
| Memória da API | 1,805 GiB de 2 GiB (heap máximo de 75%) | Cerca de 8 mil requisições em espera e spans de tracing em memória |
| Falhas do POST | 577 `PrematureClose` com 15,0 s exatos e 1 HTTP 500 em 30,7 s | Conexões fechadas por um timeout fixo de rede, mais um timeout de 30 s do Hikari (`timeout_count=1`) |

Vazão observada: cerca de 9.422 POSTs aceitos em ~27 s, ou ~350/s, contra 1.000/s injetados em modelo aberto. A fila cresce sem limite. Por isso a latência sobe com o volume (POST p50 de 2,6 s em 1k, 9,3 s em 5k e 20,4 s em 10k) e, em 10k, passa do ponto em que as conexões HTTP começam a ser fechadas. O total em andamento (8.130 esperando mais 25 ativas) fica perto de `server.tomcat.max-connections`, cujo padrão é 8.192.

Os índices e o tipo da chave primária não explicam a falha. O `id` da `solicitacao` é UUIDv7, ordenado por tempo, e entra sempre no fim do B-tree. Com 10 mil linhas, as tabelas e os índices cabem inteiros em `shared_buffers`. Pelas regras de idempotência do PRD e da TechSpec, a `Idempotency-Key` é gerada pelo cliente e vale por cliente, então não pode ser a chave primária.

### Por que a conexão fica presa

1. **Concorrência sem limite.** Com virtual threads, o Tomcat aceita todas as requisições ao mesmo tempo. Parsing do JSON, validação, SHA-256 da impressão digital e serialização do pedido disputam os 2 CPUs.
2. **Idas e voltas dentro da transação.** O POST (`IssuanceRequestPostgresGateway.registerOnce`) faz INSERT da solicitação, trabalho de CPU, INSERT do outbox e COMMIT. A cada resposta do banco, a virtual thread volta para o fim da fila do escalonador, atrás de milhares de outras, e a conexão fica ociosa e reservada.
3. **Trabalho de CPU com a conexão na mão.** Dentro da transação ainda se gera o `eventoId` (`SecureRandom`), se monta o JSON do evento e se abre uma `Observation` com tracing amostrado em 100%.
4. **Pressão de memória (a confirmar).** Com o heap perto do limite, pausas de GC congelam todas as threads que seguram conexão.

## Melhorias, por ordem de impacto esperado

### 1. Encurtar a transação do POST, mantendo `@Transactional` — implementado na tarefa 13

- Fazer **antes** da transação todo o trabalho que não depende do banco: o `id` e o `recebida_em` já vêm do caso de uso, então dá para gerar antes o `eventoId`, os JSONs do pedido e do evento e o `traceparent`.
- Gravar a solicitação e o evento em **um único comando** dentro da transação, com uma CTE que grava dados:

  ```sql
  WITH nova AS (
    INSERT INTO solicitacao (...) VALUES (...)
    ON CONFLICT (cliente_id, idempotency_key) DO NOTHING
    RETURNING id
  )
  INSERT INTO outbox (evento_id, topico, chave, payload, traceparent, criado_em)
  SELECT :eventoId, :topico, nova.id::text, CAST(:payload AS jsonb), :traceparent, :criadoEm
  FROM nova
  RETURNING chave
  ```

  Se retornar 1 linha, a solicitação é `Created`; se retornar 0, é `Existing`, e segue o `SELECT` da solicitação existente, como hoje. O comando e o COMMIT dão duas idas e voltas em vez de três, sem CPU entre elas. A transação continua explícita, então não há autocommit.
- Com JPA (tarefa 12), a CTE fica como `@Query(nativeQuery = true)` no `IssuanceRequestRepository`.
- **Medir:** `usage_max_ms` e pending do pool da API, sessões `idle in transaction`, POST p50/p99.

### 2. GET de solicitação sem `SELECT *` nem desserializar o pedido — implementado em 17/11/2026

`ReadPostgresGateway.buscarSolicitacao` lia e desserializava o `pedido` jsonb inteiro (até 500 itens), mas a `IssuanceRequestResponse` só usa id, cliente, status, datas, nota e motivo. Agora a consulta JPQL `findStatus` projeta só essas colunas em `IssuanceRequestStateRecord`, e o domínio devolve `IssuanceRequestState`, sem o pedido.

No mesmo dia, o `GET /invoices` deixou de desserializar o documento `conteudo` (jsonb), removido pela migração `V4`: a nota é lida das colunas de `nota_fiscal` e de `nota_fiscal_item` e convertida uma única vez para o domínio.

- **Medir:** GET p95/p99 e uso do pool de leitura (`-PloadScenario=consulta`).

### 3. Publicador do outbox em lote de verdade — implementado na tarefa 13

`OutboxPublisherRepository.publish` faz `kafka.send(...).get()` e um `UPDATE` **para cada evento**, dentro de uma transação que trava até 500 linhas. São até 1.000 idas e voltas sequenciais por lote. Isso explica o pico de 9.189 itens pendentes no outbox, as 7.875 solicitações pendentes e a emissão com p50 de 47,8 s: o evento da solicitação espera no outbox antes de chegar ao worker.

- Enviar todos os registros do lote sem bloquear, aguardar todos os futures e marcar tudo com um único `UPDATE outbox SET publicado_em = now() WHERE id = ANY(:ids)`.
- Avaliar um publicador por tópico (solicitações e notas), para que as notas emitidas não disputem o mesmo lote com novas solicitações.
- **Medir:** `notafiscal.outbox.pendentes`, tempo até `EMITIDA` (p50/p95/p99), lag Kafka.

### 4. Janela de lock no contador de numeração — pendente

Em `ProcessingPostgresGateway.issue`, o lock da linha de `contador_numeracao` vai do `reserve` até o commit e cobre o INSERT da nota (jsonb), o lote de itens, o outbox e o UPDATE da solicitação. Isso serializa todas as emissões da série. Uma lacuna na numeração não é aceitável (RF39/RF40), então o objetivo é reduzir a janela:

- reservar o número o mais tarde possível;
- juntar as gravações que dependem do número em menos comandos;
- avaliar várias séries por partição, como já previsto nos riscos da TechSpec.

- **Medir:** `notafiscal.numeracao.espera_lock` e a vazão de emissão.

### 5. Limite de concorrência e de conexões no ingresso — implementado na tarefa 13 e substituído na tarefa 14

A tarefa 14 trocou o semáforo por uma fila limitada com escritores em lote (item 8, ADR-007); o texto abaixo descreve a proposta original.

Sem limite, a fila de requisições cresce até esgotar memória e conexões.

- **Bulkhead** em `app/config/resilience`, como decorador da porta `RequestIssuance` (conforme `folder-structure.md`): um semáforo com fila curta e espera máxima menor que o timeout do Hikari. Quando saturado, responde `503` com `Retry-After`.
- Revisar `server.tomcat.max-connections`, `accept-count` e `spring.datasource.hikari.connection-timeout` para falhar de forma previsível e rápida, em vez de deixar as conexões presas por 15 a 30 s.
- Uma rejeição com `503` continua contando como KO no Gatling. Esta medida limita o dano; zerar os KOs a 1.000/s depende de aumentar a vazão com os itens 1 a 4.
- **Medir:** memória e CPU da API, distribuição de status HTTP, ausência de `PrematureClose`.

### 6. Custo da observabilidade sob carga — implementado na tarefa 13

- `management.tracing.sampling.probability: 1.0` gera um span por requisição, transação e mensagem. Na carga, usar uma amostragem menor (por exemplo, 0,1) ou amostragem por razão no gateway.
- A `Observation` de `OutboxTracing.register` fica dentro da transação do POST; depois do item 1, ela pode cobrir só o preparo.
- O gauge `notafiscal.outbox.pendentes` executa `count(*)` no pool de **escrita** a cada coleta do Prometheus; ele pode usar o pool de leitura ou um valor em cache.
- Log ECS por requisição: avaliar o volume na carga.
- **Medir:** CPU da API com e sem tracing completo.

### 7. Ajustes de JVM e PostgreSQL, depois da aplicação — pendente

- JVM: avaliar `-XX:MaxRAMPercentage`, o tamanho do heap e o coletor para a API com 2 GiB, depois de reduzir as requisições em espera.
- PostgreSQL: `shared_buffers`, `max_connections` e `synchronous_commit` estão no padrão; o banco não é o gargalo hoje, mas pode passar a ser depois das melhorias.

## Efeito da migração para Spring Data JPA (tarefa 12)

A migração não resolve a lentidão e tende a aumentar o custo de CPU por requisição (contexto de persistência, dirty checking, proxies). Para não piorar:

- implementar `Persistable` nos ids atribuídos, para o `save()` não fazer `merge` com `SELECT` antes do `INSERT`;
- usar `SEQUENCE` no outbox e `hibernate.jdbc.batch_size` para manter os inserts em lote;
- manter `open-in-view=false` e usar projeções nas leituras;
- deixar os caminhos quentes (itens 1, 3 e 4) em consultas nativas dos repositórios.

Comparar a rodada de 10k depois da tarefa 12 com a rodada `20261116001800664` antes de aplicar as melhorias, para separar o efeito do JPA do efeito de cada melhoria.

## Conclusão da rodada de 10k da tarefa 13 e próximos passos

Os itens 1, 3, 5 e 6 removeram o gargalo do publicador e trocaram esperas longas, `500` e fechamentos de conexão por `503` documentados. Mas o `503` **não aumenta a vazão**: a API de 2 CPUs aceita só 19% de uma rajada de 1.000 req/s, e permanece em ~220% de CPU mesmo recusando 80% dos pedidos. O pool de escrita fica com as 25 conexões em uso por 3 a 7 s enquanto o banco está ocioso. A hipótese mais provável, ainda **não medida**, é que o trabalho anterior à vaga (leitura do corpo, conversão e a resposta `503`) disputa as CPUs com as threads que já detêm uma vaga. Próximos passos, em ordem:

1. Medir com JFR (CPU e `jdk.VirtualThreadPinned`) uma rodada de 10k para confirmar onde a CPU é gasta.
2. Se confirmada, avaliar mover o limite para antes da leitura do corpo (interceptador HTTP em `app/config/resilience`), o que exige atualizar o ADR-007.
3. Reavaliar os itens 2, 4 e 7 só depois disso, e repetir a rodada com a JVM aquecida, para separar o efeito do aquecimento.

## Conclusão da tarefa 14 e próximos passos

A fila limitada com gravação em lote (item 8) atingiu a meta de aceitar 100% dos `POST` nas rodadas de 1k, 5k e 10k (duas rodadas de 10k), com 0 KO no `POST`, numeração íntegra e nenhuma solicitação perdida. O pool de escrita deixou de ser gargalo (2 conexões, nenhum pendente). O custo é a latência do `POST` na rajada (p50 de 12,6 a 16,5 s no 10k) e o gargalo passou para a leitura e a CPU da API. Problemas medidos que ficam fora da meta, em ordem:

1. **`GET` imediato.** O pool de leitura (10 conexões) acumulou ~7.200 a ~7.600 pendentes no 10k, e em uma das duas rodadas 97 `GET` passaram do `connection-timeout` de 30 s e viraram `500`. Avaliar a projeção do `GET` de solicitação (item 2) e a retenção da conexão enquanto a thread espera CPU. **Aplicado em 17/11/2026:** a réplica recebeu os mesmos recursos da primária (2 CPUs e 1 GiB, antes 1 CPU), e o pool de leitura da API foi igualado ao de escrita (25 conexões, antes 10), com o orçamento agregado de leitura passando de 20 para 35. A consulta isolada ainda precisa ser medida de novo com essa configuração: na última rodada, ainda com 10 conexões, o 10k passou sem KO, mas a espera pelo pool chegou a 14,9 s.
2. **Memória da API.** Pico de 1,92 GiB para o limite de 2 GiB com a fila de 10.000; dimensionar memória e capacidade juntas.
3. **CPU da API** (235% a 300% no 10k): medir com JFR (CPU e `jdk.VirtualThreadPinned`) e, se confirmado, avaliar mover o limite para antes da leitura do corpo.
4. **Escoamento Kafka:** o lag não zerou em 300 s por causa dos consumidores simulados; reavaliar com concorrência maior para medir o escoamento completo.

## Diagnósticos a confirmar antes de implementar

- **GC:** rodar a API com `-Xlog:gc*:file=...` durante a carga e verificar pausas e frequência com o heap perto de 90%.
- **Pinning de virtual threads:** gravar JFR com o evento `jdk.VirtualThreadPinned` (ou `-Djdk.tracePinnedThreads=full`), principalmente em `SecureRandom` e no Hikari.
- **Origem dos 15 s:** confirmar qual camada fecha as conexões (encaminhamento de porta do Docker Desktop, Tomcat ou Gatling), por exemplo repetindo a carga com o Gatling na mesma rede do Compose.
- **Limite do Tomcat:** repetir a carga com `server.tomcat.max-connections` maior e menor e ver se o número de `PrematureClose` acompanha o limite.

## Como medir

Para cada melhoria, isoladamente:

```bash
./gradlew :app:loadTest -PloadRequests=10000
```

Comparar com a rodada `20261116001800664`:

- POST e GET p50/p95/p99/máx e KO por tipo;
- pending, `usage_max_ms` e `timeout_count` dos pools;
- sessões `idle in transaction` e transação mais antiga;
- CPU e memória por serviço;
- pico do outbox e lag Kafka;
- tempo até `EMITIDA`;
- integridade da numeração (duplicidades e lacunas).

## Tarefa 16 — isolamento por serviço e contenção do host

A decisão é medir `POST`, consumo e consulta separadamente, nesta ordem, com até 4 CPUs para a instância sob medição. O cenário combinado fica em 1k, com API e worker medidos em 4 CPUs e réplica ativa. Um override Compose temporário aplica `min(LOAD_INSTANCIA_CPUS, docker info NCPU)` e preserva os limites de 2 CPUs do Compose de referência. O perfil registra média de carga do host, CPU efetiva e arquivos GC/safepoint para distinguir saturação do serviço de pausa da JVM ou contenção externa. O início da rodada acima da quantidade de núcleos é sinalizado.

As duas rodadas oficiais da tarefa 15 em 2 CPUs não cumpriram 3 s (máximos de 15,5 s e 25,3 s) e começaram sob carga do host de 15–20. Experimentos de 4 CPUs passaram em duas de três rodadas, com uma pausa inexplicada da JVM na terceira. Essas evidências justificam o perfil de 4 CPUs na carga, sem alterar o `docker-compose.yml` de referência. A primeira tentativa E2E-13 da tarefa 16 em 2 de 10 concorrência ficou incompleta: o backlog quente emitiu 3.000 notas, mas notificou 761/12.000 em 300 s; a configuração de carga passou a usar concorrência 10 para a próxima medição. A evidência de falha é mantida em `tasks/prd-emissao-nota-fiscal/evidences/runtime-separados-tarefa16/`.
