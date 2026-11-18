# Especificação técnica — API de Emissão de Nota Fiscal

> PRD de referência: [`prd.md`](prd.md). Os requisitos (`RF*`), os critérios de aceitação (`CA-*`) e as metas estão no PRD e não são repetidos aqui.

## Resumo

A solução usa um único artefato Spring Boot 4.1 (Kotlin 2.3, Java 21 com virtual threads), organizado em dois módulos Gradle: `domain`, com as regras de cálculo, validação, numeração e consulta sem nenhum framework, e `app`, com os adaptadores HTTP, PostgreSQL (Spring Data JPA) e Kafka. O mesmo artefato pode iniciar em dois papéis de runtime independentes: `api` atende HTTP e `worker` executa o polling do outbox, o processamento de emissões e os consumidores Kafka. Cada processo tem pools Hikari próprios e limitados; no ambiente de referência, os limites somados do par preservam o orçamento atual de até 30 conexões de escrita e 20 de leitura. O `POST /issuance-requests` valida o pedido de forma síncrona e, em **uma transação**, grava a solicitação, a chave de idempotência e um registro de *outbox*, respondendo `202` em seguida. Um job de *polling* do outbox (`FOR UPDATE SKIP LOCKED`, seguro com várias instâncias) publica o evento no tópico de solicitações. O worker consome o evento, calcula a nota numa função pura, reserva o número fiscal numa tabela contadora por série e persiste a nota, os itens e o outbox `NotaEmitida`, tudo na mesma transação. Esse outbox é publicado no tópico de notas emitidas, consumido por quatro *consumer groups* independentes (estoque, registro, entrega e financeiro), cada um com retry e DLQ próprios e latência simulada sem bloquear threads.

As leituras usam uma réplica opcional. Quando um registro não está na réplica, o fallback para a primária é filtrado pelo horário embutido no id (UUIDv7) ou pelo último número emitido. Assim não há `404` falso, e ids antigos ou inexistentes não geram carga na primária. A unicidade e a sequência ficam no banco, sem locks na aplicação. Os valores monetários são `BigDecimal` do JSON até o evento, com escala 2 e `HALF_EVEN`, e o modo de tributação (`POR_FORA` ou `POR_DENTRO`) é uma estratégia escolhida por configuração.

## Arquitetura do sistema

### Visão dos componentes

**Módulos e pacotes.** Pacote-base `com.itau.desafio_diogo_bernard` (preservado do projeto atual). `settings.gradle.kts` passa a incluir `app` e `domain`, com a dependência `app → domain`. A classe `DesafioDiogoBernardApplication` é renomeada para `Application.kt`, no módulo `app`.

**`domain` (núcleo, sem Spring, JDBC/JPA, Kafka ou Jackson)**

| Pacote | Componentes | Função |
|---|---|---|
| `model/pedido` | `Order`, `Recipient`, `PersonType`, `TaxRegime`, `OrderItem`, `Address`, `AddressPurpose`, `Region`, `Document` | Pedido normalizado com invariantes. `Document.masked()` é usado nos logs. |
| `model/dinheiro` | `Money` (value class sobre `BigDecimal`, escala 2, `HALF_EVEN`), `TaxRate` | Aritmética monetária exata. |
| `model/nota` | `CalculatedInvoice`, `CalculatedItem`, `Invoice`, `FiscalNumber`, `Series`, `TaxationMode` | Resultado do cálculo e nota emitida. |
| `model/solicitacao` | `IssuanceRequest`, `IssuanceRequestId`, `IssuanceRequestStatus`, `ClientId`, `IdempotencyKey`, `Fingerprint` | Ciclo de vida `RECEBIDA → PROCESSANDO → EMITIDA \| FALHA`. |
| `usecase/validations` | `OrderInputValidator` (única validação do pedido, chamada pelo `IssuanceRequestController` antes da conversão do request; o caso de uso não revalida), `Violation` (`AUSENTE`, `MALFORMADO`, `IMPEDITIVO`), `ConsistentReadPolicy` | Regras de entrada e política de fallback de leitura. |
| `usecase/calculo` | `CalculateInvoice`, `TaxRateTable`, `FreightTable`, `TaxationStrategy` (`ExclusiveTaxation`, `InclusiveTaxation`) | Cálculo puro, sequencial e em memória. Tabelas como dados. |
| `usecase/emissao` | `RequestIssuance` + `RequestIssuanceUseCase` | Valida, calcula a impressão digital do pedido e registra uma única vez. |
| `usecase/processamento` | `ProcessIssuanceRequest` + impl.; `RecordIssuanceRequestFailure` + impl. | Transições de estado, cálculo e emissão. |
| `usecase/consulta` | `RetrieveIssuanceRequest`, `RetrieveInvoice` + impl. | Leitura com isolamento por cliente e fallback seguro. |
| `gateway` | `IssuanceRequestGateway`, `ProcessingGateway`, `IssuanceRequestReadGateway`, `InvoiceReadGateway`, `IssuanceRequestIdGenerator` | Portas de saída. |
| `exception` | `InvalidOrderException`, `IdempotencyKeyReusedException`, `NumberingExhaustedException`, `IssuanceRequestNotFoundException`, `InvoiceNotFoundException` | Falhas de negócio sem `HttpStatus`. |

**`app` (adaptadores e composição)**

| Pacote | Componentes | Função |
|---|---|---|
| `entrypoint/solicitacoes` | `IssuanceRequestController` (`POST /issuance-requests`, `GET /issuance-requests/{id}`) | Protocolo HTTP: headers, `Location`, `Retry-After` e conversão de DTOs. |
| `entrypoint/notas` | `InvoiceController` (`GET /invoices/{number}`) | Nota completa com `ETag` e `Cache-Control`. |
| `entrypoint/model` | `IssueInvoiceRequest`, `ItemRequest`, `AddressRequest`, `RecipientRequest`, `IssuanceRequestResponse`, `InvoiceResponse`, `InvoiceIssuedEvent` | DTOs com `toDomain`/`fromDomain`, sem Mapper. |
| `entrypoint/handlers` | `InvalidOrderHandler`, `IdempotencyKeyReusedHandler`, `ResourceNotFoundHandler`, `IngressCapacityExhaustedHandler`, `InvalidJsonHandler`, `ResponseStatusHandler`, `MissingHeaderHandler`, `InternalErrorHandler` | Um `@RestControllerAdvice` por exceção traduz para RFC 9457, com `errors[]` por campo. `InternalErrorHandler` (`Exception`) tem a menor precedência (`@Order`) para não esconder os demais; `problemResponse` monta a resposta comum. |
| `entrypoint/processamento` | `IssuanceRequestReceivedListener`, `ProcessingFailureRecoverer` | Worker Kafka que chama `ProcessIssuanceRequest`; esgotadas as tentativas, chama `RecordIssuanceRequestFailure` e envia para a DLQ. |
| `entrypoint/consumidores` | `InventoryListener`, `RegistryListener`, `DeliveryListener`, `FinancialListener` | Quatro *consumer groups* simulados, com retorno assíncrono (`CompletableFuture`). |
| `entrypoint/job` | `OutboxPublisherJob`, `OutboxCleanupJob` | Polling `SKIP LOCKED` por tópico → envio assíncrono do lote ao Kafka; remoção de registros publicados antigos. |
| `gateway/postgres` | `IssuanceRequestPostgresGateway`, `ProcessingPostgresGateway`, `ReadPostgresGateway`, `OutboxEventRecorder`, `OutboxPublication`, `NumberingCounter` | Implementam as portas do `domain` delegando aos repositórios Spring Data, com as transações (`@Transactional`) nas operações de gateway. |
| `gateway/postgres/lote` | `IssuanceRequestWriteQueue`, `IssuanceRequestBatchWriter`, `PostgresBatchPersister`, `PendingWrite` | Serviço interno do adaptador PostgreSQL que controla as requisições do `POST` e as grava em lote: fila limitada em memória com capacidade e espera máxima (*bulkhead* com *backpressure*), escritores em threads de plataforma que agrupam os itens enfileirados (`take` e `drainTo`, sem espera fixa) e um gravador transacional que executa um único comando por lote. Cada requisição continua individual: é tratada na própria thread e só recebe a resposta depois do commit do lote que a contém. |
| `repository` | `IssuanceRequestRepository`, `InvoiceRepository`, `InvoiceItemRepository`, `NumberingCounterRepository`, `OutboxRepository`, `ReceivedNotificationRepository` | Interfaces Spring Data JPA. Métodos derivados e JPQL no caso geral; `@Query(nativeQuery = true)` só onde o JPQL não tem o recurso (`ON CONFLICT`, `UPDATE ... RETURNING`). |
| `repository/entity` | `IssuanceRequestEntity`, `InvoiceEntity`, `InvoiceItemEntity`, `NumberingCounterEntity`, `OutboxEntity`, `ReceivedNotificationEntity` e os `@Embeddable` das chaves compostas | Mapeamento JPA das tabelas do Flyway, em classes regulares (não `data class`), com `toDomain`/`fromDomain`. Ids atribuídos implementam `Persistable`. |
| `gateway/mock` | `ConsumerSimulator` | Latência e falhas simuladas por consumidor, configuráveis, sem bloquear threads. |
| `gateway/id` | `UuidV7Generator` | Implementa `IssuanceRequestIdGenerator`. |
| `config` | `DataSourceConfig` (primária + `LazyConnectionDataSourceProxy` com *read-only data source*, usado pelo `EntityManagerFactory` e pelo `JpaTransactionManager`; `spring.jpa.open-in-view=false`, `spring.jpa.hibernate.ddl-auto=validate`, `hibernate.jdbc.batch_size` e `order_inserts`), `KafkaConfig` (tópicos, error handlers, DLQs), `JacksonConfig`, `UseCaseConfig`, `ObservabilityConfig` | Composição. O modo de tributação e as tabelas são escolhidos por propriedade. |
| `config/properties` | `NotaFiscalProperties` | Propriedades tipadas `nota-fiscal.*`. |
| `exception` | `IngressCapacityExhaustedException`, `IngressRejectionReason` | Falha técnica de capacidade do `app` (`fila_cheia`, `espera_excedida` ou `encerrando`), traduzida pelo `ProblemDetailsHandler` para `503` com `Retry-After`. |

**Fluxo de dados**

```text
Solicitante ──POST /issuance-requests──▶ IssuanceRequestController ─▶ RequestIssuance (domain) ─▶ IssuanceRequestPostgresGateway
                                        │  IssuanceRequestWriteQueue (capacidade e espera máxima): cheia ou prazo excedido = 503 + Retry-After
                                        │  escritores: um comando por lote (solicitacao + chave + outbox(SolicitacaoRecebida))
                                        ▼  a resposta de cada requisição só sai depois do commit do lote
                                   202 + Location + Retry-After
OutboxPublisherJob (por tópico) ──SKIP LOCKED──▶ envio assíncrono do lote
   ─▶ Kafka notafiscal.solicitacoes.v1 (10 partições, chave=id)
IssuanceRequestReceivedListener ─▶ ProcessIssuanceRequest (domain)
   tx1: status=PROCESSANDO
   cálculo puro em memória
   tx2: lock solicitação → UPDATE contador RETURNING → nota + itens → outbox(NotaEmitida) → status=EMITIDA
OutboxPublisherJob ─▶ Kafka notafiscal.notas-emitidas.v1 (10 partições, chave=id)
   ├─ group estoque     ─▶ retry ─▶ notafiscal.notas-emitidas.v1.estoque.dlq
   ├─ group registro    ─▶ retry ─▶ ...registro.dlq
   ├─ group entrega     ─▶ retry ─▶ ...entrega.dlq
   └─ group financeiro  ─▶ retry ─▶ ...financeiro.dlq
GET /issuance-requests/{id}, GET /invoices/{number} ─▶ réplica (readOnly) ─▶ fallback filtrado ─▶ primária
```

**Configuração por ambiente**

| Recurso | Local (`docker compose up`) | Testes de integração e E2E | AWS (desenho) |
|---|---|---|---|
| PostgreSQL primária | `localhost:5432`, banco `notafiscal`, schema `notafiscal`, usuário `notafiscal`, senha por `DB_PASSWORD` (`.env`, com `.env.example` contendo `<SENHA_FICTICIA>`) | Testcontainers `postgres:17`, porta aleatória | RDS PostgreSQL ou Aurora (writer endpoint) |
| PostgreSQL réplica | `localhost:5433`, streaming replication, só com `--profile replica`. Sem esse profile, `DB_READ_URL` aponta para a primária | Segundo container sem os dados, para simular lag | RDS read replica ou reader endpoint do Aurora |
| Kafka | `apache/kafka` em KRaft, `localhost:9092` (dentro da rede: `kafka:29092`), replicação 1 | Testcontainers `apache/kafka`, porta aleatória | MSK, replicação 3, `min.insync.replicas=2`, autenticação IAM |
| API | `localhost:8080`; actuator na porta de gerenciamento `localhost:8081` (`MANAGEMENT_SERVER_PORT`) | `RANDOM_PORT` (porta de gerenciamento aleatória quando o teste a exige) | ECS Fargate ou EKS, acessível somente pelo API Gateway (VPC Link); porta de gerenciamento só na rede interna |
| Worker | Serviço separado na rede Compose, sem rotas de negócio; actuator na porta de gerenciamento `localhost:8082` | Listener/worker em contexto de integração separado quando aplicável | ECS Fargate ou EKS, escalado independentemente da API |
| Observabilidade | Grafana LGTM opcional (`--profile observabilidade`), OTLP em `4318` | Exportador em memória | ADOT Collector → CloudWatch/X-Ray |
| Região | — | — | `sa-east-1` (premissa do desenho) |

