# ADR-007: Fila limitada e gravação em lote no ingresso do `POST /issuance-requests`

- **Status:** aceito. Atualizado na tarefa 14: o mecanismo da tarefa 13 (`Semaphore` com `503`) foi
  substituído por uma fila limitada com escritores em lote; a decisão de recusar o excedente com
  `503` e `Retry-After` permanece.
- **Contexto:** com virtual threads, o Tomcat não limita a concorrência: toda requisição vira uma
  thread e disputa as 25 conexões de escrita da API e a CPU. Nas rodadas de 10k solicitações, a
  espera média por conexão chegou a ~9,5 s, a fila passou de 5 mil aquisições pendentes, o heap
  ficou perto do limite e o Gatling registrou fechamentos de conexão e `500` após 30 s (ver
  `tasks/prd-emissao-nota-fiscal/evidences/diagnostico-pool.md`). A tarefa 13 limitou a concorrência
  com um `Semaphore` justo (50 vagas, espera de 8 s): a espera virou uma fila implícita e sem
  tamanho, cada requisição ainda abria uma transação, e a rodada de 10k aceitou só 19% dos pedidos
  (1.897), recusando o resto com `503`. O limite recusava com rapidez, mas não aumentava a vazão.
- **Decisão:** um *bulkhead* com *backpressure* dentro do adaptador PostgreSQL
  (`gateway/postgres/lote`), sem mudar o caso de uso, o contrato `IssuanceRequestGateway` nem o `domain`.
  - Cada `POST` continua individual: a thread da requisição prepara o evento e o JSON, entrega a
    inserção à `IssuanceRequestWriteQueue` e bloqueia até o resultado. A resposta `202` só sai
    depois do commit da solicitação, da chave e do outbox, o que preserva a consulta imediata sem
    falso `404`.
  - A fila em memória tem `nota-fiscal.ingresso.capacidade` (padrão 1000) e
    `nota-fiscal.ingresso.espera-maxima` (padrão 8 s). Fila cheia: `503` na hora (`fila_cheia`).
    Prazo excedido: o item é cancelado e a requisição recebe `503` (`espera_excedida`). O
    `IngressCapacityExhaustedHandler` traduz para `503 application/problem+json` com `Retry-After`
    (`nota-fiscal.consulta.retry-after`), e o contador `notafiscal.ingresso.rejeitadas` leva o
    motivo como tag.
  - `nota-fiscal.ingresso.escritores` (padrão 2) threads de plataforma tiram da fila o primeiro item
    e os demais disponíveis (`drainTo`, até `nota-fiscal.ingresso.lote-maximo`, padrão 100), sem
    espera fixa: sob carga o lote enche sozinho, e em carga baixa tem um item e não acrescenta
    latência. Threads de plataforma evitam que os escritores disputem os carriers das virtual
    threads com as requisições.
  - `PostgresBatchPersister` (`@Transactional`) executa um único comando por lote: uma CTE com
    `unnest` que insere as solicitações com `ON CONFLICT (cliente_id, idempotency_key) DO NOTHING`,
    insere o outbox só das criadas e devolve os `evento_id` criados, o que informa, por item, se foi
    criado ou já existia (inclusive duplicatas dentro do mesmo lote).
  - Cada item tem uma decisão atômica: o escritor o **assume** antes de gravar, ou a requisição o
    **cancela** ao estourar o prazo. Só um vence, então um `503` por prazo nunca é seguido de uma
    gravação, e um item já assumido é aguardado até o commit.
  - Falha do lote por `DataIntegrityViolationException` (por exemplo, um caractere nulo no JSON): os
    itens são gravados um a um, e só o defeituoso falha. Qualquer outra falha (banco indisponível)
    falha todos os itens do lote com a mesma exceção, sem repetir a gravação.
  - Na parada, a fila deixa de aceitar (`503`, `encerrando`) e os escritores esvaziam o que já foi
    enfileirado. O ciclo de vida tem fase abaixo da parada graciosa do servidor web, para os escritores
    pararem depois das requisições em andamento.
  - O `connection-timeout` do pool de escrita continua configurável
    (`APP_DB_WRITE_CONNECTION_TIMEOUT`, padrão `10s`).
- **Resultado medido:** no perfil de carga (capacidade 10.000, espera de 50 s e Tomcat com 12.000
  conexões), as rodadas de 1k, 5k e duas de 10k aceitaram 100% dos `POST`, sem `503`, falha de
  transporte nem timeout, com numeração íntegra. A API usou 2 conexões de escrita (no máximo 0,72 s
  de uso e nenhum pendente, contra 25 pendentes e 5,3 s na tarefa 13). Em troca, o `POST` p50 foi de
  12,6 s a 16,5 s no 10k, e a maior espera chegou a 26,7 s. Detalhes e limitações em
  `docs/carga/relatorio.md`.
