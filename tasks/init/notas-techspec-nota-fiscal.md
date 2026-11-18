# Notas para /criar-techspec — API de Emissão de Nota Fiscal

Decisões de implementação tomadas durante a definição do PRD. Elas não entram no PRD.

## Arquitetura e fluxo
- Um deployable com os endpoints `POST` de emissão, `GET /issuance-requests/{id}` e `GET /invoices/{numero}`, mais o worker assíncrono, que pode ser o mesmo artefato com outro perfil.
- O domínio fica separado do contrato de entrada: um DTO e um mapper numa camada própria. As regras de cálculo só conhecem o domínio (`Pedido`, `Item`, `Endereco`...).
- `POST`: validação → **uma única transação** (solicitação + chave de idempotência + outbox) → `202`.
- **Transactional outbox** com **job de polling** (`SELECT ... FOR UPDATE SKIP LOCKED`), que funciona com várias instâncias. O CDC com Debezium aparece só no desenho, como evolução (ADR com o trade-off).
- Kafka: tópico de solicitações → worker (cálculo, numeração, persistência da nota e outbox `NotaEmitida`, tudo na mesma transação) → tópico de notas emitidas.
- 10 partições, chave = id da solicitação. Replicação 1 localmente e 3 no MSK, no desenho.
- **4 consumer groups distintos** (estoque, registro, entrega, financeiro), cada um com retry e DLQ próprios. A latência simulada não bloqueia threads.

## Identificadores e numeração
- Id da solicitação: Snowflake ou UUIDv7.
- Número fiscal: **tabela contadora por série**, com `UPDATE ... SET ultimo = ultimo + 1 RETURNING` na mesma transação da nota. Sem lacunas e sem duplicidade. Para escalar, várias séries.
- Não usar `SEQUENCE`: ela pode deixar lacunas, porque o `nextval` não é desfeito no rollback.
- Idempotência: a chave fica na tabela de solicitações, com restrição única, junto com o hash do payload para detectar uma chave reutilizada com payload diferente.

## Concorrência e desempenho
- Virtual threads (Java 21). **Nada de `synchronized` em caminho quente**, por causa do pinning no JDK 21, que só foi corrigido pela JEP 491 no JDK 24. Se um lock local for necessário, usar `ReentrantLock`.
- O ideal é não ter lock na aplicação: a unicidade e a sequência ficam no banco.
- Detectar pinning com `-Djdk.tracePinnedThreads=full` ou com o evento JFR `jdk.VirtualThreadPinned`.
- Dimensionar o HikariCP: com virtual threads, o gargalo passa a ser o pool de conexões e o número de partições.
- Driver pgjdbc atualizado (as versões recentes trocaram `synchronized` por locks).
- Cálculo em memória, sequencial e numa função pura. Não usar coroutines para o cálculo, porque o custo de criação e sincronização é maior que o próprio cálculo, e a faixa depende do total.

## Valores monetários
- Ler o JSON como `BigDecimal`, **nunca como `Double`** (por exemplo, `USE_BIG_DECIMAL_FOR_FLOATS` no Jackson).
- Estratégia de tributação atrás de uma interface (`POR_FORA` e `POR_DENTRO`), selecionada por propriedade (por exemplo, `nota-fiscal.tributacao.modo`).
- Tabelas de alíquota e frete como dados e configuração, e não como `if`s espalhados.

## Leitura com réplica
- Datasource de leitura separado, roteado por `@Transactional(readOnly = true)` (por exemplo, `AbstractRoutingDataSource` com `LazyConnectionDataSourceProxy`). Sem réplica configurada, o datasource de leitura aponta para a primária.
- POST, worker, numeração e idempotência usam sempre a primária.
- **Fallback filtrado pelo horário do id** (Snowflake/UUIDv7 carrega o horário de criação). Quando o GET não encontra o id na réplica:
  - id criado há mais de N segundos (por exemplo, 60 s, configurável): `404` direto, **sem consultar a primária**;
  - id recente: consulta a primária, protegida pelo rate limit do gateway.
  - Trade-off registrado em ADR. A alternativa descartada é um cache Redis com escrita após o commit, TTL e o id do cliente: tira 100% da carga da primária, mas traz dupla escrita, mais infraestrutura e um modo de falha a mais.