Tópicos criados pela aplicação na inicialização (`NewTopic`): `notafiscal.solicitacoes.v1`, `notafiscal.solicitacoes.v1.dlq`, `notafiscal.notas-emitidas.v1` e `notafiscal.notas-emitidas.v1.<consumidor>.dlq` para cada consumidor, com 10 partições e replicação configurável.

## Design de implementação

### Principais interfaces

Portas de entrada (`domain/usecase`):

```kotlin
interface RequestIssuance {
    fun executar(comando: IssuanceCommand): SolicitacaoAceita
}

interface ProcessIssuanceRequest {
    fun executar(id: IssuanceRequestId)
}

interface RecordIssuanceRequestFailure {
    fun executar(id: IssuanceRequestId, motivo: FailureReason)
}

interface RetrieveIssuanceRequest {
    fun executar(clienteId: ClientId, id: IssuanceRequestId): Solicitacao
}

interface RetrieveInvoice {
    fun executar(clienteId: ClientId, referencia: InvoiceReference): Invoice
}
```

Portas de saída (`domain/gateway`):

```kotlin
interface IssuanceRequestGateway {
    /** Grava a solicitação e agenda o processamento numa operação atômica. Nunca duplica a chave. */
    fun registrarUmaVez(nova: NovaSolicitacao): IssuanceRequestRegistration
}

sealed interface IssuanceRequestRegistration {
    data class Criada(val solicitacao: Solicitacao) : IssuanceRequestRegistration
    data class Existente(val solicitacao: Solicitacao, val impressao: Fingerprint) : IssuanceRequestRegistration
}

interface ProcessingGateway {
    fun iniciarProcessamento(id: IssuanceRequestId): Solicitacao?
    /** Numera, persiste a nota e o evento e marca EMITIDA na mesma transação. Idempotente por solicitação. */
    fun emitir(id: IssuanceRequestId, nota: CalculatedInvoice): Invoice
    fun registrarFalha(id: IssuanceRequestId, motivo: FailureReason)
}
```

```kotlin
interface IssuanceRequestReadGateway {
    fun buscar(id: IssuanceRequestId): Solicitacao?
    fun buscarConsistente(id: IssuanceRequestId): Solicitacao?
}

interface InvoiceReadGateway {
    fun buscar(referencia: InvoiceReference): Invoice?
    fun buscarConsistente(referencia: InvoiceReference): Invoice?
    fun ultimoNumeroEmitido(serie: Serie): FiscalNumber?
}

interface TaxationStrategy {
    fun calcular(itens: List<OrderItem>, aliquota: Aliquota): CalculatedTax
}

interface TaxRateTable {
    fun aliquota(destinatario: Destinatario, base: Dinheiro): Aliquota
}
```

- `find` lê da réplica, `findConsistent` lê da primária, e o adaptador decide a origem. `ConsistentReadPolicy(janela, clock)` decide se o fallback é permitido: id criado dentro da janela (padrão de 60 s), pelo instante do UUIDv7, ou `numero ≤ ultimoNumeroEmitido`.
- `lastIssuedNumber` é lido da primária, com cache de alguns segundos. Quando o número pedido é maior que o valor em cache, o valor é relido uma vez, com *single-flight* (requisições concorrentes compartilham a mesma leitura). Isso evita o `404` falso causado por um cache desatualizado.
- `CalculateInvoice.calcular(pedido: Order, modo: TaxationStrategy): CalculatedInvoice` é uma função pura: não tem estado compartilhado nem I/O. Isso impede o acúmulo de itens do legado (RF6).
- A idempotência é semântica do caso de uso: o `RequestIssuanceUseCase` compara a `Fingerprint` do `Existing` e lança `IdempotencyKeyReusedException`. A atomicidade fica na restrição única do banco, no adaptador.

### Modelos de dados

Convenções: JSON em `snake_case` (`PropertyNamingStrategies.SNAKE_CASE`); datas em ISO-8601 UTC (`Instant`), obtidas de um `Clock` injetado; valores monetários como números JSON com 2 casas, em representação plana (`WRITE_BIGDECIMAL_AS_PLAIN`). O Jackson 3 (`tools.jackson`) é configurado por `JsonMapperBuilderCustomizer` com `USE_BIG_DECIMAL_FOR_FLOATS` habilitado e **`ACCEPT_FLOAT_AS_INT` desabilitado**, para que `quantity: 1.5` seja rejeitada em vez de truncada, e `FAIL_ON_UNKNOWN_PROPERTIES` desabilitado. Os DTOs de entrada têm campos anuláveis, para que a validação reúna todas as violações de uma vez.

#### `IssueInvoiceRequest` — corpo do `POST /issuance-requests`

| Campo | Tipo | Obrigatório | Descrição |
|---|---|---|---|
| `recipient` | `RecipientRequest` | sim | Quem recebe a nota. |
| `items` | `ItemRequest[]` | sim | De 1 a 500 itens. Vazio → `400`; mais de 500 → `422`. |
| `freight_value` | `decimal` | sim | ≥ 0. Mais de 2 casas → normalizado. |
| `addresses` | `AddressRequest[]` | sim | Exatamente um endereço com finalidade `ENTREGA` ou `COBRANCA_ENTREGA`. |

```json
{
  "recipient": { "person_type": "FISICA", "document": "12345678909", "tax_regime": null },
  "items": [
    { "code": "SKU-001", "description": "Teclado", "unit_price": 100.005, "quantity": 2 },
    { "code": "SKU-002", "description": "Monitor", "unit_price": 350.00, "quantity": 1 }
  ],
  "freight_value": 50.00,
  "addresses": [
    { "purpose": "COBRANCA", "region": "SUL", "street": "Rua A, 10", "city": "Curitiba", "state": "PR", "postal_code": "80000000" },
    { "purpose": "ENTREGA", "region": "SUDESTE", "street": "Av. B, 200", "city": "São Paulo", "state": "SP", "postal_code": "01000000" }
  ]
}
```

#### `RecipientRequest`

| Campo | Tipo | Obrigatório | Descrição |
|---|---|---|---|
| `person_type` | `FISICA` \| `JURIDICA` | sim | Valor fora do enum → `400`. |
| `document` | `string` | sim | CPF com 11 dígitos (PF) ou CNPJ com 14 dígitos (PJ), sem máscara. Nunca é registrado em log sem máscara. |
| `tax_regime` | `SIMPLES_NACIONAL` \| `LUCRO_REAL` \| `LUCRO_PRESUMIDO` \| `OUTROS` | se PJ | PJ sem regime → `400`; `OUTROS` → `422`. Em PF é ignorado (premissa). |

#### `ItemRequest`

| Campo | Tipo | Obrigatório | Descrição |
|---|---|---|---|
| `code` | `string` | sim | Identificador do produto, até 60 caracteres. |
| `description` | `string` | sim | Até 120 caracteres. |
| `unit_price` | `decimal` | sim | ≥ 0. Normalizado para 2 casas com `HALF_EVEN` (`100.005 → 100.00`). |
| `quantity` | `integer` | sim | Inteiro > 0. Decimal ou ≤ 0 → `400`. |

#### `AddressRequest`

| Campo | Tipo | Obrigatório | Descrição |
|---|---|---|---|
| `purpose` | `ENTREGA` \| `COBRANCA` \| `COBRANCA_ENTREGA` | sim | Exatamente um endereço de entrega (`ENTREGA` ou `COBRANCA_ENTREGA`). Nenhum ou mais de um → `400` (premissa do PRD). |
| `region` | `NORTE` \| `NORDESTE` \| `CENTRO_OESTE` \| `SUDESTE` \| `SUL` | sim | Só a região do endereço de entrega entra no frete. |
| `street`, `city`, `state`, `postal_code` | `string` | não | Informativos; não entram no cálculo. |

> **Contrato substituível:** se o payload oficial chegar, só os DTOs de `entrypoint/model` e seus `toDomain` mudam. `Order` e o cálculo permanecem iguais.

#### `IssuanceRequestResponse` — status da solicitação

| Campo | Tipo | Obrigatório | Descrição |
|---|---|---|---|
| `id` | `string` (UUIDv7) | sim | Identificador técnico, diferente do número fiscal. |
| `status` | `RECEBIDA` \| `PROCESSANDO` \| `EMITIDA` \| `FALHA` | sim | Estado atual. |
| `received_at` | `instant` | sim | Momento do aceite. |
| `updated_at` | `instant` | sim | Última transição. |
| `invoice` | `{number, series, issued_at}` | se `EMITIDA` | Resumo da nota, sem itens. |
| `reason` | `string` | se `FALHA` | Motivo legível, sem detalhes internos. |
| `links` | `{self, invoice?}` | sim | `invoice` só aparece em `EMITIDA`. |

```json
{
  "id": "01933b6e-7c1a-7d3e-9f10-2b6a4c8d0e11",
  "status": "EMITIDA",
  "received_at": "2026-11-14T18:00:00.120Z",
  "updated_at": "2026-11-14T18:00:00.410Z",
  "invoice": { "number": 1024, "series": 1, "issued_at": "2026-11-14T18:00:00.410Z" },
  "links": {
    "self": "/issuance-requests/01933b6e-7c1a-7d3e-9f10-2b6a4c8d0e11",
    "invoice": "/invoices/1024?series=1"
  }
}
```

> **Variante `RECEBIDA`/`PROCESSANDO`:** sem `invoice` e `reason`, com o header `Retry-After: 1`.

```json
{ "id": "01933b6e-...", "status": "PROCESSANDO", "received_at": "...", "updated_at": "...", "links": { "self": "/issuance-requests/01933b6e-..." } }
```

> **Variante `FALHA`:**

```json
{ "id": "01933b6e-...", "status": "FALHA", "reason": "Não foi possível emitir a nota. Envie um novo pedido com outra Idempotency-Key.", "received_at": "...", "updated_at": "...", "links": { "self": "/issuance-requests/01933b6e-..." } }
```

#### `InvoiceResponse` — nota completa da resposta HTTP

| Campo | Tipo | Obrigatório | Descrição |
|---|---|---|---|
| `number` | `integer` | sim | De 1 a 999.999.999 (nNF). |
| `series` | `integer` | sim | Série da numeração. |
| `request_id` | `string` | sim | Correlação com a solicitação. |
| `issued_at` | `instant` | sim | Data de emissão. |
| `taxation_mode` | `POR_FORA` \| `POR_DENTRO` | sim | Modo aplicado. |
| `tax_rate` | `decimal` | sim | Alíquota da faixa (ex.: `0.12`). |
| `recipient` | `{person_type, masked_document, tax_regime}` | sim | O documento sai mascarado (`***.456.789-**`). |
| `items` | `{code, description, unit_price, quantity, item_total, item_tax}[]` | sim | Unitário normalizado. |
| `freight` | `{provided_value, region, percentage, surcharge, total}` | sim | `total = provided_value + surcharge`. |
| `totals` | `{items_total, tax, freight, invoice_total}` | sim | `Σ item_total = items_total` e `Σ item_tax = tax`. |

```json
{
  "number": 1024, "series": 1, "request_id": "01933b6e-7c1a-7d3e-9f10-2b6a4c8d0e11",
  "issued_at": "2026-11-14T18:00:00.410Z", "taxation_mode": "POR_FORA", "tax_rate": 0.12,
  "recipient": { "person_type": "FISICA", "masked_document": "***.456.789-**", "tax_regime": null },
  "items": [
    { "code": "SKU-001", "description": "Teclado", "unit_price": 100.00, "quantity": 2, "item_total": 200.00, "item_tax": 24.00 },
    { "code": "SKU-002", "description": "Monitor", "unit_price": 350.00, "quantity": 1, "item_total": 350.00, "item_tax": 42.00 }
  ],
  "freight": { "provided_value": 50.00, "region": "SUDESTE", "percentage": 0.048, "surcharge": 2.40, "total": 52.40 },
  "totals": { "items_total": 550.00, "tax": 66.00, "freight": 52.40, "invoice_total": 668.40 }
}
```

> **Variante `POR_DENTRO`:** para cada item, `item_tax = round(item_total × a / (1 + a), 2, HALF_EVEN)` e o item ganha o campo `net_value = item_total − item_tax`. `invoice_total = items_total + freight`. Com o pedido acima: `24.00 → 21.43`, `42.00 → 37.50`, tributo `58.93`, `invoice_total = 602.40`.

#### `InvoiceIssuedEvent` — envelope Kafka

| Campo | Tipo | Obrigatório | Descrição |
|---|---|---|---|
| `evento_id` | `string` (UUIDv7) | sim | Identifica o registro de outbox e permite desduplicar. |
| `versao` | `integer` | sim | `1`. |
| `ocorrido_em` | `instant` | sim | Momento do commit da emissão. |
| `nota` | `InvoiceEventPayload` | sim | Nota completa no formato do evento. |

Headers Kafka: `traceparent` (contexto W3C gravado no outbox), `solicitacao-id`. Chave: `solicitacao_id`. O evento `SolicitacaoRecebida` tem o mesmo envelope, com `solicitacao_id` no lugar de `nota`.

