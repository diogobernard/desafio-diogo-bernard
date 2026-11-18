# Relatório de revisão de código — Emissão de Nota Fiscal

## Resumo

- Data: 2026-11-18
- Branch: `main` (sem commits; alterações no working tree)
- Status: **APROVADO COM RESSALVAS**
- Escopo revisado: separação entre o contrato HTTP e o formato persistido do pedido (`StoredOrder`, `StoredRecipient`, `StoredOrderItem`, `StoredAddress`); validação de entrada movida para `IssuanceRequestController.create`; remoção dos `fromDomain` dos DTOs de request; e correções de testes afetados pela renomeação do contrato HTTP para inglês (ver `qa.md`, BUG-01 a BUG-04).

## Conformidade com regras

| Regra | Status | Observações |
|------|--------|-------------|
| `clean-architecture.md` — conversão na fronteira e independência entre adaptadores | OK | O repositório deixou de depender de `entrypoint.model.IssueInvoiceRequest`; persistência e HTTP têm tipos de borda próprios. `domain` não foi alterado. |
| `code-standards.md` — `toDomain`/`fromDomain` no tipo de borda, sem Mapper | OK | `Stored*` implementam as duas direções; os requests mantêm só `toDomain`/`toInput`, conforme "implementar somente as direções necessárias". |
| `code-standards.md` — 150 linhas por arquivo, 30 por função, 3 parâmetros | OK | Maior arquivo de produção alterado: `IssuanceRequestController.kt` (130 linhas); `create` com 28 linhas. |
| `code-standards.md` — strings mágicas e comentários | OK | Mensagem de documento obrigatório extraída para `DOCUMENTO_OBRIGATORIO`; único KDoc (`StoredOrder`) explica uma decisão de compatibilidade. |
| `kotlin.md` — nulabilidade apenas quando faz parte do contrato | OK com ressalva | `Stored*` são não anuláveis, exceto `taxRegime` (PF sem regime). `IssueInvoiceRequest.toDomain` usa casts `as` em campos anuláveis, mantidos por decisão do usuário (ver Problemas). |
| `kotlin.md` — nomes em inglês e contratos externos fixados na borda | OK | Classes e propriedades em inglês; chaves do `jsonb` em português via `@JsonProperty`, coerentes com as colunas do banco. |
| `unit-tests.md` / `e2e-tests.md` — nomes em português, AAA, testes significativos | OK | `StoredOrderTest`, novos casos em `IssuanceRequestControllerValidationTest` e TI-07 comparando valores. |
| `logs.md` — dados sensíveis | OK | Documentos fictícios nos testes e na evidência; nenhum log novo. |
| `spring-rest-docs.md` — sem anotações Swagger | OK | `grep -rn io.swagger app/src/main domain/src/main` vazio; testes de documentação passaram. |
| `gradle.md` | NÃO APLICÁVEL | Build não alterado. |

## Aderência à TechSpec

| Decisão Técnica | Implementado | Observações |
|-----------------|--------------|-------------|
| `OrderInputValidator` como única validação do pedido; o caso de uso não revalida | SIM | Desvio aprovado pelo usuário: a chamada saiu da conversão do request e foi para o `create` do controller. TechSpec atualizada (seção de componentes do `domain`). |
| DTOs de entrada com campos anuláveis para reunir todas as violações | SIM | Inalterado; `IssueInvoiceRequest(null, null, null, null)` produz 5 violações. |
| Coluna `pedido` (`jsonb`) mapeada como `String` e serializada pelo `JsonMapper` | SIM | O conteúdo passou a ser `StoredOrder`. TechSpec atualizada com o formato e a justificativa. |
| Idempotência por impressão digital canônica do domínio | SIM | `OrderFingerprint` não mudou; o reenvio de um pedido gravado antes da renomeação devolve o id original. |
| Evento `NotaEmitida` com `nota` em `InvoiceEventPayload`, valores idênticos ao `GET` (TI-07) | SIM | TI-07 passou a comparar valores; o formato do evento não mudou. |
| Endpoints e contrato HTTP | SIM | Nenhuma mudança de rota, status ou campo. |