- **Calibração:** os padrões da aplicação (1000 e 8 s) são conservadores; com eles, uma rajada de 10k
  a 1.000 req/s só cabe inteira se a vazão de gravação passar de ~900 req/s, e o excedente recebe `503`.
  A espera do último pedido é, aproximadamente, `10.000 / vazão − 10 s`. O perfil de carga usa capacidade
  10.000 (a maior rajada do PRD) e espera de 50 s (abaixo do timeout padrão de 60 s do Gatling). Esses valores
  vêm de variáveis de ambiente (`scripts/carga.sh` e Compose) e não mudam os padrões.
- **Por que no `app` e no adaptador:** é um mecanismo técnico de persistência que não muda a semântica
  do contrato (`folder-structure.md`): o `domain` continua entregando uma solicitação por vez e recebendo
  criada ou existente. Ficou em `gateway/postgres/lote`, e não em `config/resilience` como decorador da porta
  `RequestIssuance` (como na tarefa 13), porque a fila é o próprio mecanismo de agrupamento da gravação:
  um decorador não conseguiria agrupar as gravações. A rejeição não gera log por requisição, só métrica.
- **Consequências:** a latência do `POST` sob rajada passa a ser limitada pela espera máxima, e a memória
  é limitada pela capacidade da fila: com 10.000 itens, a API chegou a 1,92 GiB de um limite de 2 GiB
  (~8.000 requisições paradas), então capacidade e memória precisam ser dimensionadas juntas. O limite é
  por instância, não global; requisições inválidas também entram na fila, porque a validação roda no caso
  de uso. O gargalo passou para a leitura: o `GET` imediato acumulou milhares de pendentes no pool de
  leitura (10 conexões) e, em uma rodada, 97 `GET` passaram do `connection-timeout` de 30 s.
- **Questão em aberto:** a CPU da API ficou em 235% a 300% nas rodadas de 10k. Mover o limite para antes da
  leitura do corpo da requisição e perfilar a CPU (JFR) continuam como hipóteses não medidas.
- **Alternativas rejeitadas:** aumentar o pool de conexões (o banco não é o gargalo e o orçamento agregado é
  de 30 conexões de escrita), limitar só `server.tomcat.max-connections` (o cliente recebe recusa ou
  fechamento de conexão, sem `Retry-After`) e responder `202` antes do commit (a consulta imediata daria
  falso `404` e um pedido aceito poderia se perder em uma queda).
- **Atualização (tarefa 15):** JFR confirmou que com 2 CPUs a API se aproxima da capacidade a 1.000 `POST`/s,
  enquanto o banco não é o limite: cada lote leva ~25–45 ms e usa no máximo 2 conexões de escrita. `accept-count`
  de 4.096 eliminou os transbordos da fila de conexões nas rodadas observadas, e o aquecimento de 3.000 POST
  melhorou os tempos. Os experimentos com 2 CPUs passaram em duas rodadas, mas as duas rodadas oficiais falharam
  a meta de 3 s (máximos de 15,5 s e 25,3 s), iniciadas com carga média do host de 15 a 20. Experimentos
  exploratórios com 4 CPUs passaram em duas de três rodadas; uma teve uma parada única da JVM de ~6–8 s, sem
  causa confirmada. Portanto a meta não foi cumprida oficialmente na tarefa 15.
- **Decisão (tarefa 16):** os testes de carga isolam `POST`, consumo e consulta, na ordem, com até 4 CPUs no
  serviço medido; o cenário combinado fica somente em 1k, com API e worker em 4 CPUs e réplica ativa. O perfil
  temporário é limitado às CPUs do Docker e não muda o Compose de referência, que continua com 2 CPUs por API e
  worker. Cada rodada registra CPU efetiva, carga média do host e logs de GC/safepoints. Quando a carga inicial
  do host supera os núcleos disponíveis, a evidência sinaliza a possível contenção e não atribui automaticamente
  uma parada à aplicação. Em produção, manter `accept-count` dimensionado para a rajada e aquecer as instâncias
  antes de tráfego cheio (readiness após aquecimento ou *slow start* no balanceador). O POST também deixou de
  criar duas observações próprias por requisição: o log e o outbox usam o span HTTP atual.