#### `ProblemDetails` — envelope de erro (RFC 9457, `application/problem+json`)

| Código (`code`) | HTTP | Significado |
|---|---|---|
| `json_malformed` | `400` | Corpo ilegível. |
| `required_field` | `400` | Campo ausente ou nulo. |
| `invalid_value` | `400` | Tipo, formato ou enum inválido; quantidade não inteira ou ≤ 0; valor negativo; documento inválido. |
| `empty_items` | `400` | `items` vazio. |
| `missing_delivery_address` | `400` | Nenhum endereço de entrega, ou mais de um. |
| `idempotency_key_missing` | `400` | Header ausente ou que não é um UUID. |
| `idempotency_key_reused` | `422` | Mesma chave com outro payload. |
| `unsupported_tax_regime` | `422` | PJ com regime `OUTROS`. |
| `item_limit_exceeded` | `422` | Mais de 500 itens. |

```json
{
  "type": "https://api.notafiscal.local/problemas/requisicao-invalida",
  "title": "Requisição inválida",
  "status": 400,
  "detail": "O pedido contém 2 erros.",
  "instance": "/issuance-requests",
  "trace_id": "4bf92f3577b34da6a3ce929d0e0e4736",
  "errors": [
    { "field": "items[1].quantity", "code": "invalid_value", "message": "A quantidade deve ser um inteiro maior que zero." },
    { "field": "recipient.tax_regime", "code": "required_field", "message": "O regime de tributação é obrigatório para pessoa jurídica." }
  ]
}
```

> Quando um pedido tem violações de `400` e de `422` ao mesmo tempo, prevalece o `400` (dado ausente ou malformado), e a resposta lista todas as violações.

#### Mapeamento DTO → domínio

| Origem (`IssueInvoiceRequest`) | Destino (`Order`) |
|---|---|
| `recipient.person_type` / `tax_regime` / `document` | `Recipient(tipo, regime?, Document)` |
| `items[].unit_price` | `OrderItem.valorUnitarioInformado` (original) e `OrderItem.valorUnitario` (normalizado, `Money`) |
| `items[].quantity` | `OrderItem.quantidade: Int` |
| `freight_value` | `Order.valorFrete: Money` |
| `addresses[]` com finalidade de entrega | `Order.enderecoEntrega: Address` (os demais endereços são guardados, mas não entram no cálculo) |
| header `X-Client-Id` | `IssuanceCommand.clienteId` |
| header `Idempotency-Key` | `IssuanceCommand.chave` |

`Fingerprint` é o SHA-256 da forma canônica do `Order`: chaves ordenadas, valores informados com `stripTrailingZeros().toPlainString()` e endereços na ordem recebida. Assim, diferenças de formatação (`50.0` × `50.00`, espaços, ordem dos campos) não mudam o hash, e qualquer diferença de valor muda.

#### Parâmetros fixos (configuração `nota-fiscal.*`)

| Propriedade | Padrão | Descrição |
|---|---|---|
| `tributacao.modo` | `POR_FORA` | `POR_FORA` ou `POR_DENTRO`. Nunca é inferido do payload. |
| `numeracao.serie-padrao` | `1` | Série usada pelo worker e default do `GET` de nota. |
| `consulta.janela-leitura-consistente` | `60s` | Idade máxima do id para permitir fallback na primária. |
| `consulta.retry-after` | `1s` | Valor do `Retry-After`. |
| `consulta.cache-ultimo-numero` | `2s` | TTL do último número emitido. |
| `outbox.intervalo` / `outbox.lote` | `50ms` / `500` | Polling do publicador, por tópico. |
| `outbox.espera-confirmacao` | `10s` | Espera máxima pelas confirmações do Kafka de um lote. Os eventos não confirmados nesse prazo continuam pendentes. |
| `ingresso.capacidade` | `1000` | Capacidade da fila em memória de gravação por instância da API. Cheia, a requisição recebe `503` na hora (`fila_cheia`). Na carga de 10k, `scripts/carga.sh` usa `10000` (a maior rajada do PRD), por `NOTA_FISCAL_INGRESSO_CAPACIDADE`. |
| `ingresso.espera-maxima` | `8s` | Prazo máximo de uma requisição na fila antes do `503` (`espera_excedida`); o item é cancelado antes de qualquer escritor assumi-lo, e nada é persistido. Na carga de 10k, `scripts/carga.sh` usa `50s`, abaixo do timeout padrão de 60 s do Gatling. Também limita a espera da parada graciosa dos escritores (ADR-007). |
| `ingresso.lote-maximo` | `100` | Máximo de solicitações gravadas por comando. Sob carga o lote enche sozinho (`drainTo`); em carga baixa tem um item e não acrescenta latência. |
| `ingresso.escritores` | `2` | Threads de plataforma que gravam os lotes; cada uma usa uma conexão de escrita por vez, em vez de uma por requisição. |
| `outbox.retencao` | `24h` | Retenção dos registros publicados. |
| `processamento.tentativas` / `backoff` | `5` / `200ms × 2` (máx. `5s`) | Retry do worker antes de `FALHA`. |
| `consumidores.<nome>.latencia` | `50ms–300ms` | Latência simulada (uniforme). |
| `consumidores.<nome>.taxa-falha` | `0.0` | Probabilidade de falha simulada (usada nos testes). |
| `consumidores.<nome>.tentativas` / `max-em-voo` | `4` / `200` | Retry antes da DLQ e limite de notificações simultâneas. |

As tabelas de alíquota e de frete são dados imutáveis no `domain` (`TaxRateTable` e `FreightTable`), com as faixas do PRD expressas como limites inclusivos, e não como `if`s espalhados.

#### Esquema PostgreSQL (Flyway, schema `notafiscal`)

```sql
CREATE TABLE solicitacao (
  id                 uuid PRIMARY KEY,                 -- UUIDv7
  cliente_id         varchar(64)  NOT NULL,
  idempotency_key    uuid         NOT NULL,
  impressao_digital  char(64)     NOT NULL,            -- SHA-256 hex
  pedido             jsonb        NOT NULL,
  status             varchar(12)  NOT NULL CHECK (status IN ('RECEBIDA','PROCESSANDO','EMITIDA','FALHA')),
  motivo_falha       varchar(200),
  nota_serie         int,
  nota_numero        int,
  recebida_em        timestamptz  NOT NULL,
  atualizada_em      timestamptz  NOT NULL,
  emitida_em         timestamptz,
  CONSTRAINT uk_solicitacao_chave UNIQUE (cliente_id, idempotency_key)
);

CREATE TABLE contador_numeracao (
  serie          int PRIMARY KEY,
  ultimo_numero  int NOT NULL DEFAULT 0 CHECK (ultimo_numero BETWEEN 0 AND 999999999)
);

CREATE TABLE nota_fiscal (
  serie            int          NOT NULL,
  numero           int          NOT NULL CHECK (numero BETWEEN 1 AND 999999999),
  solicitacao_id   uuid         NOT NULL UNIQUE REFERENCES solicitacao(id),
  cliente_id       varchar(64)  NOT NULL,
  modo_tributacao  varchar(10)  NOT NULL,
  aliquota         numeric(6,4) NOT NULL,
  total_itens      numeric(15,2) NOT NULL,
  tributo          numeric(15,2) NOT NULL,
  frete            numeric(15,2) NOT NULL,
  total_nota       numeric(15,2) NOT NULL,
  emitida_em       timestamptz  NOT NULL,
  destinatario_tipo_pessoa varchar(8)  NOT NULL,
  destinatario_documento   varchar(20) NOT NULL,      -- já mascarado
  destinatario_regime      varchar(20),
  frete_informado  numeric(15,2) NOT NULL,
  frete_regiao     varchar(12)   NOT NULL,
  frete_percentual numeric(6,4)  NOT NULL,
  PRIMARY KEY (serie, numero)
);

CREATE TABLE nota_fiscal_item (
  serie int NOT NULL, numero int NOT NULL, sequencia int NOT NULL,
  codigo varchar(60) NOT NULL, valor_unitario numeric(15,2) NOT NULL, quantidade int NOT NULL,
  total_item numeric(15,2) NOT NULL, tributo_item numeric(15,2) NOT NULL,
  descricao text NOT NULL, valor_liquido numeric(15,2),
  PRIMARY KEY (serie, numero, sequencia),
  FOREIGN KEY (serie, numero) REFERENCES nota_fiscal(serie, numero)
);

CREATE TABLE outbox (
  id            bigserial PRIMARY KEY,
  evento_id     uuid        NOT NULL UNIQUE,
  topico        varchar(120) NOT NULL,
  chave         varchar(64)  NOT NULL,
  payload       jsonb        NOT NULL,
  traceparent   varchar(64),
  criado_em     timestamptz  NOT NULL,
  publicado_em  timestamptz
);
CREATE INDEX ix_outbox_pendente ON outbox (id) WHERE publicado_em IS NULL;
CREATE INDEX ix_outbox_pendente_topico ON outbox (topico, id) WHERE publicado_em IS NULL;  -- V3: ciclo por tópico

CREATE TABLE notificacao_recebida (            -- registro dos consumidores simulados
  consumidor   varchar(20) NOT NULL,
  evento_id    uuid        NOT NULL,
  serie int NOT NULL, numero int NOT NULL,
  recebida_em  timestamptz NOT NULL,
  entregas     int NOT NULL DEFAULT 1,
  PRIMARY KEY (consumidor, evento_id)
);
```

> A nota é lida das colunas de `nota_fiscal` e das linhas de `nota_fiscal_item`, ordenadas por `sequencia`, e convertida uma única vez para o domínio. A migração `V4` substituiu o antigo documento `conteudo` (jsonb), que duplicava os itens e exigia desserializar o JSON a cada `GET`, preenchendo as novas colunas a partir dele. As alíquotas lidas de `numeric(6,4)` perdem os zeros de escala (`0.1200 → 0.12`), de modo que o `GET` e o evento continuam com os mesmos valores. `descricao` é `text` porque o limite de 120 caracteres não é validado na entrada.

**Mapeamento JPA**

- O Flyway continua dono do schema; o Hibernate só valida o mapeamento (`ddl-auto=validate`).
- Entidades sem associações bidirecionais nem carregamento preguiçoso entre agregados: `nota_fiscal_item` é gravado explicitamente pelo seu repositório, e as chaves estrangeiras continuam no banco. Assim não há N+1 nem proxies cruzando a fronteira do gateway.
- Ids atribuídos pela aplicação (UUIDv7 da solicitação e chaves compostas de nota, item e notificação) implementam `Persistable` com `isNew`, para que o `save()` use `persist` em vez de `merge`, que faria um `SELECT` antes de cada `INSERT`.
- `outbox.id` usa `@SequenceGenerator` sobre `outbox_id_seq`, com `allocationSize` igual ao `INCREMENT BY` da sequência (migração `V2`). `IDENTITY` desligaria o *batch* de inserts do Hibernate.
- Colunas `jsonb` (`pedido`, `payload`) mapeadas como `String` com `@JdbcTypeCode(SqlTypes.JSON)`, com a serialização no `JsonMapper` da aplicação. O `pedido` é gravado e lido por `StoredOrder` (`repository/entity`), com campos não anuláveis e chaves próprias em português (`destinatario`, `itens`, `valor_frete`, `enderecos`), independentes do contrato HTTP: renomear campos da API não invalida solicitações já gravadas, e o `IssueInvoiceRequest` fica restrito à camada HTTP. A nota não tem coluna `jsonb`: seus campos são colunas próprias (`@Embedded` para destinatário e frete).
- Leituras na réplica usam `@Transactional(readOnly = true)` no gateway de leitura: o `LazyConnectionDataSourceProxy` direciona a conexão ao *read-only data source*. `findConsistent` e o último número emitido usam transação de escrita (primária).

**Operações transacionais do adaptador**

