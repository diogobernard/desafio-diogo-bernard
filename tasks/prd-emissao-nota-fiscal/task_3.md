# Tarefa 3.0: Validação, modelo de solicitação e casos de uso

## Visão geral

Implementar no `domain` a validação do pedido (violações `AUSENTE`, `MALFORMADO` e `IMPEDITIVO`), a impressão digital canônica para idempotência, o mascaramento de documento, o ciclo de vida da solicitação, as portas de saída e os casos de uso `SolicitarEmissao`, `ProcessarSolicitacao`, `RegistrarFalhaSolicitacao`, `ConsultarSolicitacao` e `ConsultarNotaFiscal`, com a `PoliticaLeituraConsistente`. Tudo testado com fakes e MockK nas portas, sem Spring.

<skills>
### Conformidade com skills

- `executar-task`: implementação incremental desta tarefa.
</skills>

<rules>
### Conformidade com o AGENTS.md e as rules

Lidos o `AGENTS.md` e todas as rules em `.agents/rules/`. Relevantes:

- `clean-architecture.md` e `folder-structure.md`: portas de entrada e implementações no `domain`, portas de saída em `gateway/`, um caso de uso não chama outro, idempotência como semântica do caso de uso, exceções sem `HttpStatus`, `Clock` injetado.
- `code-standards.md`: interfaces nas dependências, cláusulas de guarda, limites de tamanho e de parâmetros, sem generics próprios.
- `logs.md`: `Documento.toString()` nunca expõe o valor; máscara para uso nos logs.
- `unit-tests.md`: MockK só nas fronteiras, `verify(exactly = 0)` quando a ausência de chamada é a regra, `Clock.fixed`, 100% de cobertura no `domain`.
</rules>

<requirements>
- RF2, RF7 a RF11: validação síncrona, chave obrigatória, mesma chave e payload → mesmo id, payload diferente → erro, chave sem expiração.
- RF5: estados `RECEBIDA → PROCESSANDO → EMITIDA | FALHA`, motivo legível.
- RF12 a RF17: consultas com isolamento por cliente, sem `404` falso e sem carga na primária para ids antigos ou inexistentes.
- RF19 a RF21, RF34: classificação de erros `400` × `422`.
- RF41: id técnico distinto do número fiscal.
- RF50: CPF e CNPJ mascarados.
</requirements>

## Subtarefas

- [x] 3.1 Criar `model/solicitacao` (`Solicitacao`, `SolicitacaoId`, `StatusSolicitacao`, `ClienteId`, `ChaveIdempotencia`, `ImpressaoDigital`, `MotivoFalha`) e `Documento` com `mascarado()`.
- [x] 3.2 Criar `NotaFiscal`, `NumeroFiscal`, `Serie` e `ReferenciaNota` em `model/nota`.
- [x] 3.3 Implementar `ValidadorPedido` e `Violacao` em `usecase/validations`, reunindo todas as violações com o caminho do campo.
- [x] 3.4 Implementar a impressão digital SHA-256 da forma canônica do pedido.
- [x] 3.5 Criar as portas em `gateway/` (`SolicitacaoGateway`, `RegistroSolicitacao`, `ProcessamentoGateway`, `SolicitacaoLeituraGateway`, `NotaFiscalLeituraGateway`, `GeradorSolicitacaoId`).
- [x] 3.6 Criar as exceções de `exception/`.
- [x] 3.7 Implementar `SolicitarEmissaoUseCase`, `ProcessarSolicitacao`, `RegistrarFalhaSolicitacao`, `ConsultarSolicitacao` e `ConsultarNotaFiscal` com a `PoliticaLeituraConsistente(janela, clock)`.
- [x] 3.8 Escrever os testes de unidade TU-08 a TU-15 e TU-17.

## Detalhes de implementação

Ver na TechSpec: "Visão dos componentes" (pacotes `model/solicitacao`, `usecase/validations`, `usecase/emissao`, `usecase/processamento`, `usecase/consulta`, `gateway`, `exception`), "Principais interfaces", "Mapeamento DTO → domínio" (forma canônica da `ImpressaoDigital`), "`ProblemDetails`" (códigos e precedência do `400`) e os algoritmos de `GET /issuance-requests/{id}` e `GET /invoices/{numero}`.

## Critérios de aceitação relacionados

- CA-02
- CA-03
- CA-04
- CA-05
- CA-06
- CA-09
- CA-10
- CA-11
- CA-12
- CA-13
- CA-14
- CA-15
- CA-22
- CA-28

## Testes da tarefa

### Testes de unidade

- [x] TU-08 — `classifica dados ausentes e malformados como violacoes de entrada`
- [x] TU-09 — `classifica regime OUTROS e mais de 500 itens como impeditivos`
- [x] TU-10 — `registra solicitacao nova e reaproveita existente pela impressao digital`
- [x] TU-11 — `impressao digital ignora formatacao e detecta mudanca de valor`
- [x] TU-12 — `processa solicitacao e ignora reentrega ja emitida`
- [x] TU-13 — `registra falha com motivo legivel`
- [x] TU-14 — `aplica politica de leitura consistente pela idade do id`
- [x] TU-15 — `consulta nota com fallback ate o ultimo numero e isolamento por cliente`
- [x] TU-17 — `mascara CPF e CNPJ`

Validação executada: compilação Kotlin 2.3.21 das fontes e dos testes de `domain`, seguida do JUnit Platform Launcher, com 31 testes aprovados. O Gradle Wrapper e as verificações Kover/ktlint não puderam ser executados neste sandbox.

## Arquivos relevantes

- `domain/src/main/kotlin/com/itau/desafio_diogo_bernard/model/solicitacao/**`
- `domain/src/main/kotlin/com/itau/desafio_diogo_bernard/model/pedido/Documento.kt`
- `domain/src/main/kotlin/com/itau/desafio_diogo_bernard/model/nota/**`
- `domain/src/main/kotlin/com/itau/desafio_diogo_bernard/usecase/{validations,emissao,processamento,consulta}/**`
- `domain/src/main/kotlin/com/itau/desafio_diogo_bernard/gateway/**`
- `domain/src/main/kotlin/com/itau/desafio_diogo_bernard/exception/**`
- `domain/src/test/kotlin/com/itau/desafio_diogo_bernard/**`
