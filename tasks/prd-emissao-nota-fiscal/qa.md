# Relatório de QA — Emissão de Nota Fiscal

As evidências desta execução estão em `tasks/prd-emissao-nota-fiscal/evidences/`. O produto é uma API machine-to-machine sem interface gráfica; não há telas a capturar no navegador, e os fluxos foram validados por HTTP.

## Resumo

- Data: 2026-11-18 (UTC)
- Status: **APROVADO**
- Total de critérios de aceitação: 30
- Critérios de aceitação atendidos: 30
- Bugs encontrados: 4 (1 de produto e 3 em testes automatizados), todos corrigidos
- Motivação: revalidar a separação entre o contrato HTTP e o formato do pedido gravado (`StoredOrder`) e a validação de entrada movida para o `IssuanceRequestController`, após a renomeação do contrato HTTP para inglês.
- Ambiente: testes de integração e E2E com Testcontainers (PostgreSQL 17 e Kafka descartáveis); smoke Compose em projeto isolado (`notafiscal-smoke-<pid>`, portas livres escolhidas pelo script e removido ao final); ambiente local do usuário (`desafio-diogo-bernard-*`, portas 8080/8081/8082) usado somente para a evidência HTTP do BUG-01 e preservado.

## Critérios de aceitação verificados

| ID | Critério de aceitação | Casos de teste | Status | Evidência |
|----|-----------------------|----------------|--------|-----------|
| CA-01 | POST válido retorna 202, id, Location e Retry-After | TU-20, TI-01, E2E-01 | PASSOU | `:app:test`, `:app:integrationTest`, `:app:e2eTest` |
| CA-02 | Entradas ausentes/malformadas retornam 400 Problem Details sem persistência | TU-08, TU-18, TU-19, TI-02, TI-13 | PASSOU | `IssuanceRequestControllerValidationTest`, [evidência HTTP](evidences/qa-20261118-pedido-gravado.md) (passo 3) |
| CA-03 | Regime OUTROS e mais de 500 itens retornam 422 | TU-09, TU-19, TI-02, E2E-07 | PASSOU | `:app:e2eTest` |
| CA-04 | POST sem Idempotency-Key retorna 400 | TU-08, TU-18, TI-02, TI-13 | PASSOU | `IssuanceRequestControllerValidationTest` (chave validada antes do corpo) |
| CA-05 | Reenvio idempotente retorna o mesmo id sem nova solicitação | TU-10, TU-11, TI-03, E2E-03 | PASSOU | [evidência HTTP](evidences/qa-20261118-pedido-gravado.md) (passo 1), `StoredOrderTest` |
| CA-06 | Reuso da chave com payload diferente retorna 422 | TU-10, TU-11, TU-19, TI-04 | PASSOU | [evidência HTTP](evidences/qa-20261118-pedido-gravado.md) (passo 2) |
| CA-07 | Requisições simultâneas com a mesma chave criam uma solicitação | TI-03 | PASSOU | `IssuanceRequestHttpIntegrationTest` |
| CA-08 | Entradas inválidas não retornam 500 nem geram notas incompletas | TU-19, TI-13 | PASSOU | `:app:integrationTest` |
| CA-09 | Solicitação RECEBIDA/PROCESSANDO expõe estado, datas e Retry-After | TU-12, TU-20, TI-01, E2E-01 | PASSOU | `:app:test`, `:app:e2eTest` |
| CA-10 | Solicitação EMITIDA fornece link consultável para a nota completa | TU-12, TU-15, TU-20, TI-17, E2E-01 | PASSOU | `:app:e2eTest`, [evidência HTTP](evidences/qa-20261118-pedido-gravado.md) (passo 4) |
| CA-11 | Solicitação FALHA retorna motivo legível sem detalhes internos | TU-13, TI-16 | PASSOU | `:app:integrationTest` |
| CA-12 | GET imediato após 202 nunca retorna 404 falso | TU-14, TU-22, TI-09, E2E-04, E2E-08/09 | PASSOU | `:app:e2eTest` (300 POST/GET); [combinado 1k](../../docs/carga/relatorio.md) |
| CA-13 | Solicitações/notas de outro cliente retornam 404 | TU-15, TI-12 | PASSOU | `:app:integrationTest` |
| CA-14 | Solicitação ou nota inexistente retorna 404 | TU-14, TU-15, TI-09 | PASSOU | `:app:integrationTest` |
| CA-15 | Varredura de ids inexistentes não consulta o banco de escrita | TU-14, TI-09 | PASSOU | `:app:integrationTest` |
| CA-16 | Alíquotas seguem as faixas de PF e regimes PJ | TU-01, TU-02, E2E-01, E2E-02 | PASSOU | `:domain:test`, E2E-02 após BUG-04 |
| CA-17 | Frete regional usa HALF_EVEN em 2 casas | TU-03, E2E-01 | PASSOU | `:domain:test`, `:app:e2eTest` |
| CA-18 | Totais de item, nota e evento são consistentes ao centavo | TU-04, TI-07, E2E-02 | PASSOU | TI-07 após BUG-03 |
| CA-19 | Valor unitário é normalizado com HALF_EVEN | TU-05, TU-18, E2E-01 | PASSOU | `:domain:test`, `StoredOrderTest` (valor informado preservado) |
| CA-20 | POR_FORA/POR_DENTRO seguem fórmulas e configuração | TU-06, TU-07, E2E-06 | PASSOU | `:domain:test`, `:app:e2eTest` |
| CA-21 | Pedidos distintos não acumulam itens | TU-16, TI-08, E2E-12/13 | PASSOU | `ProcessingIntegrationTest` (consulta `pedido->'itens'` coerente com o formato gravado) |
| CA-22 | Numeração concorrente é única, sequencial e sem lacunas | TU-12, TI-05, E2E-12/13 | PASSOU | `:app:integrationTest` após BUG-02 (contador) |
| CA-23 | Quatro sistemas recebem a nota completa ao menos uma vez | TI-06, TI-11, E2E-05, E2E-13 | PASSOU | E2E-05 após BUG-02; smoke Compose |
| CA-24 | Consumidor lento/falho não bloqueia os demais | TU-21, TI-10, E2E-13 | PASSOU | `:app:integrationTest` |
| CA-25 | Falha persistente é enviada à DLQ sem perda | TI-10, TI-30, E2E-13 | PASSOU | `:app:integrationTest` |
| CA-26 | Cargas 1k/5k/10k registram latência, vazão, drenagem, recursos e integridade | E2E-08, E2E-12, E2E-13 | PASSOU | [Relatório de carga](../../docs/carga/relatorio.md), rodada de 17/11 22:02–22:26 UTC |
| CA-27 | GET sob carga registra p50/p95/p99 e réplica registra lag/fallback | E2E-09, E2E-14 | PASSOU | [Relatório de carga](../../docs/carga/relatorio.md), reexecução de 17/11 22:40–23:01 UTC |
| CA-28 | Trace correlaciona emissão/consumidores e documentos ficam mascarados | TU-17, TI-14, TI-15 | PASSOU | `:app:integrationTest` |
| CA-29 | Compose sobe ambiente e CI bloqueia cobertura inferior a 100% | E2E-10, E2E-11, koverVerify | PASSOU | `bash scripts/smoke-e2e.sh` e `koverVerify` nesta execução |
| CA-30 | API/workers usam pools limitados e carga registra pools, transações, backlog e integridade | TI-19, E2E-11, E2E-12/13/14 | PASSOU | smoke E2E-11; [relatório de carga](../../docs/carga/relatorio.md) |

