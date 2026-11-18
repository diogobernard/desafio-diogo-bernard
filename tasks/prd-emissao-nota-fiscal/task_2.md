# Tarefa 2.0: Domínio de cálculo

## Visão geral

Implementar no `domain` o cálculo puro da nota fiscal: aritmética monetária exata (`Dinheiro`, escala 2, `HALF_EVEN`), tabelas de alíquota (PF e PJ por regime) e de frete por região como dados imutáveis, as estratégias `TributacaoPorFora` e `TributacaoPorDentro` e a função `CalcularNotaFiscal`, sem estado compartilhado nem I/O.

<skills>
### Conformidade com skills

- `executar-task`: implementação incremental desta tarefa.
</skills>

<rules>
### Conformidade com o AGENTS.md e as rules

Lidos o `AGENTS.md` e todas as rules em `.agents/rules/`. Relevantes:

- `clean-architecture.md`: invariantes no domínio, sem framework, cálculo dependente só de dados normalizados.
- `code-standards.md`: constantes nomeadas para faixas e percentuais, arquivos de até 100 linhas, funções de até 30, até 3 parâmetros, sem generics próprios, interfaces nas dependências de comportamento.
- `kotlin.md`: `value class`/`data class`, `sealed`, `when` exaustivo, sem `!!`.
- `unit-tests.md`: JUnit Jupiter, `kotlin.test`, nomes em português entre crases, AAA, testes parametrizados para limites, sem espelhar o algoritmo no resultado esperado, 100% de cobertura no `domain` só com testes unitários.
</rules>

<requirements>
- RF6: execuções sem acúmulo de itens (função pura).
- RF27 a RF33: valores exatos, normalização do unitário, totais por item, tributo por item, frete por região e tabelas de alíquota.
- RF35 a RF38: modos `POR_FORA` e `POR_DENTRO`, ambos implementados, nunca inferidos do payload.
</requirements>

## Subtarefas

- [x] 2.1 Criar `model/dinheiro`: `Dinheiro` (value class sobre `BigDecimal`, escala 2, `HALF_EVEN`) e `Aliquota`.
- [x] 2.2 Criar os modelos de pedido em `model/pedido` necessários ao cálculo (`Pedido`, `Destinatario`, `TipoPessoa`, `RegimeTributacao`, `ItemPedido`, `Endereco`, `Finalidade`, `Regiao`) com suas invariantes.
- [x] 2.3 Criar `model/nota`: `NotaCalculada`, `ItemCalculado`, `ModoTributacao` e o resultado `TributacaoCalculada`.
- [x] 2.4 Implementar `TabelaAliquotas` (PF e PJ por regime, faixas com limites inclusivos) e `TabelaFrete` (percentuais por região).
- [x] 2.5 Implementar `EstrategiaTributacao` com `TributacaoPorFora` e `TributacaoPorDentro`.
- [x] 2.6 Implementar `CalcularNotaFiscal.calcular(pedido, modo)` como função pura.
- [x] 2.7 Escrever os testes de unidade TU-01 a TU-07 e TU-16.

## Detalhes de implementação

Ver na TechSpec: "Visão dos componentes" (pacotes `model/pedido`, `model/dinheiro`, `model/nota`, `usecase/calculo`), "Principais interfaces" (`EstrategiaTributacao`, `TabelaAliquotas`, `CalcularNotaFiscal`), "`NotaFiscalResponse`" (exemplo de 668,40 e variante `POR_DENTRO` de 602,40) e "Parâmetros fixos" (tabelas como dados imutáveis).

## Critérios de aceitação relacionados

- CA-16
- CA-17
- CA-18
- CA-19
- CA-20
- CA-21

## Testes da tarefa

### Testes de unidade

- [x] TU-01 — `aplica aliquota PF conforme a faixa nos limites`
- [x] TU-02 — `aplica aliquota PJ por regime e faixa nos limites`
- [x] TU-03 — `calcula acrescimo de frete por regiao com HALF_EVEN`
- [x] TU-04 — `arredonda tributo por item e soma das linhas igual ao total`
- [x] TU-05 — `normaliza valor unitario com mais de duas casas`
- [x] TU-06 — `calcula tributacao por dentro por item`
- [x] TU-07 — `calcula total da nota por fora`
- [x] TU-16 — `calculos de pedidos distintos sao independentes`

## Arquivos relevantes

- `domain/src/main/kotlin/com/itau/desafio_diogo_bernard/model/dinheiro/**`
- `domain/src/main/kotlin/com/itau/desafio_diogo_bernard/model/pedido/**`
- `domain/src/main/kotlin/com/itau/desafio_diogo_bernard/model/nota/**`
- `domain/src/main/kotlin/com/itau/desafio_diogo_bernard/usecase/calculo/**`
- `domain/src/test/kotlin/com/itau/desafio_diogo_bernard/usecase/calculo/**`
- `domain/src/test/kotlin/com/itau/desafio_diogo_bernard/model/**`