- `registerOnce`: o id do evento, o payload `SolicitacaoRecebida`, o JSON do pedido e o `traceparent` são preparados **antes** de qualquer fila ou conexão, na thread da requisição. O gateway entrega a inserção à `IssuanceRequestWriteQueue`, que a enfileira (ou lança `503` se estiver cheia ou encerrando) e bloqueia a thread virtual da requisição até o resultado, por no máximo `ingresso.espera-maxima`. Os escritores tiram da fila o primeiro item e os demais disponíveis (`drainTo`, até `ingresso.lote-maximo`) e chamam `PostgresBatchPersister.persist` (`@Transactional`), que executa **um único comando por lote**: `IssuanceRequestRepository.insertBatch` é uma CTE com `unnest` dos arrays do lote que faz `INSERT INTO solicitacao ... ON CONFLICT (cliente_id, idempotency_key) DO NOTHING RETURNING id`, seguido de `INSERT INTO outbox ... SELECT ... JOIN nova RETURNING evento_id`. Um item é `Created` quando seu `evento_id` volta no `RETURNING`; caso contrário o gateway busca a existente com `findByClienteIdAndChave` e devolve `Existing`. Duas inserções da mesma chave no mesmo lote resultam em uma criada e uma existente. Cada item tem uma decisão atômica: o escritor o **assume** antes de gravar, ou a requisição o **cancela** ao estourar o prazo, e só um dos dois vence. Um item cancelado é descartado sem gravar, e um item já assumido é aguardado até o commit, sem inserção fantasma. Se o lote falha por `DataIntegrityViolationException` (por exemplo, um caractere nulo no JSON), os itens são gravados individualmente para isolar o defeituoso; qualquer outra falha (banco indisponível) falha todos os itens do lote com a mesma exceção. Na parada, a fila deixa de aceitar (`encerrando`) e os escritores esvaziam o que já foi enfileirado. O `outbox.id` usa o `DEFAULT` da coluna (`nextval` de `outbox_id_seq`); os valores obtidos pelo comando nativo não colidem com os blocos que o otimizador do Hibernate aloca para as entidades, porque cada bloco contém um único valor da progressão da sequência, o que o próprio Hibernate obteve. O TI-24 verifica essa convivência.
- `startProcessing`: `UPDATE` condicional (`@Modifying` em JPQL: `status = PROCESSANDO WHERE id = :id AND status IN (RECEBIDA, PROCESSANDO)`), seguido de `findById` quando uma linha for alterada. Devolve `null` se a solicitação já estiver `EMITIDA` ou em `FALHA`, o que torna a reentrega inofensiva.
- `issue`: `findById` com `@Lock(PESSIMISTIC_WRITE)` na solicitação (`SELECT ... FOR UPDATE`). Se já estiver `EMITIDA`, devolve a nota existente. Senão, executa `NumberingCounterRepository.reserve` (consulta nativa `UPDATE contador_numeracao SET ultimo_numero = ultimo_numero + 1 WHERE serie = :serie AND ultimo_numero < 999999999 RETURNING ultimo_numero`), salva a nota, os itens (*batch* do Hibernate) e o outbox `NotaEmitida`, atualiza a entidade da solicitação para `EMITIDA` (dirty checking no commit) e faz o commit. O cálculo é feito **antes** da transação, e o `UPDATE` do contador é o último bloqueio adquirido, para encurtar o tempo de lock. Um rollback desfaz o incremento, por isso não há lacunas.
- Publicador: `OutboxRepository` busca os pendentes ordenados por `id`, com `Limit`/`Pageable` do tamanho do lote, `@Lock(PESSIMISTIC_WRITE)` e a dica `jakarta.persistence.lock.timeout = -2`, que o Hibernate traduz para `FOR UPDATE SKIP LOCKED` no PostgreSQL (se o SQL gerado não confirmar o `SKIP LOCKED`, usar consulta nativa equivalente). Há um ciclo por tópico (`topico = :topico`, índice parcial `(topico, id) WHERE publicado_em IS NULL`), para que os eventos `NotaEmitida` não esperem atrás do backlog de `SolicitacaoRecebida`. O lote é enviado ao Kafka (`acks=all`, `enable.idempotence=true`) **sem bloquear por registro**: todos os `send` são disparados, e o publicador aguarda os futures até `outbox.espera-confirmacao`. Os confirmados são marcados com um único `@Modifying` (`UPDATE outbox SET publicado_em = :agora WHERE id = ANY(:ids)`) e a transação faz commit. Os não confirmados continuam pendentes e voltam no próximo ciclo (*at-least-once*, duplicados com o mesmo `evento_id`); um registro recusado não bloqueia os demais do lote.
- Notificações dos consumidores: `ReceivedNotificationRepository.register` (`@Modifying` nativo com `ON CONFLICT (consumidor, evento_id) DO UPDATE SET entregas = entregas + 1`).
- Limpeza do outbox: `@Modifying` em JPQL removendo os publicados antes da retenção.

### Endpoints da API

Headers comuns: `X-Client-Id` (injetado pelo gateway; obrigatório), `traceparent` (opcional). Todas as respostas de erro usam `application/problem+json`.

#### Visão geral

| Método | Rota | Descrição |
|---|---|---|
| `POST` | `/issuance-requests` | Solicita a emissão de uma nota (assíncrona). |
| `GET` | `/issuance-requests/{id}` | Consulta o status da solicitação. |
| `GET` | `/invoices/{number}` | Consulta a nota completa. |
| `GET` | `/actuator/health/{liveness,readiness}` | Health checks (fora dos logs). |
| — | `app/src/docs/asciidoc/index.adoc` | Documentação do contrato HTTP gerada com Spring REST Docs a partir dos testes MVC. |

---

#### `POST /issuance-requests`

Valida o pedido, registra a solicitação de forma idempotente e agenda o processamento.

**Headers e corpo**

| Parâmetro | Tipo | Padrão | Regras |
|---|---|---|---|
| `Idempotency-Key` (header) | `uuid` | — | Obrigatório. Escopo: por cliente. Nunca expira. |
| `X-Client-Id` (header) | `string` | — | Obrigatório (gateway). |
| corpo | `IssueInvoiceRequest` | — | Ver Modelos de dados. |

**Respostas**

| Status | Corpo | Quando |
|---|---|---|
| `202` | `IssuanceRequestResponse` (`RECEBIDA`) | Pedido válido, com chave nova ou chave repetida com o mesmo payload (mesmo `id`). Headers `Location: /issuance-requests/{id}` e `Retry-After: 1`. |
| `400` | `ProblemDetails` | JSON malformado, campos ausentes ou inválidos, header ausente. Nada é persistido. |
| `401` | `ProblemDetails` | `X-Client-Id` ausente. |
| `422` | `ProblemDetails` | Regime `OUTROS`, mais de 500 itens, chave reutilizada com outro payload. |
| `503` | `ProblemDetails` | A fila de gravação da instância está cheia, o prazo `ingresso.espera-maxima` foi excedido ou a instância está encerrando (o motivo vai para a tag da métrica, não para o corpo). Header `Retry-After`. Nada é persistido: um item cancelado por prazo nunca é gravado depois. O cliente pode repetir com a mesma `Idempotency-Key`. Sobrecarga operacional, não regra de negócio: um pedido válido aceito continua recebendo `202` (RF3). |

**Exemplo — sucesso**

```http
POST /issuance-requests
Idempotency-Key: 6f1c2b7e-3a4d-4e5f-8a9b-0c1d2e3f4a5b
X-Client-Id: cliente-erp-01
Content-Type: application/json

(corpo do exemplo de IssueInvoiceRequest)
```

```http
HTTP/1.1 202 Accepted
Location: /issuance-requests/01933b6e-7c1a-7d3e-9f10-2b6a4c8d0e11
Retry-After: 1
```

```json
{ "id": "01933b6e-7c1a-7d3e-9f10-2b6a4c8d0e11", "status": "RECEBIDA", "received_at": "2026-11-14T18:00:00.120Z", "updated_at": "2026-11-14T18:00:00.120Z", "links": { "self": "/issuance-requests/01933b6e-7c1a-7d3e-9f10-2b6a4c8d0e11" } }
```

**Exemplo — repetição com o mesmo payload:** `202` idêntico, com o mesmo `id` e o status atual (pode já ser `EMITIDA`).

> A resposta repetida devolve o estado **atual** da solicitação, com o mesmo `id`, `Location` e `Retry-After`. O PRD exige "o mesmo `202` com o mesmo id"; o status no corpo reflete o progresso.

**Exemplo — chave reutilizada com outro payload**

```json
{ "type": "https://api.notafiscal.local/problemas/idempotency-key-reutilizada", "title": "Idempotency-Key já utilizada", "status": 422, "detail": "A chave informada já foi usada com outro pedido. Gere uma nova chave para cada pedido.", "instance": "/issuance-requests", "errors": [ { "field": "Idempotency-Key", "code": "idempotency_key_reused", "message": "A chave já foi usada com outro conteúdo." } ] }
```

**Exemplo — PJ com regime `OUTROS`**

```json
{ "type": "https://api.notafiscal.local/problemas/pedido-nao-emitivel", "title": "Pedido não pode ser emitido", "status": 422, "detail": "O pedido contém 1 impedimento.", "instance": "/issuance-requests", "errors": [ { "field": "recipient.tax_regime", "code": "unsupported_tax_regime", "message": "Não é possível emitir nota para o regime OUTROS." } ] }
```

---

#### `GET /issuance-requests/{id}`

Consulta o status da solicitação. Só lê a réplica, com fallback filtrado para a primária.

| Parâmetro | Tipo | Padrão | Regras |
|---|---|---|---|
| `id` (path) | `uuid` | — | Formato inválido → `404`, sem acessar o banco. |

**Respostas**

| Status | Corpo | Quando |
|---|---|---|
| `200` | `IssuanceRequestResponse` | Encontrada e do próprio cliente. `RECEBIDA`/`PROCESSANDO` incluem `Retry-After`; `EMITIDA` inclui `links.invoice` e o header `Link: </invoices/1024?series=1>; rel="invoice"`. |
| `404` | `ProblemDetails` | Inexistente, de outro cliente, ou não encontrada na réplica com id fora da janela de leitura consistente. |

> **Algoritmo:** (1) busca na réplica; (2) se encontrou e é de outro cliente → `404`, sem fallback; (3) se não encontrou e o instante do UUIDv7 está dentro de `janela-leitura-consistente` → `findConsistent` na primária; (4) caso contrário → `404`, sem acessar a primária. Ids com instante no futuro (forjados) são tratados como fora da janela.

**Exemplos:** ver as variantes de `IssuanceRequestResponse`. O `404` segue o `ProblemDetails`, com `code: nao_encontrado`.

---

#### `GET /invoices/{number}`

Única representação da nota completa.

| Parâmetro | Tipo | Padrão | Regras |
|---|---|---|---|
| `number` (path) | `integer` | — | De 1 a 999.999.999. Fora do intervalo → `404`. |
| `series` (query) | `integer` | `numeracao.serie-padrao` | O link gerado sempre inclui a série. |
| `If-None-Match` (header) | `string` | — | `ETag` igual → `304`. |

**Respostas**

| Status | Corpo | Quando |
|---|---|---|
| `200` | `InvoiceResponse` | Nota encontrada e do próprio cliente. Headers `ETag: "1-1024"` e `Cache-Control: private, max-age=86400, immutable`. |
| `304` | — | `If-None-Match` igual ao `ETag`. |
| `404` | `ProblemDetails` | Inexistente, de outro cliente, ou `number` maior que o último emitido. |

> **Algoritmo:** (1) busca na réplica só por `(serie, numero)`; (2) se encontrou, verifica o dono: outro cliente → `404`, sem fallback; (3) se não encontrou e `numero ≤ ultimoNumeroEmitido(serie)` (cache com releitura *single-flight* quando o número pedido for maior que o valor em cache) → `findConsistent` na primária; (4) caso contrário → `404`.

**Exemplo — sucesso:** ver o exemplo de `InvoiceResponse`.

---

## Pontos de integração

- **API Gateway e Cognito (desenho):** OAuth2 client credentials, JWT com scopes `notas:emitir` (POST) e `notas:consultar` (GETs), e rate limiting por cliente (usage plans). O gateway valida o token e injeta `X-Client-Id` (claim `client_id`), removendo qualquer valor enviado pelo cliente. A aplicação fica numa subnet privada, alcançável só por VPC Link, e confia no header sem validar o JWT.
- **Kafka:** produtor com `acks=all` e idempotência habilitada. O publicador envia o lote de forma assíncrona e aguarda as confirmações uma vez por lote, nunca uma por registro: nos clientes Kafka 4.x o `linger.ms` padrão é 5 ms, e o envio bloqueante por registro limitava a publicação a cerca de 125 eventos/s (ver `evidences/diagnostico-pool.md`). O worker usa `DefaultErrorHandler` com backoff exponencial: falhas transitórias (banco, rede) são repetidas, e `NumberingExhaustedException` e erros de desserialização não são repetidos. Esgotadas as tentativas, o `ProcessingFailureRecoverer` marca `FALHA` e envia a mensagem para `notafiscal.solicitacoes.v1.dlq`. Cada consumidor repete a própria notificação e a encaminha para `<topico>.<consumidor>.dlq` (`ReceiveNotificationWithRetries`), com o mesmo backoff exponencial e `tentativas` por consumidor; a falha de um não afeta os outros grupos. O retry fica no consumidor, e não no `DefaultErrorHandler` do container, porque, com listener assíncrono, o spring-kafka 4.1.2 só reprocessa a última de várias falhas do mesmo lote, e as demais se perdiam sem retry nem DLQ (tarefa 16). O futuro só conclui depois do registro ou da confirmação da DLQ, e a publicação na DLQ é repetida até o Kafka aceitar, então o offset nunca é confirmado antes disso. `IllegalArgumentException` não é repetida. O `DefaultErrorHandler` do container fica como contingência.
- **Consumidores simulados:** o listener devolve um `CompletableFuture`, concluído por um `ScheduledExecutorService` após a latência simulada, com *ack* assíncrono do Spring Kafka. Nenhuma thread fica bloqueada durante a espera, e `max-em-voo` limita as notificações pendentes. Como o *ack* assíncrono só libera o próximo lote quando o atual termina, cada container busca no máximo `max-em-voo / concorrência` registros por `poll` (`max.poll.records`, aplicado por `ContainerCustomizer`, porque a factory não repassa `kafkaConsumerProperties` aos containers); assim o simulador não rejeita notificações por excesso. A permissão do simulador é liberada antes de concluir o `CompletableFuture`. Para desduplicar, cada consumidor grava `notificacao_recebida` com `ON CONFLICT (consumidor, evento_id) DO UPDATE SET entregas = entregas + 1`, o que torna a entrega *at-least-once* observável.
- **PostgreSQL:** HikariCP separado por processo e por função de banco. O orçamento agregado do ambiente de referência é de até 30 conexões de escrita na primária e 35 conexões de leitura (API 25 e worker 10); a leitura passou de 20 para 35 quando a réplica recebeu os mesmos recursos da primária (2 CPUs e 1 GiB) e o pool de leitura da API foi igualado ao de escrita, porque a espera por esse pool foi o gargalo do `GET` no 10k. Os limites por papel são configuráveis e sua soma deve respeitar esse orçamento considerando a quantidade de réplicas de API e workers. Quando não houver réplica PostgreSQL, o pool de leitura continua apontando à primária e também conta no limite agregado do servidor. Com virtual threads, o gargalo é o pool. `spring.threads.virtual.enabled=true`. Como o Tomcat não limita a concorrência com virtual threads, o `POST` passa pela fila limitada de gravação (`IssuanceRequestWriteQueue`): só `ingresso.escritores` conexões de escrita gravam as solicitações, em lote, em vez de uma conexão por requisição, e o `connection-timeout` dos pools é configurável por papel (`APP_DB_WRITE_CONNECTION_TIMEOUT`, padrão `10s`, acima de `ingresso.espera-maxima`; e `APP_DB_READ_CONNECTION_TIMEOUT`, que mantém o padrão de `30s` do Hikari: um timeout curto na leitura converte esperas por CPU em `500` nos `GET`, o que a rodada de 10k mostrou): a sobrecarga aparece como `503` previsível, e não como `500` após 30 s ou fechamento da conexão. O acesso é feito por Spring Data JPA (Hibernate) sobre os mesmos pools; `open-in-view=false` impede que a conexão fique presa durante a renderização da resposta.
- **Tratamento de erros:** exceções de domínio são traduzidas pelos handlers de `entrypoint/handlers`, um por exceção. `IngressCapacityExhaustedException` vira `503` com `Retry-After`, sem mensagem interna e sem log por requisição (contada em `notafiscal.ingresso.rejeitadas`, com a tag `motivo`). Falhas inesperadas viram `500` genérico com `trace_id`, sem mensagem interna. Falhas de integração (Kafka e banco) seguem `logs.md`: `WARN` por tentativa e `ERROR` na falha definitiva, com a mensagem original sanitizada.