- Status em `EMITIDA`: `200` com o número e o link (`links.nota: /invoices/{numero}`, opcionalmente também no header `Link`), sem o corpo da nota. Assim a nota tem uma única representação.
- `GET /invoices/{numero}`: a nota é imutável depois de emitida, então a resposta leva `Cache-Control: private, max-age=...` e `ETag`.
- Consistência do link: se o status `EMITIDA` foi lido na réplica, a nota também já está nela, porque as duas são gravadas na mesma transação. Se o status veio do fallback na primária, o link pode apontar para uma nota que a réplica ainda não tem. Por isso, o `GET /invoices/{numero}` também tem fallback para a primária quando `numero ≤ último número emitido`, com esse valor lido do contador, que está em cache por alguns segundos. Acima disso, o `404` é real. A busca é feita só pelo número, e a checagem do dono vem depois: assim, a nota de outro cliente é encontrada na réplica e devolve `404` sem gerar fallback na primária.
- Os eventos `NotaEmitida` carregam a nota completa, para que os consumidores não precisem consultar a API.
- O `202` devolve `Location: /issuance-requests/{id}` e `Retry-After`. O GET em `RECEBIDA` ou `PROCESSANDO` também devolve `Retry-After` (fixo; calcular pelo tamanho da fila é opcional).
- Métricas: lag de replicação, taxa de fallback por motivo (recente ou antigo) e `404` falsos (precisam ser zero).
- Desenho AWS: RDS read replica ou reader endpoint do Aurora.
- `docker compose`: a réplica com streaming replication fica num profile opcional (`--profile replica`), para medir lag e fallback no teste de carga. Sem esse profile, tudo roda na primária.

## API
- Erros em Problem Details (RFC 9457), com erros por campo.
- OpenAPI/Swagger.

## Testes
- Unitários do domínio: tabelas de faixa, limites (499,99 / 500,00 / 2.000,00 / 2.000,01...), HALF_EVEN, soma das linhas igual ao total e os dois modos de tributação.
- Integração com Testcontainers (Postgres e Kafka): pedidos em sequência e em paralelo sem acumular itens, numeração concorrente sem duplicidade nem lacunas, idempotência concorrente e garantia de que o outbox publica.
- **Teste dedicado de numeração:** vários workers e várias instâncias numerando em paralelo. As asserções são `count(distinct numero) = count(*)` (sem duplicidade) e `max − min + 1 = count(*)` (sem lacunas).
- **Carga (Gatling com DSL Kotlin, ou k6):**
  - **Modelo aberto** (taxa de chegada constante, meta de 1.000 req/s) para evitar coordinated omission.
  - Rodadas de 1k, 5k e 10k solicitações, com warm-up da JVM excluído da medição. Rodada opcional de 100k como soak curto.
  - Executável por um único comando, com o relatório versionado no repositório.
- **Métricas do fluxo de emissão (POST e worker):**
  - vazão;
  - p50, p95, p99 e máximo do POST;
  - taxa de erro por código;
  - tempo até emissão (`emitida_em − recebida_em`), medido **pelo banco** e não por polling no GET;
  - tempo de escoamento;
  - pico do outbox pendente e do lag do Kafka;
  - tempo de espera por lock na numeração;
  - duplicidades e lacunas, verificadas por SQL ao fim de cada rodada;
  - heap após GC e CPU por rodada.
- **GETs:** p50, p95 e p99 por nível de carga, executados **junto com** a carga de POST (leitura sob escrita).
- Relatório comparativo entre 1k, 5k e 10k.
- Cobertura de 100% no domínio, com o CI bloqueando abaixo disso.

## Observabilidade
- OpenTelemetry (traces POST → outbox → Kafka → consumidores), métricas Micrometer (de negócio e de lag) e logs JSON com mascaramento de CPF/CNPJ.

## Segurança
- Autenticação no API Gateway (OAuth2 client credentials, JWT, Cognito). A aplicação acessível só pelo gateway (VPC Link/integração privada), e a identidade do cliente propagada por header injetado pelo gateway.

## Entrega
- `docker compose up` (Postgres, Kafka/Redpanda, aplicação e, opcionalmente, Grafana).
- GitHub Actions: build, testes, cobertura e análise estática (por exemplo, detekt/ktlint).
- Desenho de arquitetura AWS (API Gateway, Cognito, ECS Fargate ou EKS, RDS Postgres, MSK) e plano de deploy (estratégia blue/green ou canary, migrations Flyway compatíveis com a versão anterior, rollback).