## Testes E2E executados

| ID | Fluxo | Resultado | Observações |
|----|-------|-----------|-------------|
| E2E-01 | Emissão PF até consulta da nota | PASSOU | `:app:e2eTest`, HTTP real com Testcontainers. |
| E2E-02 | Emissão PJ por regime | PASSOU | Falhou na primeira execução por BUG-04 (asserção do teste); produto calculava 0,13/0,15/0,16 corretamente. |
| E2E-03 | Reenvio idempotente | PASSOU | Mesmo id; também verificado manualmente com pedido gravado em formato anterior (BUG-01). |
| E2E-04 | GET imediato após aceite | PASSOU | 300 POST/GET; zero 404 falso. |
| E2E-05 | Notificação dos quatro sistemas | PASSOU | Falhou na primeira execução por BUG-02 (parâmetro SQL do teste). |
| E2E-06 | Emissão POR_DENTRO | PASSOU | Configuração validada. |
| E2E-07 | Limite de 500 itens | PASSOU | 500 aceitos; 501 rejeitado com 422. |
| E2E-08/09 | Carga combinada e GET imediato em 1k | PASSOU | Não reexecutado; rodada de 17/11 no relatório de carga. A mudança não altera o caminho de leitura do GET. |
| E2E-10/11 | Subida local e API/worker separados | PASSOU | `bash scripts/smoke-e2e.sh`: API e worker separados emitiram uma nota, concluíram as quatro notificações e expuseram métricas Prometheus. |
| E2E-12/13/14 | POST, consumo e consulta isolados | PASSOU | Não reexecutados; rodadas de 17/11 no relatório de carga. A mudança mantém o mesmo JSON gravado (somente nomes de chave) e o mesmo número de leituras. |
| Manual | Reenvio de pedido gravado antes da renomeação do contrato | PASSOU | [evidência HTTP](evidences/qa-20261118-pedido-gravado.md): 202 com o id original, 422 com outro corpo, 400 com corpo vazio, 200 na nota. |