## Abordagem de testes

Ferramentas: JUnit Jupiter, `kotlin.test`, MockK (só nas fronteiras), Testcontainers (`postgresql`, `kafka`), Awaitility (polling com timeout, sem `Thread.sleep`), Gatling com DSL Kotlin para carga e Kover para cobertura. Tags: `integration`, `e2e` e `load`, com as tarefas Gradle `test`, `integrationTest`, `e2eTest` e `loadTest`.

**Cobertura:** 100% de linhas e de branches em cada módulo (`app` e `domain`), verificada separadamente com `koverVerify`. No `domain`, a medição usa só a tarefa `test`; no `app`, o Kover agrega `test` e `integrationTest` (decisão aprovada pelo usuário). Só o ponto de entrada (`Application.kt`) fica excluído. O CI bloqueia o merge abaixo disso.

### Testes de unidade

| ID | Nome do caso de teste | Critérios de aceitação | Resultado esperado |
|----|-----------------------|------------------------|--------------------|
| TU-01 | `aplica aliquota PF conforme a faixa nos limites` | CA-16 | 499,99 → 0%; 500,00 e 2.000,00 → 12%; 2.000,01 e 3.500,00 → 15%; 3.500,01 → 17%. |
| TU-02 | `aplica aliquota PJ por regime e faixa nos limites` | CA-16 | 999,99 → 3%; 1.000,00 e 2.000,00 → 7/9/9%; 2.000,01 e 5.000,00 → 13/15/16%; 5.000,01 → 19/20/20%. |
| TU-03 | `calcula acrescimo de frete por regiao com HALF_EVEN` | CA-17 | Cinco regiões; casos de meio centavo arredondam para o par (ex.: SUL 6% de 0,75 = 0,045 → 0,04; SUL 6% de 0,25 = 0,015 → 0,02; NORDESTE 8,5% de 1,00 = 0,085 → 0,08). |
| TU-04 | `arredonda tributo por item e soma das linhas igual ao total` | CA-18 | `Σ tributo_item = tributo` e `Σ total_item = total_itens` para massas com centavos ímpares. |
| TU-05 | `normaliza valor unitario com mais de duas casas` | CA-19 | `100.005 → 100.00`, `100.015 → 100.02`, `0.125 → 0.12`. |
| TU-06 | `calcula tributacao por dentro por item` | CA-20 | Exemplo do modelo: tributo 58,93, total 602,40; `liquido + tributo = bruto` por item. |
| TU-07 | `calcula total da nota por fora` | CA-20 | Exemplo do modelo: 668,40. |
| TU-08 | `classifica dados ausentes e malformados como violacoes de entrada` | CA-02, CA-04 | Sem itens, sem entrega, mais de uma entrega, PJ sem regime, quantidade ≤ 0, valor negativo, documento inválido → `AUSENTE`/`MALFORMADO`, todas listadas. |
| TU-09 | `classifica regime OUTROS e mais de 500 itens como impeditivos` | CA-03 | 500 itens → válido; 501 → `IMPEDITIVO`; `OUTROS` → `IMPEDITIVO`. |
| TU-10 | `registra solicitacao nova e reaproveita existente pela impressao digital` | CA-05, CA-06 | `Created` → aceita; `Existing` com a mesma impressão → mesmo id; com outra → `IdempotencyKeyReusedException`. Pedido inválido não chama o gateway. |
| TU-11 | `impressao digital ignora formatacao e detecta mudanca de valor` | CA-05, CA-06 | `50.0` × `50.00` e ordem de campos → mesmo hash; `quantidade 2 → 3` → hash diferente. |
| TU-12 | `processa solicitacao e ignora reentrega ja emitida` | CA-09, CA-10, CA-22 | `startProcessing` → cálculo → `issue`; `startProcessing` nulo → nada é emitido. |
| TU-13 | `registra falha com motivo legivel` | CA-11 | `NumberingExhaustedException` → `recordFailure` com motivo sem detalhes internos. |
| TU-14 | `aplica politica de leitura consistente pela idade do id` | CA-12, CA-15 | Id recente ausente → `findConsistent`; id antigo ou com instante futuro → `404`, sem chamar a primária (MockK `verify(exactly = 0)`). |
| TU-15 | `consulta nota com fallback ate o ultimo numero e isolamento por cliente` | CA-10, CA-13, CA-14 | `numero ≤ ultimo` → primária; `>` → `404`; outro cliente encontrado na réplica → `404`, sem fallback. |
| TU-16 | `calculos de pedidos distintos sao independentes` | CA-21 | Calcular A, B e A de novo produz notas idênticas às calculadas isoladamente (sem estado). |
| TU-17 | `mascara CPF e CNPJ` | CA-28 | `12345678909 → ***.456.789-**`; `12345678000195 → **.345.678/0001-**`; `toString` do `Document` nunca expõe o valor. |
| TU-18 | `converte request para dominio preservando BigDecimal` | CA-02, CA-19 | `toDomain` mantém o valor informado e o normalizado; campos nulos geram violações com o caminho do campo. |
| TU-19 | `traduz excecoes para problem details` | CA-02, CA-03, CA-06, CA-08 | `AUSENTE`/`MALFORMADO` → 400; só `IMPEDITIVO` → 422; mistura → 400; exceção inesperada → 500 genérico sem mensagem interna. |
| TU-20 | `monta Location Retry-After e links por status` | CA-01, CA-09, CA-10 | `202`/`RECEBIDA` com `Retry-After`; `EMITIDA` com `links.invoice` e `Link`; `FALHA` sem `Retry-After`. |
| TU-21 | `simulador de consumidor conclui apos latencia sem bloquear` | CA-24 | Com um scheduler controlado, o future só é concluído após a latência; a chamada retorna antes de o future terminar. |
| TU-22 | `gera UUIDv7 ordenavel e extrai o instante` | CA-12 | Ids monotônicos no mesmo milissegundo; o instante extraído é igual ao do `Clock` fixo. |
| TU-23 | `rejeita sem esperar quando a fila de gravacao esta cheia` | CA-26 | Com a capacidade ocupada, a chamada seguinte lança `IngressCapacityExhaustedException` com o motivo `fila_cheia` sem esperar; ao liberar o escritor, os itens enfileirados são gravados. |
| TU-24 | `traduz capacidade esgotada para 503 com Retry-After e conta o motivo` | CA-26 | `503` `application/problem+json` com `Retry-After`, sem mensagem interna e com `notafiscal.ingresso.rejeitadas` incrementado só na tag do motivo. |
| TU-25 | `cancela o item e rejeita quando a espera maxima excede antes de um escritor assumi-lo` | CA-26 | O prazo vencido cancela o item (`espera_excedida`), e um escritor que o recebe depois o descarta sem gravar. |
| TU-26 | `aguarda o resultado quando o escritor assumiu o item antes do prazo` | CA-26 | Com o item já assumido, a requisição aguarda a conclusão em vez de cancelar, e devolve o resultado do commit. |
| TU-27 | `grava o lote em um comando e informa criada ou existente por item` | CA-11 | Um lote de três itens chama o gravador uma vez, e cada requisição recebe criada ou existente conforme a posição. |
| TU-28 | `descarta itens cancelados e nao chama o banco quando o lote fica vazio` | CA-26 | Itens cancelados saem do lote; um lote só com cancelados não chama o gravador. |
| TU-29 | `grava individualmente quando o lote falha por dado invalido e isola o item defeituoso` | CA-26 | `DataIntegrityViolationException` no lote repete cada item sozinho; os válidos concluem e só o defeituoso falha. |
| TU-30 | `falha todos os itens do lote quando o banco esta indisponivel` | CA-26 | Outras falhas não repetem a gravação e propagam a mesma exceção a todos os itens. |
| TU-31 | `descarrega a fila no encerramento e rejeita novas requisicoes` | CA-26 | Após a parada, `persist` lança `encerrando`, e os itens já enfileirados são gravados antes de o escritor terminar. |
| TU-32 | `mantem o hexadecimal das impressoes ja gravadas` | CA-05, CA-06 | A impressão digital de um pedido fixo continua igual ao valor calculado antes da troca de `String.format` por `HexFormat`, para que as chaves de idempotência já gravadas sigam reconhecidas. |
| TU-33 | `executa o registro do outbox dentro de uma observacao` | CA-28 | `OutboxTracing.register` executa a ação; o evento `SolicitacaoRecebida` passa a usar o `traceparent` do span HTTP atual, sem observação própria por requisição. |

Estratégia: o `domain` é testado só com fakes e MockK nas portas, sem Spring. O `app` é testado com unidades isoladas (DTOs, handler, simulador e políticas de cache *single-flight*) e com MockK nas fronteiras. O `Clock` é fixo em todos os testes.

### Testes de integração

Executados com `@Tag("integration")` e Testcontainers (PostgreSQL 17 primária, um PostgreSQL "réplica" vazio que simula lag e Kafka KRaft), com o contexto Spring real.