## Tarefas verificadas

| Tarefa | Status | Observações |
|------|--------|-------------|
| 1.0–16.0 | COMPLETA | Todas marcadas como concluídas em `tasks.md`. Esta revisão não introduz tarefa nova; a mudança afeta o escopo das tarefas 5.0 (API HTTP), 6.0 (worker) e 12.0 (persistência JPA), cujos testes (TU-18 a TU-20, TI-01 a TI-08, TI-16) foram reexecutados e passaram. |

## Testes

- Total de testes: 256 (147 unidade `app`, 49 unidade `domain`, 53 integração, 7 E2E HTTP), além do smoke Compose (E2E-10/11)
- Passando: 256 e o smoke
- Falhando: 0
- Cobertura: 100% de linhas e branches por módulo (`app` 1.587/1.587 linhas e 341/341 branches; `domain` 486/486 e 222/222); `koverVerify` aprovado
- Comandos: `./gradlew help --warning-mode=all build -x koverVerify --rerun-tasks`; `./gradlew test :app:integrationTest :app:e2eTest detekt ktlintCheck koverHtmlReport koverVerify`; `./gradlew :app:e2eTest --rerun-tasks detekt ktlintCheck` (após a correção do E2E); `bash scripts/smoke-e2e.sh`; `git diff --check`

## Problemas encontrados

| Severidade | Arquivo | Linha | Descrição | Sugestão |
|------------|---------|-------|-----------|----------|
| Baixa | `app/src/main/kotlin/com/itau/desafio_diogo_bernard/entrypoint/model/IssueInvoiceRequest.kt` | 15–23 | `toDomain()` usa casts `as` em propriedades anuláveis e depende de o controller ter chamado o validador antes. Mantido por decisão do usuário: o único ponto de entrada de nulos é o request do cliente, validado no `create`. | Se o DTO passar a ser usado fora do controller, trocar os casts por `requireNotNull` com mensagem, conforme `kotlin.md`. |
| Baixa | Banco local | — | Solicitações gravadas com o formato HTTP entre a renomeação do contrato e esta correção não são legíveis por `StoredOrder`. Não há dados fora de ambientes locais (repositório sem commits). | Recriar o banco local com `bash scripts/subir-ambiente.sh --limpar`. Uma migration só seria necessária se esse formato tivesse chegado a outro ambiente. |

## Pontos positivos

- O formato persistido ficou estável e desacoplado da API: o mesmo erro não volta numa próxima renomeação do contrato HTTP.
- A falha de leitura de um dado interno deixou de aparecer ao cliente como erro de validação.
- O teste `le pedido gravado antes da renomeacao do contrato HTTP` reproduz o caso real com o JSON que estava no banco.
- A revisão encontrou e corrigiu testes que não exercitavam o que diziam (parâmetros SQL renomeados e chaves antigas).

## Recomendações

- Antes de novas renomeações do contrato HTTP, procurar nos testes nomes antigos usados como chaves de parâmetro SQL e em expressões regulares sobre respostas, como foi feito nesta revisão.
- Manter o `StoredOrder` fora de renomeações futuras; mudanças no formato gravado devem seguir expand/contract com migration, conforme `docs/deploy.md`.

## Conclusão

**APROVADO COM RESSALVAS.** As mudanças seguem as regras do projeto e a TechSpec, que foi atualizada para registrar a validação no controller e o formato próprio do `pedido`, ambos decididos pelo usuário. Todos os testes, a análise estática, o smoke Compose e a cobertura de 100% passaram. As ressalvas são de baixa severidade e não bloqueiam: os casts do `IssueInvoiceRequest`, aceitos por decisão explícita, e dados locais antigos que se resolvem recriando o banco.