## Testes automatizados e cobertura

| Camada | ID | Resultado | Validação/comando | Observações |
|--------|----|-----------|-------------------|------------|
| Build | — | PASSOU | `./gradlew help --warning-mode=all build -x koverVerify --rerun-tasks` | — |
| Unidade (`app`) | TU-* | PASSOU | `./gradlew test` | 147 testes, zero falhas. Inclui `StoredOrderTest` (5) e os novos casos de `IssuanceRequestControllerValidationTest`. |
| Unidade (`domain`) | TU-* | PASSOU | `./gradlew test` | 49 testes, zero falhas. |
| Integração | TI-* | PASSOU | `./gradlew :app:integrationTest` | 53 testes, zero falhas, após BUG-02 e BUG-03. |
| E2E HTTP | E2E-01..07 | PASSOU | `./gradlew :app:e2eTest --rerun-tasks` | 7 testes, zero falhas, após BUG-02 e BUG-04. |
| Smoke Compose | E2E-10/11 | PASSOU | `bash scripts/smoke-e2e.sh` | Projeto Compose isolado, removido ao final. |
| Análise estática | — | PASSOU | `./gradlew detekt ktlintCheck` | Sem violações. |
| Cobertura | — | PASSOU | `./gradlew koverHtmlReport koverVerify` | — |

- Cobertura (meta de 100% de linhas e branches por módulo): `app` 1.587/1.587 linhas e 341/341 branches; `domain` 486/486 linhas e 222/222 branches.

## Acessibilidade

- Navegação por teclado, rótulos, texto alternativo, contraste, formulários, fontes, estados visuais e breakpoints: **não aplicável**; o PRD define uma API sem interface gráfica.
- Mensagens de erro: verificadas nas respostas Problem Details (400 com erro por campo, 422 de idempotência) em testes e na evidência HTTP.

## Bugs encontrados e corrigidos