| ID | Nome do caso de teste | Critérios de aceitação | Resultado esperado |
|----|-----------------------|------------------------|--------------------|
| TI-01 | `aceita pedido e grava solicitacao e outbox na mesma transacao` | CA-01 | `202` com `Location` e `Retry-After`; uma linha em `solicitacao` (`RECEBIDA`) e uma em `outbox`. Uma falha simulada no insert do outbox não deixa a solicitação gravada. |
| TI-02 | `rejeita pedido invalido sem persistir` | CA-02, CA-03, CA-04 | Para cada cenário de `400`/`422`, as tabelas continuam vazias. |
| TI-03 | `cria uma unica solicitacao para chaves simultaneas` | CA-07 | 50 POSTs paralelos com a mesma chave → 1 linha, todos com o mesmo `id`. |
| TI-04 | `rejeita chave reutilizada com outro payload` | CA-06 | `422 idempotency_key_reutilizada`; a solicitação original não muda. |
| TI-05 | `numera sem duplicidade nem lacunas com varias instancias` | CA-22 | Dois contextos de aplicação e 20 workers emitindo 2.000 notas: `count(distinct numero) = count(*)` e `max − min + 1 = count(*)`. Rollbacks forçados não deixam lacunas. |
| TI-06 | `publica outbox ao menos uma vez com duas instancias` | CA-23 | Dois publicadores concorrentes: todo evento chega ao tópico, sem perda; os duplicados têm o mesmo `evento_id`. |
| TI-07 | `evento NotaEmitida e identico a nota persistida` | CA-18 | O `nota` do evento é igual ao `GET /invoices`; as somas das linhas conferem com `nota_fiscal_item`. |
| TI-08 | `pedidos sequenciais e paralelos nao acumulam itens` | CA-21 | 200 pedidos com quantidades de itens diferentes → cada nota contém exatamente os itens do próprio pedido. |
| TI-09 | `roteia leitura para replica e aplica fallback filtrado` | CA-12, CA-15 | Id recente fora da réplica → `200` pela primária; id antigo ou inexistente → `404`, com zero consultas na primária (contador de statements do datasource). |
| TI-10 | `falha persistente em um consumidor vai para sua DLQ sem afetar os outros` | CA-24, CA-25 | `financeiro` com `taxa-falha=1.0` e `entrega` com latência de 2 s: estoque e registro recebem todas as notas dentro do prazo; a DLQ do financeiro contém todas as notas. |
| TI-11 | `cada consumidor recebe uma notificacao por nota com todos os itens` | CA-23 | Quatro linhas em `notificacao_recebida` por nota; reentregas incrementam `entregas` sem criar linhas. |
| TI-12 | `isola solicitacoes e notas por cliente` | CA-13 | Cliente B consultando recursos do cliente A → `404` nas duas rotas. |
| TI-13 | `responde problem details para entradas malformadas` | CA-02, CA-04, CA-08 | JSON truncado, `quantity: 1.5`, `region: "LESTE"`, header ausente → `400` com `errors[].field`; nenhum `500`. |
| TI-14 | `propaga trace da requisicao ate os quatro consumidores` | CA-28 | O exportador em memória mostra o mesmo `trace_id` no POST, no worker e nos quatro listeners. |
| TI-15 | `logs nao expoem documento sem mascara` | CA-28 | Com `OutputCaptureExtension` durante uma jornada completa, nenhuma ocorrência do CPF ou CNPJ cru; `/actuator/health` não gera log. |
| TI-16 | `marca FALHA apos esgotar tentativas do worker` | CA-11 | Contador esgotado (`ultimo_numero = 999999999`) → `FALHA` com motivo legível e mensagem na DLQ de solicitações. |
| TI-17 | `GET da nota responde ETag e 304` | CA-10 | `200` com `ETag` e `Cache-Control`; com `If-None-Match` → `304`. |
| TI-18 | `ativa componentes conforme o papel do processo` | CA-30 | Papel `api` inicia controllers HTTP sem listeners Kafka ou jobs de worker; papel `worker` inicia listeners e jobs sem servidor HTTP. O modo padrão de testes mantém o contexto combinado já usado pelas suítes existentes. |
| TI-19 | `limita pools por papel dentro do orçamento agregado` | CA-30 | Os limites configurados de escrita e leitura são próprios de cada papel, positivos e limitados; a soma por tipo de conexão não excede 30 para escrita e 35 para leitura no ambiente de referência. |
| TI-20 | `persiste entidades com id atribuido sem SELECT previo` | CA-01, CA-22 | Com as estatísticas do Hibernate (ou o contador de statements do datasource) ativadas, o POST e a emissão executam apenas os `INSERT`/`UPDATE` esperados: nenhum `SELECT` gerado por `merge` antes da gravação de solicitação, nota, itens, outbox ou notificação. |
| TI-21 | `insere itens da nota em lote` | CA-03, CA-21 | Uma nota com 500 itens grava os itens em *batch* JDBC (estatísticas do Hibernate com `batch_size` aplicado; número de execuções de lote menor que o de itens), e o outbox usa `SEQUENCE` sem desligar o lote. |
| TI-22 | `publica lote do outbox com uma confirmacao por lote` | CA-23 | Um lote de 200 eventos chega ao tópico e é marcado com um único `UPDATE` (contador de statements). Um registro recusado pelo produtor (por exemplo, acima de `max.request.size`) fica pendente sozinho, e os demais do lote são publicados e marcados. |
| TI-23 | `publica notas emitidas sem esperar o backlog de solicitacoes` | CA-23, CA-26 | Com milhares de `SolicitacaoRecebida` pendentes, um `NotaEmitida` gravado depois é publicado no ciclo seguinte do seu tópico, sem esperar o escoamento do outro. |
| TI-24 | `grava solicitacao e outbox em um unico comando` | CA-01, CA-05, CA-07 | O `POST` com chave nova executa um único comando de escrita (`INSERT INTO solicitacao` com `INSERT INTO outbox`) antes do commit; com chave repetida, o comando não grava outbox e segue o `SELECT` da existente. Os ids do outbox gravados pelo comando nativo e pelas entidades (`NotaEmitida`) não colidem. |
| TI-25 | `expoe actuator so na porta de gerenciamento` | CA-26, CA-30 | Com `management.server.port` definido, `/actuator/prometheus` e `/actuator/health/readiness` respondem na porta de gerenciamento e `404` na porta da API; o health check continua sem gerar log. |
| TI-26 | `documenta 503 de capacidade esgotada no POST` (REST Docs) | CA-26 | `IssuanceRequestErrorDocumentationTest` gera o snippet `solicitacoes/criar-capacidade-esgotada` com o `ProblemDetails`, o header `Retry-After` e o status `503`. |
| TI-27 | `agrupa gravacoes concorrentes em poucos comandos` | CA-26 | 200 gravações simultâneas criam 200 solicitações e 200 eventos com menos comandos que requisições. |
| TI-28 | `resolve a mesma chave de idempotencia dentro do mesmo lote` | CA-11 | Duas inserções da mesma chave em um lote resultam em uma criada e uma existente, com uma solicitação e um evento. |
| TI-29 | `item invalido nao falha os demais itens do lote` | CA-26 | Um pedido com `\u0000` falha com `DataIntegrityViolationException`, e os demais itens do lote são gravados. |
| TI-30 | `falhas de varias notas no mesmo lote vao todas para a DLQ do consumidor` | CA-25 | `financeiro` com `taxa-falha=1.0` e 40 notas: todas chegam à DLQ do financeiro, e estoque, registro e entrega recebem as 40. |

### Testes E2E

Executados com `@Tag("e2e")`: a aplicação sobe em `RANDOM_PORT` contra Testcontainers, e o teste usa um cliente HTTP real. Cada teste é uma jornada (Given/When/Then), com Awaitility para aguardar os estados.

| ID | Nome do caso de teste | Critérios de aceitação | Resultado esperado |
|----|-----------------------|------------------------|--------------------|
| E2E-01 | `emite nota PF do pedido ate a consulta da nota` | CA-01, CA-09, CA-10, CA-16, CA-17, CA-19 | `202` → `RECEBIDA`/`PROCESSANDO` → `EMITIDA` → link → nota com os valores exatos do exemplo (668,40). |
| E2E-02 | `emite nota PJ para cada regime suportado` | CA-16, CA-18 | Simples, Real e Presumido na faixa de 2.000,01 a 5.000,00 → 13%, 15% e 16%, com as somas conferidas. |
| E2E-03 | `reenvio com a mesma chave devolve a mesma solicitacao` | CA-05 | Dois POSTs → mesmo `id`; uma única nota. |
| E2E-04 | `consulta imediata apos o aceite nunca retorna 404` | CA-12 | 300 POSTs seguidos de GET imediato, com a réplica ativa → zero `404`. |
| E2E-05 | `quatro sistemas sao notificados da nota emitida` | CA-23 | `notificacao_recebida` com os quatro consumidores para a nota. |
| E2E-06 | `emite nota no modo por dentro configurado` | CA-20 | Contexto com `tributacao.modo=POR_DENTRO` → total 602,40 para o mesmo pedido do E2E-01. |
| E2E-07 | `aceita 500 itens e rejeita 501` | CA-03 | 500 itens → `EMITIDA`; 501 → `422`. |
| E2E-08 | `carga combinada em 1k` (`@Tag("load")`, Gatling) | CA-26 | Só 1.000 solicitações, modelo aberto a 1.000 req/s, API e worker em instâncias isoladas de 4 CPUs (limitadas por `docker info`) e réplica ativa. Aquecimento fora da medição com 3.000 solicitações do próprio cenário (`LOAD_COMBINADO_AQUECIMENTO`), aguardando o outbox e o lag do Kafka zerarem antes de medir. Registra POST/GET, erros, emissão, escoamento, recursos, lag de réplica, fallbacks por motivo, `404` falsos e integridade da numeração. |
| E2E-09 | `latencia dos GETs sob carga combinada em 1k` (`@Tag("load")`) | CA-12, CA-27 | Cenário combinado do E2E-08 com réplica ativa; percentis por endpoint, lag e fallback por motivo; zero `404` falsos. |
| E2E-10 | `ambiente local sobe com um comando` (smoke script no CI) | CA-29 | `docker compose up -d --wait` → `readiness` `UP` e um POST de exemplo chega a `EMITIDA`. |
| E2E-11 | `emite nota com API e worker em processos separados` (smoke Compose) | CA-30 | O Compose inicia os dois papéis; a API aceita o POST, o worker processa a solicitação e a consulta HTTP chega a `EMITIDA`, com notificação concluída e zero duplicidades ou lacunas na numeração. |
| E2E-12 | `post isolado em 1k 5k e 10k` (`./gradlew :app:loadTest -PloadScenario=post`, Gatling) | CA-26 | Só o POST recebe tráfego, worker parado, a 1.000 req/s, após 3.000 POST de aquecimento. A instância medida usa 4 CPUs (limitadas por `docker info`). Duas rodadas oficiais; Gatling exige 100% aceitos e máximo de 3 s. SQL confirma um evento de outbox por solicitação sem repetição. Registra fila, lotes, pool, recursos, carga do host, GC e safepoints. |
| E2E-13 | `consumo isolado em 1k 5k e 10k` (`-PloadScenario=consumo`) | CA-23, CA-26 | Worker aquecido com backlog separado de 3.000; backlogs medidos são criados com o worker pausado e conferidos no banco antes do unpause. Mede vazão, tempo de emissão, quatro notificações por nota, lag, métricas de emissão/lock, recursos, pools, numeração e DLQs. |
| E2E-14 | `consulta isolada em 1k 5k e 10k` (`-PloadScenario=consulta`) | CA-12, CA-27 | Base emitida e replicada antes da medição; worker parado. Antes do primeiro volume, os GETs de uma base separada de 3.000 notas aquecem o caminho de leitura, fora da medição (`LOAD_CONSULTA_AQUECIMENTO`). A 1.000 usuários/s, cada usuário consulta status e nota com o cliente dono. Registra percentis e erros por endpoint, pools de leitura, lag da réplica e fallback por motivo. Exige zero `5xx` e `404`. |

E2E-12, E2E-13 e E2E-14 são cargas isoladas por serviço executadas por `:app:loadTest` com `-PloadScenario=post|consumo|consulta`; o perfil dedicado aplica até 4 CPUs ao serviço medido por override temporário, limitado às CPUs que `docker info` reporta. No consumo, a API também recebe o perfil, porque monta os backlogs a 1.000 req/s antes da medição e fica sem tráfego durante ela (rodada de 17/11/2026); `instancia.txt` registra os serviços com o perfil em `servicos_dedicados`. Isso não altera os 2 CPUs/2 GiB por processo no Compose de referência, nem marca por si só a execução como experimento (`LOAD_API_CPUS` continua sendo a chave explícita de experimento). E2E-08/09 rodam combinados somente em 1k, com API e worker em 4 CPUs e a réplica ativa. O CI mantém `./gradlew :app:loadTest -PloadRequests=1000`. O relatório registra CPU efetiva, carga média do host durante a rodada e alerta se a carga já excede os núcleos no início; GC e safepoints da instância medida são gravados junto das evidências. O host compartilhado (gerador, Docker Desktop e outros processos) pode conter as medições, portanto resultados que começam com carga acima dos núcleos são identificados. CA-30 continua exigindo pools próprios e limitados por processo, orçamento agregado de 30 conexões de escrita/35 de leitura e registro de recursos, pools, sessões `idle in transaction`, retenção de conexões, backlog, lag e integridade; a capacidade de CPU de 4 CPUs é somente do perfil de carga. A réplica é ativada para consulta e combinado e os fallbacks são registrados por motivo. Respostas `503` ficam separadas das falhas de transporte. A UI não se aplica.

## Sequenciamento do desenvolvimento

### Ordem de construção

1. **Build e estrutura:** dividir em `app`/`domain`, criar o catálogo de versões e o plugin de convenção (`build-logic`), adicionar Kover, detekt, ktlint e as tarefas `integrationTest`/`e2eTest`/`loadTest`, e renomear para `Application.kt`. Vem primeiro porque todas as tarefas dependem dessa base.
2. **Domínio de cálculo:** `Money`, tabelas, estratégias `POR_FORA`/`POR_DENTRO` e `CalculateInvoice` (TU-01 a TU-07, TU-16). Não depende de infraestrutura e é o núcleo do PRD.
3. **Validação e modelo de solicitação:** `ValidadorPedido`, `Fingerprint` e os casos de uso de emissão e consulta, com fakes (TU-08 a TU-15, TU-17).
4. **Infraestrutura local:** `docker-compose.yml` (Postgres, réplica opcional, Kafka KRaft, app, observabilidade opcional), Flyway e `DataSourceConfig` com *read-only data source*.
5. **POST e GETs:** controllers, DTOs, handlers de exceção e o gateway de solicitação com outbox (TU-18 a TU-20, TU-22, TI-01 a TI-04, TI-09, TI-12, TI-13, TI-17).
6. **Publicador do outbox e worker:** job `SKIP LOCKED`, listener, contador de numeração, emissão e recoverer (TI-05 a TI-08, TI-16).
7. **Consumidores simulados:** quatro grupos, simulador assíncrono e DLQs (TU-21, TI-10, TI-11, TI-30).
8. **Observabilidade:** OpenTelemetry, métricas e logs estruturados com máscara (TI-14, TI-15).
9. **E2E e carga:** E2E-01 a E2E-10, Gatling, scripts e relatório.
10. **Documentação e CI:** Spring REST Docs, README, ADRs, desenho AWS, plano de deploy, GitHub Actions e a atualização do `AGENTS.md` e da `e2e-tests.md` (desvios registrados abaixo).
11. **Experimento de isolamento API/worker:** selecionar papéis independentes no mesmo artefato, limitar os pools por papel sem ultrapassar o orçamento agregado, atribuir 2 CPUs e 2 GiB a cada processo, validar o smoke separado e registrar a rodada 10k e suas limitações (TI-18, TI-19, E2E-11 e CA-30).
12. **Persistência com Spring Data JPA:** substituir os adaptadores `JdbcClient` por entidades e repositórios Spring Data, com consultas nativas pontuais, migração `V2` da sequência do outbox e ADR-006. Sem mudança no contrato HTTP, no `domain` nem nas garantias transacionais; TI-01 a TI-19 e E2E-01 a E2E-11 servem de regressão, e TI-20/TI-21 verificam o uso correto do JPA.
13. **Ajustes de desempenho do ingresso e do outbox:** publicador do outbox por tópico com envio assíncrono do lote e marcação única, limite de ingresso com `503` e `Retry-After`, `connection-timeout` configurável, `POST` em um único comando com o preparo fora da transação, amostragem de traces configurável e gauge do outbox fora do pool de escrita, actuator na porta de gerenciamento e correção do diagnóstico de pool (TU-23, TU-24, TI-22 a TI-26 e a rodada 10k do E2E-08).
14. **Fila limitada e gravação em lote do ingresso:** `Semaphore` substituído por uma fila em memória com capacidade e espera máxima configuráveis (*bulkhead* com *backpressure*) e escritores que gravam as solicitações em lote, um comando por lote, com cada requisição individual e respondida só após o commit (TU-23, TU-25 a TU-31, TI-27 a TI-29 e as rodadas de 1k, 5k e 10k do E2E-08).

### Dependências técnicas

- Docker para o `docker compose` e para os Testcontainers (disponível: Docker 29.9, Compose v5.7).
- JDK 21 provisionado pela toolchain do Gradle (a máquina tem o JDK 27, que não é usado na compilação).
- Imagens `postgres:17`, `apache/kafka` (KRaft) e, opcionalmente, `grafana/otel-lgtm`.
- Bloqueador conhecido: o payload oficial ou o projeto base, pendentes com a recrutadora. Se chegarem, a mudança fica restrita a `entrypoint/model`.

## Monitoramento e observabilidade

- **Traces (OpenTelemetry, `spring-boot-starter-opentelemetry`):** spans HTTP → transação → outbox (o `traceparent` é gravado na linha e restaurado pelo publicador) → `KafkaTemplate`/listener (`observation-enabled`) → quatro consumidores. O `solicitacao_id` é atributo de span e entra no MDC (`solicitacaoId`, `traceId`). A amostragem é configurável por `MANAGEMENT_TRACING_SAMPLING_PROBABILITY`: `1.0` por padrão e nos testes (TI-14), reduzida nas rodadas de carga. No `POST`, o `solicitacao_id` é gravado no span HTTP atual e o evento `SolicitacaoRecebida` leva o `traceparent` desse span, sem observações próprias por requisição (cada uma criava timer, *long task timer* e span e pesava na CPU da API sob rajada). A `Observation` do registro do outbox fica só no evento `NotaEmitida`, no worker.
- **Métricas (Micrometer, `/actuator/prometheus`):**
  - `notafiscal.solicitacoes.recebidas` (counter, por cliente);
  - `notafiscal.notas.emitidas` (counter, por modo e série);
  - `notafiscal.solicitacoes.falhas` (counter, por motivo);
  - `notafiscal.emissao.duracao` (timer, de `recebida_em` a `emitida_em`, com percentis);
  - `notafiscal.outbox.pendentes` (gauge, por tópico, com valor em cache curto lido fora do pool de escrita, para não disputar conexões a cada coleta);
  - `notafiscal.ingresso.rejeitadas` (counter de `503`, com a tag `motivo`: `fila_cheia`, `espera_excedida` e `encerrando`);
  - `notafiscal.ingresso.fila.tamanho` (gauge da fila de gravação), `notafiscal.ingresso.lote.tamanho` (distribuição) e `notafiscal.ingresso.lote.duracao` (timer);
  - `notafiscal.numeracao.espera_lock` (timer);
  - `notafiscal.consumidor.atraso` (timer, por consumidor, de `ocorrido_em` ao processamento);
  - `notafiscal.consumidor.dlq` (counter, por consumidor);
  - `notafiscal.leitura.fallback` (counter, por motivo `recente`/`ultimo_numero`/`negado`);
  - `notafiscal.leitura.404_falso` (counter que precisa ser zero: incrementado quando um `404` é seguido de sucesso para o mesmo id dentro da janela, medido no teste de carga);
  - `notafiscal.replica.lag` (gauge via `pg_last_xact_replay_timestamp()`);
  - lag dos *consumer groups* (métricas do cliente Kafka).
- **Health:** `liveness` e `readiness` (banco primário e Kafka), excluídos dos logs de acesso, dos filtros e dos interceptadores. Na API, health e métricas ficam na porta de gerenciamento (`management.server.port`) quando configurada, com conector próprio, para que a coleta continue respondendo sob saturação do tráfego de negócio.
- **Logs:** JSON estruturado (`logging.structured.format.console=ecs`), em UTC. Nunca registrar `documento`, payload bruto ou `Idempotency-Key` completa; a chave aparece só como hash curto. Níveis:
  - `INFO`: solicitação aceita, nota emitida (id, série, número, duração), início e parada do publicador;
  - `WARN`: tentativa de worker ou consumidor que será repetida, fallback de leitura negado em volume anômalo;
  - `ERROR`: mensagem enviada para DLQ, `FALHA` definitiva, falha de publicação do outbox após retries, sempre com a mensagem original sanitizada;
  - validações de entrada (`400`/`422`) não geram `ERROR` nem stack trace.

## Considerações técnicas

### Principais decisões

- **Outbox transacional com polling `SKIP LOCKED`:** garante "registrado ⇔ será processado" e "emitido ⇔ será notificado" sem transação distribuída, e funciona com várias instâncias. *Alternativa:* CDC com Debezium, que tem menor latência e não faz polling, mas traz mais infraestrutura e operação. Fica no desenho como evolução (ADR-001).
- **Numeração por tabela contadora com `UPDATE ... RETURNING` na transação da nota:** sem lacunas, porque o rollback desfaz o incremento, e sem duplicidade, por causa do lock de linha. *Descartado:* `SEQUENCE`, porque o `nextval` não é transacional e deixa lacunas. *Trade-off:* emissões da mesma série são serializadas no commit; mitigado com o cálculo fora da transação, o lock adquirido por último e a possibilidade de várias séries (ADR-002).
- **Idempotência por restrição única `(cliente_id, idempotency_key)` com impressão digital:** atômica sob concorrência, sem lock na aplicação, e com o escopo por cliente, para não vazar informação entre clientes (ADR-003).
- **Processamento em duas transações (`PROCESSANDO` e depois emissão):** torna o estado `PROCESSANDO` observável e mantém a reentrega idempotente pelo `FOR UPDATE` na solicitação.
- **Leitura em réplica com fallback filtrado (UUIDv7 e último número):** zero `404` falso, sem carga na primária para ids antigos ou inexistentes. *Descartado:* cache Redis com escrita após o commit, que tiraria 100% da carga da primária, mas traz dupla escrita, mais infraestrutura e um modo de falha a mais (ADR-004). A réplica é ligada pelo `LazyConnectionDataSourceProxy` com *read-only data source* e `@Transactional(readOnly = true)`, sem `AbstractRoutingDataSource` próprio.
- **Spring Data JPA com consultas nativas pontuais:** padroniza a persistência em entidades e repositórios Spring Data, com mapeamento declarativo, métodos derivados e menos SQL manual nos gateways. Onde o JPQL não tem o recurso (`ON CONFLICT`, `UPDATE ... RETURNING`), a consulta nativa fica no próprio repositório, sem `JdbcClient`. *Trade-offs:* mais CPU e memória por transação (contexto de persistência e *dirty checking*), `merge` com `SELECT` prévio em ids atribuídos (evitado com `Persistable`), *batch* desligado por `IDENTITY` (evitado com `SEQUENCE` no outbox) e SQL gerado menos visível (verificado por TI-20/TI-21 e pelo log de SQL nos testes). *Substitui:* `JdbcClient` com SQL explícito, decisão original do ADR-005 (ADR-006).
- **Fila limitada com gravação em lote (*bulkhead* com *backpressure*):** com virtual threads, sem limite, todas as requisições disputam o pool ao mesmo tempo; nas rodadas de 10k, a fila chegou a milhares de aquisições pendentes, com o heap perto do limite, fechamentos de conexão e `500` após 30 s. O `Semaphore` da tarefa 13 limitava a concorrência, mas a espera era uma fila implícita e sem tamanho, e cada requisição ainda abria uma transação e reservava uma conexão. A fila limitada recusa o excesso na hora, e os escritores agrupam as gravações, de modo que poucas conexões e poucas transações atendem muitas requisições. *Alternativas:* aumentar o pool (o banco não é o gargalo e o orçamento é de 30 conexões), limitar só `server.tomcat.max-connections` (o cliente recebe recusa de conexão, sem `Retry-After`) ou responder `202` antes do commit (descartada: a consulta imediata não pode dar falso `404`). Registrado em ADR-007.
- **Publicação do outbox por tópico, com envio assíncrono do lote:** a confirmação por lote retira o limite de ~125 eventos/s do envio bloqueante, e o ciclo por tópico impede que `NotaEmitida` espere atrás do backlog de solicitações. Marcar só os confirmados preserva o *at-least-once* sem deixar um registro recusado bloquear o lote.
- **Virtual threads, sem coroutines:** I/O bloqueante simples e escalável. O cálculo é sequencial e em memória, porque é mais barato que paralelizar. Nada de `synchronized` em caminho quente (risco de *pinning* no JDK 21); onde houver lock local (cache *single-flight*), usar `ReentrantLock`. O *pinning* é detectado com `-Djdk.tracePinnedThreads=full` e com o evento JFR `jdk.VirtualThreadPinned` nos testes de carga. Exceção: os consumidores Kafka rodam em threads de plataforma (`KafkaListenerThreadsConfig`). No JDK 21, o cliente Kafka registra logs dentro de `synchronized`; com os consumidores em virtual threads, a espera pelo lock do appender prendeu os carriers e travou o worker na atribuição de partições (tarefa 16).
- **Consumidores assíncronos com `CompletableFuture`:** a latência simulada não ocupa threads, os quatro grupos têm offsets e DLQs próprios, e a falha de um não afeta os outros.
- **Evento com a nota completa:** os consumidores não precisam consultar a API. Os valores são idênticos aos do `GET` porque os dois partem do mesmo domínio `Invoice` (no `GET`, reconstruído das colunas), o que TI-07 verifica.
- **Modo de tributação como `TaxationStrategy` escolhida em `UseCaseConfig` por propriedade:** aberto para extensão e nunca inferido do payload.
- **Contrato JSON em `snake_case` com números de 2 casas:** mantém o vocabulário do enunciado (`valor_unitario`, `valor_frete`).

### Riscos conhecidos

- **Rajada de conexões, JVM e contenção do host no `POST`.** Com 2 CPUs, as duas rodadas oficiais da tarefa 15 falharam a meta de 3 s (máximos de 15,5 s e 25,3 s) enquanto o Mac compartilhado tinha carga média de 15 a 20; experimentos prévios em 2 CPUs passaram quando o host estava menos ocupado. Em 4 CPUs, experimentos exploratórios passaram em duas de três rodadas, com uma parada de JVM sem causa confirmada. `accept-count=4096` eliminou transbordos observados e o aquecimento ajuda, mas não remove contenção externa. Tarefa 16 mede as instâncias com até 4 CPUs, registra carga do host e logs de GC/safepoints; a carga inicial acima dos núcleos marca a rodada como potencialmente contaminada.
- **Contenção no contador da série a 1.000 req/s.** Mitigação: lock adquirido por último, *batch* de itens, métrica `espera_lock`. Se o teste de carga mostrar saturação, várias séries distribuídas por partição (decisão a registrar em ADR, sem mudar o contrato, porque o link já inclui a série).
- **Pool de conexões como gargalo com virtual threads.** Mitigação: dimensionar pelo teste de carga, limitar a concorrência do listener por partição (10 partições × instâncias) e limitar o ingresso do `POST` (`ingresso.*`).
- **`503` sob carga conta como erro no relatório e a fila consome memória.** A fila limitada recusa o excesso na hora, mas o `503` não aumenta a vazão. Na tarefa 13 (50 vagas, espera de 8 s), só ~19% da rajada de 10k era aceita. Na tarefa 14, a gravação em lote e o perfil de carga (capacidade 10.000, espera de 50 s) aceitaram 100% dos `POST` em 1k, 5k e duas rodadas de 10k, com `POST` p50 de 12,6 a 16,5 s no 10k. Com os padrões da aplicação (1.000 e 8 s), o excedente de uma rajada de 10k recebe `503`. A fila de 10.000 levou a API a 1,92 GiB de um limite de 2 GiB, então capacidade e memória precisam ser dimensionadas juntas. O gargalo passou para o `GET` imediato (pool de leitura de 10 conexões com milhares de pendentes; em uma rodada, 97 `500` por `connection-timeout` de 30 s) e para a CPU da API (235% a 300%), ainda sem profiler (ADR-007).
- **Outbox gravado por comando nativo e por entidades na mesma sequência.** O comando nativo consome um bloco do incremento por linha, e a ordem por `id` deixa de seguir exatamente a ordem de gravação entre instâncias. Mitigação: a ordem só importa por chave (a solicitação é publicada antes de existir a nota) e o TI-24 verifica a ausência de colisão.
- **Orçamento agregado de conexões ao escalar API e worker independentemente.** Mitigação: dimensionar os limites Hikari por papel e pelo número de réplicas para manter folga dentro do limite do PostgreSQL; acompanhar uso e espera de cada pool, sessões `idle in transaction`, retenção de conexão e backlog do outbox.
- **Fórmula do `POR_DENTRO` não confirmada.** Mitigação: estratégia isolada, que pode ser trocada sem efeito no restante.
- **Compatibilidade de detekt e ktlint com Kotlin 2.3.21 e de bibliotecas com o Spring Boot 4.1.2 (Spring REST Docs, Gatling).** Mitigação: validar as versões no build; se o detekt não for compatível, registrar e usar só ktlint até haver uma versão compatível.