| ID | Descrição | Severidade | Status | Correção | Teste de regressão | Evidência |
|----|-----------|------------|--------|----------|--------------------|-----------|
| BUG-01 | O pedido gravado na coluna `pedido` usava o DTO HTTP `IssueInvoiceRequest`. Após a renomeação do contrato HTTP, o reenvio com uma chave já usada lia o pedido antigo com campos nulos e devolvia 400 "Campo obrigatório" ao cliente, embora o corpo estivesse correto; o worker leria o mesmo pedido e esgotaria as tentativas até a DLQ. | Alta | Corrigido | Novo formato de persistência `StoredOrder`/`StoredRecipient`/`StoredOrderItem`/`StoredAddress` em `repository/entity`, não anulável (exceto o regime tributário de PF), com chaves estáveis em português. `IssueInvoiceRequest` ficou restrito à camada HTTP, sem `fromDomain`; a validação de entrada passou para `IssuanceRequestController.create`. | `StoredOrderTest` (`le pedido gravado antes da renomeacao do contrato HTTP`, `grava com nomes proprios independentes do contrato HTTP`); `IssuanceRequestControllerValidationTest` (`reune todos os campos ausentes do corpo antes de solicitar a emissao`, `valida cliente e chave antes do corpo`). O teste de leitura falha com a implementação anterior, que não reconhece `destinatario`/`itens`. | [qa-20261118-pedido-gravado.md](evidences/qa-20261118-pedido-gravado.md) |
| BUG-02 | Testes passavam `.param("number", …)` para SQL com `:numero` (renomeação incidental), falhando com `No value supplied for the SQL parameter 'numero'` e impedindo a verificação de CA-22/CA-23. | Média | Corrigido | Chave do parâmetro restaurada para `numero` em `ConcurrentCounterReadIntegrationTest`, `CounterReadCacheIntegrationTest`, `IssuanceRequestGatewayMappingIntegrationTest` e `InvoiceIssuanceE2ETest`. | Os próprios testes, que agora executam e passam. | `:app:integrationTest`, `:app:e2eTest` |
| BUG-03 | TI-07 comparava o JSON bruto do evento `NotaEmitida` com o corpo do `GET /invoices`. Pela TechSpec, o `nota` do evento é `InvoiceEventPayload` (chaves em português) e o requisito é a igualdade dos **valores** (CA-18); com o contrato HTTP em inglês, a comparação textual sempre falhava. | Média | Corrigido | O teste converte a resposta do GET em `InvoiceResponse` e compara `InvoiceEventPayload.from(resposta)` com o `nota` do outbox lido como `InvoiceEventPayload`. Nenhuma alteração de produção. | `ProcessingIntegrationTest` › `evento NotaEmitida e identico a nota persistida` | `:app:integrationTest` |
| BUG-04 | E2E-02 procurava a chave antiga `"aliquota"` na resposta HTTP, que agora expõe `tax_rate`; a asserção falhava mesmo com a alíquota correta. | Baixa | Corrigido | Regex atualizada para `"tax_rate"`. | `InvoiceIssuanceE2ETest` › `emite nota PJ para cada regime suportado` | `:app:e2eTest` |

## Conclusão

**APROVADO (30/30 critérios atendidos).** O defeito de produto (BUG-01) foi corrigido na causa: o formato persistido do pedido deixou de depender do contrato HTTP, e a validação de entrada passou a ocorrer somente na borda HTTP. Com isso, o reenvio idempotente de um pedido gravado antes da renomeação do contrato devolve `202` com o id original. Os três defeitos restantes estavam em testes automatizados afetados pela mesma renomeação e foram corrigidos sem mudar o produto. Unidade, integração, E2E HTTP, smoke Compose, análise estática e cobertura de 100% passaram nesta execução. As rodadas de carga não foram repetidas; a mudança não altera o volume de leituras nem o caminho do GET. Solicitações gravadas localmente com o formato HTTP entre a renomeação e esta correção ficam ilegíveis; o banco local deve ser recriado com `bash scripts/subir-ambiente.sh --limpar`.