### Decisão de documentação HTTP

O requisito original de OpenAPI foi substituído por documentação com Spring REST Docs, conforme `.agents/rules/spring-rest-docs.md`. Os testes MVC são a fonte executável do contrato, e o Asciidoctor gera a documentação navegável. A aplicação não publica `/v3/api-docs` ou Swagger UI; os contratos de rota e payload permanecem inalterados.
- **Jackson 3:** o equivalente exato de `USE_BIG_DECIMAL_FOR_FLOATS` e `ACCEPT_FLOAT_AS_INT` em `tools.jackson` precisa ser confirmado na tarefa 5. O TI-13 e o TU-18 protegem o comportamento.
- **Uso incorreto do JPA nos caminhos quentes.** `merge` com `SELECT` oculto, *batch* desligado, N+1 e carregamento preguiçoso fora da transação. Mitigação: `Persistable` nos ids atribuídos, `SEQUENCE` no outbox, entidades sem associações bidirecionais, `open-in-view=false` e TI-20/TI-21.
- **Hibernate 7 com Kotlin e Jackson 3.** Entidades exigem construtor sem argumentos e classes abertas (`plugin.jpa` e all-open); o JSON fica como `String` com `@JdbcTypeCode(SqlTypes.JSON)` para não depender do `FormatMapper` do Hibernate com Jackson 3. Validar na tarefa 12 e confirmar que o código sintético dos plugins não entra na medição do Kover.
- **Cobertura de 100% no `app`:** adaptadores de persistência e Kafka não são verificáveis de forma significativa sem infraestrutura. Decisão aprovada: o Kover do `app` agrega `test` e `integrationTest` (ver desvios). O risco restante é o tempo do `koverVerify` no CI, que passa a depender dos Testcontainers.
- **`404` falso por cache desatualizado do último número.** Mitigação: releitura *single-flight* quando o número pedido for maior que o valor em cache, e métrica `404_falso`.
- **Crescimento do outbox.** Mitigação: `OutboxCleanupJob` com a retenção configurada e índice parcial para os pendentes.

### Conformidade com o AGENTS.md e as rules

Foram lidos o `AGENTS.md` (`CLAUDE.md` é um link para ele) e todas as rules: `clean-architecture.md`, `code-standards.md`, `e2e-tests.md`, `folder-structure.md`, `gradle.md`, `kotlin.md`, `logs.md` e `unit-tests.md`.

Restrições aplicadas:

- `app → domain`; `domain` sem Spring, JDBC/JPA, Kafka ou Jackson; entidades JPA e repositórios Spring Data só no `app` (`repository/`), sem atravessar as portas do domínio; portas de entrada e implementações dos casos de uso no `domain`, e só as portas de saída implementadas no `app`. `@Transactional` só no `app`, nas operações de gateway que cumprem a garantia (`registerOnce`, `issue`) e, com `readOnly = true`, nas leituras roteadas à réplica.
- Conversões com `toDomain`/`fromDomain` nos DTOs e entidades de borda. As notas de entrada citavam "um DTO e um mapper"; **não haverá classe Mapper**, conforme `code-standards.md`.
- Interfaces nas dependências de comportamento, sem generics próprios, sem `!!`, até 3 parâmetros por função, arquivos de até 100 linhas e funções de até 30.
- Constantes nomeadas para faixas, percentuais e limites (500 itens, 999.999.999).
- `Clock` injetado; nenhuma leitura implícita de horário no cálculo ou na conversão.
- Logs conforme `logs.md`: dados sensíveis mascarados, health checks fora dos logs, falhas de integração com a mensagem original sanitizada, `WARN` por tentativa e `ERROR` na falha definitiva.
- Testes: nomes em português entre crases, AAA/GWT, sem `Thread.sleep`, MockK só nas fronteiras, fixtures com argumentos padrão e a tag por tipo de teste.
- Gradle: Kotlin DSL, `tasks.register`/`configureEach`, catálogo de versões, plugin de convenção sem `allprojects`/`subprojects` e versões gerenciadas pelo BOM do Spring Boot.

Desvios registrados (decisão do usuário: corrigir o `AGENTS.md` numa tarefa de implementação):

- O `AGENTS.md` e a `e2e-tests.md` descrevem json-server, Numerator, transações de cartão e recebíveis e o `docker-compose.e2e.yml`, herdados de outro desafio. Esta TechSpec usa PostgreSQL e Kafka, e os E2E rodam contra Testcontainers em vez de json-server e Numerator. O `AGENTS.md` cita a toolchain Java 25; o PRD e o build usam Java 21.
- As rules citam os pacotes `com.db.project_mister_study` e `com.db.study_kubernetes`; o pacote existente `com.itau.desafio_diogo_bernard` é preservado, conforme a própria regra de preservar o pacote-base.
- `unit-tests.md` exige 100% de cobertura "por testes unitários". Para o `app`, a cobertura é medida agregando `test` e `integrationTest` (Kover por módulo), porque o SQL e as integrações Kafka só são verificáveis com infraestrutura real. O `domain` atinge 100% só com testes unitários. **Aprovado pelo usuário em 2026-11-14.**
- `folder-structure.md` sugere `migrations/<banco>/<base>/`; as migrations ficam em `migrations/postgresql/notafiscal/` e são incluídas como recurso do `app` (`processResources`), com `spring.flyway.locations=classpath:db/migration`.

### Conformidade com skills

- `criar-tasks`: decompõe esta TechSpec; os casos `TU-*`, `TI-*` e `E2E-*` são a fonte dos testes de cada tarefa.
- `executar-task`: implementação incremental. As portas da faixa `30**` dessa skill se aplicam a execuções isoladas da aplicação (por exemplo, `SERVER_PORT=3000`); o compose padrão usa `8080`.
- `executar-review`: verificação de conformidade com as rules e com esta TechSpec, incluindo `koverVerify`.
- `executar-qa`: valida os `CA-*`. A verificação por navegador, de acessibilidade e de responsividade **não se aplica** (não há UI); a evidência é feita por testes e relatórios.

### Arquivos relevantes e dependentes

**Existentes (modificados)**

- `settings.gradle.kts` — `include("app", "domain")`.
- `build.gradle.kts` — passa a raiz sem código; os plugins vão para os módulos e para `build-logic`.
- `src/main/kotlin/com/itau/desafio_diogo_bernard/DesafioDiogoBernardApplication.kt` → `app/src/main/kotlin/com/itau/desafio_diogo_bernard/Application.kt`.
- `src/main/resources/application.yaml` → `app/src/main/resources/application.yaml` (com perfis `local` e `test`).
- `src/test/kotlin/.../DesafioDiogoBernardApplicationTests.kt` → substituído pelos testes de integração do `app`.
- `AGENTS.md`, `.agents/rules/e2e-tests.md` — atualização dos desvios.
- `.gitignore` — incluir `.env`.

**Novos**

- `gradle/libs.versions.toml`, `build-logic/` (convenções Kotlin, Kover, detekt, ktlint e tarefas de teste).
- `domain/build.gradle.kts`, `domain/src/main/kotlin/com/itau/desafio_diogo_bernard/{model,usecase,gateway,exception}/**`, `domain/src/test/kotlin/**`.
- `app/build.gradle.kts`, `app/src/main/kotlin/com/itau/desafio_diogo_bernard/{entrypoint,gateway,config}/**`, `app/src/test/kotlin/**` (incluindo `e2e/`), `app/src/gatling/kotlin/**`.
- `migrations/postgresql/notafiscal/V1__esquema_inicial.sql` (e as seguintes).
- `docker-compose.yml` (profiles `replica` e `observabilidade`), `docker/postgres-replica/` (scripts de replicação), `Dockerfile`, `.env.example`.
- `scripts/carga.sh`, `scripts/verificar-numeracao.sql`.
- Tarefa 12: `app/src/main/kotlin/com/itau/desafio_diogo_bernard/repository/**` (repositórios Spring Data) e `repository/entity/**` (entidades JPA), `migrations/postgresql/notafiscal/V2__outbox_sequence.sql`, `docs/adr/ADR-006-spring-data-jpa.md`; alterações em `gradle/libs.versions.toml` (`spring-boot-starter-data-jpa`, plugin `kotlin("plugin.jpa")`), `app/build.gradle.kts`, `application.yaml`, `gateway/postgres/**`, `config/ObservabilityConfig.kt`, `entrypoint/job/OutboxCleanupJob.kt`; remoção de `config/JdbcClientConfig.kt`.
- Tarefa 13: `app/src/main/kotlin/com/itau/desafio_diogo_bernard/config/resilience/LimiteIngressoSolicitarEmissao.kt` (removido na tarefa 14), `exception/IngressCapacityExhaustedException.kt`, `migrations/postgresql/notafiscal/V3__outbox_pendente_por_topico.sql`, `docs/adr/ADR-007-limite-ingresso.md`; alterações em `config/UseCaseConfig.kt`, `config/ObservabilityConfig.kt`, `config/OutboxTracing.kt`, `config/properties/NotaFiscalProperties.kt`, `entrypoint/handlers/ProblemDetailsHandler.kt`, `entrypoint/job/OutboxPublisherJob.kt`, `gateway/postgres/{IssuanceRequestPostgresGateway,OutboxRepository,OutboxPublisherRepository}.kt`, `repository/{IssuanceRequestRepository,OutboxRepository}.kt`, `application.yaml`, `docker-compose.yml` (porta de gerenciamento, timeout e amostragem por ambiente), `scripts/lib-compose-isolated.sh`, `scripts/carga.sh`, `app/src/docs/asciidoc/index.adoc`.
- Tarefa 14: `app/src/main/kotlin/com/itau/desafio_diogo_bernard/gateway/postgres/lote/{IssuanceRequestWrite,IssuanceRequestBatchPersister,PostgresBatchPersister,PendingWrite,BatchWriter,IssuanceRequestBatchWriter,IssuanceRequestWriteQueue}.kt`, `repository/InsertBatch.kt`, `config/IngressMetrics.kt`, `exception/IngressRejectionReason.kt`; alterações em `config/{UseCaseConfig,ObservabilityConfig,BusinessMetrics}.kt`, `config/properties/NotaFiscalProperties.kt`, `exception/IngressCapacityExhaustedException.kt`, `entrypoint/handlers/ProblemDetailsHandler.kt`, `gateway/postgres/IssuanceRequestPostgresGateway.kt`, `repository/IssuanceRequestRepository.kt`, `application.yaml`, `docker-compose.yml`, `scripts/carga.sh`, `scripts/atualizar-relatorio-carga.py`, `docs/adr/ADR-007-limite-ingresso.md`.
- Tarefa 15: `app/src/gatling/kotlin/com/itau/desafio_diogo_bernard/load/{CargaComum,IngressoCargaSimulation}.kt`, `scripts/{carga-post.sh,lib-carga-coleta.sh,lib-carga-experimento.sh,verificar-ingresso.sql}`, `app/build.gradle.kts` (`-PloadScenario`), `docker-compose.yml` (`SERVER_TOMCAT_ACCEPT_COUNT`), `domain/.../usecase/validations/OrderFingerprint.kt`, `app/.../config/{ObservabilityCorrelation,OutboxTracing}.kt`, `app/.../gateway/postgres/OutboxPostgresGateway.kt`, `app/.../entrypoint/solicitacoes/IssuanceRequestController.kt`.
- `.github/workflows/ci.yml` (build, `test`, `integrationTest`, `e2eTest`, `koverVerify`, detekt, ktlint; carga em execução manual ou agendada).
- `docs/adr/ADR-001` a `ADR-005`, `docs/arquitetura.md` (desenho AWS: API Gateway, Cognito, VPC Link, ECS Fargate/EKS, RDS/Aurora com réplica, MSK, ADOT), `docs/deploy.md` (ambientes, blue/green ou canary, migrations *expand/contract* compatíveis com a versão anterior, rollback e checklist), `docs/carga/relatorio.md`, `docs/exemplos/*.http`, `README.md`.
