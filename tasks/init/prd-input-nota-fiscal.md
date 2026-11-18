# Entrada para /criar-prd — API de Emissão de Nota Fiscal

> Uso: `/criar-prd --prompt "<conteúdo deste arquivo>"`
> Todas as decisões abaixo já foram discutidas e validadas. Use-as como respostas às perguntas da etapa "Esclarecer"; pergunte apenas o que não estiver coberto aqui. Detalhes de implementação estão em um arquivo separado (notas para a TechSpec) e não devem entrar no PRD.

## Contexto

Desafio técnico (Itaú) "Desafio Nota Fiscal - Kotlin". É uma API que recebe um pedido, calcula tributos e frete, gera a nota fiscal e notifica os sistemas de estoque, registro, entrega e financeiro.

O enunciado descreve uma aplicação legada com estes problemas:
- código difícil de manter, muitas regras de cálculo, fluxo complexo e uma classe "principal" instável;
- baixa cobertura de testes, alguns quebrando e outros passando sem garantir correção;
- itens de execuções anteriores se acumulam na resposta (só a primeira execução funciona);
- pedidos com mais de 6 itens têm tempo de processamento muito alto;
- a resposta fica mais lenta após algumas execuções;
- requisições simultâneas são processadas uma de cada vez;
- sistemas consumidores recebem valores inconsistentes de nota, tributos, frete e total de itens (centavos "sumindo" ou "sobrando");
- notas fiscais com numeração duplicada;
- pedidos com dados incompletos retornam erro 500 sem explicação, ou geram notas sem itens ou sem frete.

**Premissa principal:** o projeto será implementado do zero, porque o repositório base não foi recebido (a confirmação foi solicitada à recrutadora). O contrato de entrada é definido pelo candidato a partir dos termos do enunciado e deve poder ser substituído pelo payload oficial sem alterar as regras de cálculo.

## Problema e valor

Os sistemas que solicitam notas e os que as consomem precisam de notas corretas (valores exatos ao centavo), com numeração única e sequencial, e com erros de entrada explicados de forma clara. Os desenvolvedores precisam de um código fácil de manter, testado e simples de executar localmente; o desempenho deve ser caracterizado por medições no ambiente documentado.

## Usuários

- **Sistemas solicitantes** (machine-to-machine): pedem a emissão e consultam o status da solicitação ou a nota.
- **Sistemas consumidores**: estoque, registro, entrega e financeiro, notificados quando uma nota é emitida.
- **Desenvolvedores e operação**: mantêm, testam, observam e implantam a aplicação.
- **Avaliadores do desafio**: executam localmente e verificam os critérios.

## Fluxo principal

1. O sistema solicitante envia um `POST` de emissão com o pedido e o header `Idempotency-Key`.
2. A API valida o pedido de forma síncrona. Se for inválido, responde `400` ou `422` com uma explicação por campo e **não persiste nada**.
3. Se for válido, a API registra a solicitação e responde `202` com o identificador dela.
4. O processamento é assíncrono: cálculo de tributos e frete, numeração e geração da nota.
5. Os 4 sistemas consumidores são notificados de forma independente, cada um com sua própria latência e falhas.
6. O solicitante consulta o status pelo id da solicitação. Quando a nota está emitida, a resposta traz o status `EMITIDA`, o número da nota e o link para a consulta da nota completa.

**Estados da solicitação:** `RECEBIDA → PROCESSANDO → EMITIDA | FALHA`. Não existe estado de rejeição, porque um pedido inválido nunca é persistido. `FALHA` traz um motivo legível, sem detalhes internos.

## Funcionalidades e regras

### Emissão (POST)
- O header `Idempotency-Key` é obrigatório. Sem ele, a resposta é `400`.
- Mesma chave com o mesmo payload: devolve o mesmo `202` com o mesmo id, sem criar uma nova solicitação.
- Mesma chave com payload diferente: `422`.
- Duas requisições simultâneas com a mesma chave criam uma única solicitação.
- A chave nunca expira e nunca pode ser reutilizada. O cliente gera um UUID por pedido.
- O payload de entrada não pode ser alterado em relação ao contrato definido.

### Consulta
- `GET /issuance-requests/{id}`:
  - `RECEBIDA` ou `PROCESSANDO` → `200` com status e datas;
  - `EMITIDA` → `200` com o status, o número, a série, a data de emissão e o **link para `GET /invoices/{numero}`**. A nota completa não vem nesta resposta;
  - `FALHA` → `200` com o motivo;
  - id inexistente → `404`.
- `GET /invoices/{numero}`: a **única** representação da nota completa (número, série, itens com tributo, frete, totais, data de emissão). Número inexistente → `404`. Como a nota não muda depois de emitida, a resposta pode ser cacheada pelo cliente.
- Seguir o link de uma solicitação `EMITIDA` nunca pode resultar em `404`.
- Um cliente só enxerga as próprias solicitações e notas. As de outro cliente retornam `404`.
- O `202` informa **onde** consultar (`Location`) e **quando** consultar (`Retry-After`). O `GET` em `RECEBIDA` ou `PROCESSANDO` também devolve `Retry-After`, para orientar o polling.
- As consultas aceitam **consistência eventual** (por exemplo, mostrar `PROCESSANDO` por alguns instantes depois da emissão), mas **nunca podem devolver um `404` falso** para uma solicitação que acabou de ser aceita.
- A consulta de um id inexistente ou antigo não pode gerar carga no banco de escrita, para impedir abuso por varredura de ids.

### Validação e códigos de erro
Regra geral: **dado ausente ou malformado → `400`; dado presente que impede a emissão → `422`**. As respostas de erro seguem um formato padronizado (Problem Details, RFC 9457), com mensagem por campo. Um pedido inválido nunca resulta em `500` nem em uma nota sem itens ou sem frete.
- `400`:
  - JSON malformado;
  - campos obrigatórios ausentes;
  - pedido sem itens;
  - pedido sem endereço de entrega;
  - PJ sem regime de tributação;
  - `Idempotency-Key` ausente;
  - quantidade não inteira ou ≤ 0;
  - valores monetários negativos (premissa).
- `422`:
  - PJ com regime `OUTROS`;
  - mais de 500 itens no pedido;
  - `Idempotency-Key` reutilizada com payload diferente.

### Dados de entrada (contrato definido pelo candidato a partir do enunciado)
- Tipo de pessoa (física ou jurídica) e regime de tributação (Simples Nacional, Lucro Real, Lucro Presumido, Outros) quando for pessoa jurídica.
- Itens com `valor_unitario` e `quantidade` (sempre inteira).
- `valor_frete`.
- Endereços com finalidade e região (NORTE, NORDESTE, CENTRO_OESTE, SUDESTE, SUL).
- Premissa: existe um único endereço de entrega, com finalidade `ENTREGA` ou `COBRANCA_ENTREGA`. A região dele define o frete. Os demais endereços não entram no cálculo.

### Regras de cálculo (fonte da verdade do enunciado)
- Todos os valores monetários são exatos, com 2 casas decimais e arredondamento **HALF_EVEN**.
- `valor_unitario` com mais de 2 casas é aceito e normalizado (HALF_EVEN, 2 casas) na entrada. A nota e os eventos carregam o unitário normalizado.
- `total_item = valor_unitario_normalizado × quantidade`.
- `total_itens = Σ total_item`. Esse valor define a faixa da alíquota.
- `tributo_item = round(total_item × alíquota, 2, HALF_EVEN)`, com o arredondamento feito no fim da conta de cada item.
- `tributo_nota = Σ tributo_item`, com normalização em escala 2. A soma das linhas sempre bate com o total.
- `frete = valor_frete + round(valor_frete × percentual_região, 2, HALF_EVEN)`.
- Total da nota (modo por fora) = `total_itens + tributo_nota + frete`.

**Alíquota PF** (pelo total dos itens):

| Faixa | Alíquota |
|---|---|
| até 499,99 | 0% |
| 500,00 a 2.000,00 | 12% |
| 2.000,01 a 3.500,00 | 15% |
| acima de 3.500,00 | 17% |

**Alíquota PJ** (pelo total dos itens):

| Faixa | Simples Nacional | Lucro Real | Lucro Presumido |
|---|---|---|---|
| até 999,99 | 3% | 3% | 3% |
| 1.000,00 a 2.000,00 | 7% | 9% | 9% |
| 2.000,01 a 5.000,00 | 13% | 15% | 16% |
| acima de 5.000,00 | 19% | 20% | 20% |

PJ sem regime informado ou com regime `OUTROS` não pode ter a nota gerada.

**Acréscimo de frete por região:** NORTE 8%, NORDESTE 8,5%, CENTRO_OESTE 7%, SUDESTE 4,8%, SUL 6%. Pedido sem endereço de entrega não pode ter a nota gerada.

### Modo de tributação (configurável e explícito)
- `POR_FORA` (padrão, alinhado ao enunciado): o tributo é acrescentado ao valor dos itens.
- `POR_DENTRO`: o valor recebido já contém o tributo (bruto). A faixa é definida pelo total **bruto** dos itens. O tributo é extraído **por item** com `round(bruto_item × alíquota / (1 + alíquota), 2, HALF_EVEN)`, e o líquido é `bruto_item − tributo_item`. Total da nota = `total_itens_bruto + frete`. A fórmula é uma premissa, a confirmar quando o projeto base chegar.
- O modo **nunca** é inferido do payload (por exemplo, por um campo nulo). Ele é definido por configuração, ou por um campo explícito caso o payload oficial tenha um.
- Os dois modos serão implementados.

### Numeração
- Cada nota tem um número **sequencial, único e sem lacunas** por série, compatível com o campo nNF da NF-e (até 9 dígitos, de 1 a 999.999.999).
- Não pode haver duplicidade nem com processamento concorrente e várias instâncias.
- O identificador técnico da solicitação (devolvido no `202`) é diferente do número fiscal.

### Notificação dos sistemas consumidores
- Toda nota emitida é notificada a estoque, registro, entrega e financeiro **ao menos uma vez**, mesmo que haja falhas momentâneas.
- Há uma notificação por nota, com a lista completa de itens. A notificação não é feita por item.
- Os 4 sistemas são independentes entre si: a lentidão ou a falha de um não atrasa os outros.
- Os sistemas consumidores são simulados, com latência artificial (exigência do enunciado: o tempo das integrações não pode ser removido, só simulado de outra forma). Essa latência não pode bloquear o processamento de outras notas.
- Uma falha persistente em um consumidor não se perde: ela fica isolada para reprocessamento.

## Objetivos e métricas
- **Medição de latência:** registrar p50, p95, p99 e máxima do POST, do GET e do tempo entre `RECEBIDA` e `EMITIDA` para pedidos com até 50 itens; os valores observados são apresentados no relatório, sem limites predefinidos.
- **Carga e vazão:** executar rodadas de 1.000, 5.000 e 10.000 solicitações a uma taxa de chegada configurada de 1.000 req/s, num ambiente documentado (CPU, memória e recursos dos containers). O relatório registra vazão, uso de recursos e distribuições de latência para caracterizar cada execução.
- **Relatório do fluxo de emissão (POST e processamento):**
  - vazão sustentada;
  - p50, p95, p99 e máximo;
  - taxa de erro por código;
  - tempo até emissão (de `RECEBIDA` a `EMITIDA`);
  - tempo de escoamento (até todas as solicitações estarem `EMITIDA`);
  - pico de notificações pendentes;
  - duplicidades e lacunas de numeração, que precisam ser zero.
- **Relatório das consultas (GETs):** latência p50, p95 e p99 por nível de carga, medida durante a carga de emissão. Quando a réplica de leitura estiver ativa, o relatório inclui o lag de replicação e a taxa de fallback para o banco de escrita.
- **Consistência das consultas:** zero `404` falsos para solicitações aceitas, em todas as rodadas de carga.
- **Isolamento:** em execuções sequenciais e concorrentes, 100% das respostas contêm só os itens do próprio pedido.
- **Numeração:** zero números duplicados e zero lacunas em teste concorrente com várias instâncias.
- **Centavos:** em 100% das notas, a soma das linhas é igual aos totais de tributo e de itens, e os valores dos eventos são idênticos aos da nota.
- **Erros:** zero respostas `500` para entradas inválidas. 100% das rejeições têm mensagem explicativa por campo.
- **Qualidade:** cobertura de testes de 100% de linhas e de branches em cada módulo (`app` e `domain`), medida separadamente por módulo, com o CI bloqueando merges abaixo disso. Fica excluído somente o ponto de entrada da aplicação.

## Restrições técnicas de alto nível
- Java 21, Kotlin 2.x e a versão mais recente do Spring Boot, usando recursos novos quando fizer sentido (por exemplo, virtual threads).
- Banco relacional PostgreSQL. As notas e as solicitações são persistidas (dado fiscal, sem expiração).
- Mensageria com Kafka para a notificação dos sistemas consumidores.
- As leituras (GETs) podem ser servidas por uma réplica de leitura do banco. Escrita, numeração e idempotência usam sempre o banco principal.
- A aplicação não guarda estado entre requisições e pode rodar com várias instâncias.
- Contexto AWS no desenho de arquitetura: API Gateway, autenticação OAuth2 client credentials com JWT e scopes (Cognito), rate limiting por cliente, aplicação acessível só pelo gateway, banco gerenciado, Kafka gerenciado e orquestração de containers.
- A autenticação acontece no gateway. A aplicação confia na identidade do cliente propagada pelo gateway e a usa para isolar os dados por cliente.
- Dados sensíveis (CPF/CNPJ) são mascarados nos logs.
- Observabilidade: rastreamento de ponta a ponta (da requisição ao consumo pelos 4 sistemas) correlacionado pelo id da solicitação, métricas de negócio (notas emitidas, falhas, fila pendente de notificação, atraso dos consumidores, tempo até a emissão) e logs estruturados.

## Experiência do desenvolvedor e entrega
- Um único comando sobe o ambiente local completo (banco, Kafka e aplicação).
- Documentação da API em OpenAPI, README, ADRs das decisões, exemplos de requisição e o relatório do teste de carga.
- CI com build, testes (unitários, integração e carga), cobertura mínima e análise estática.
- Desenho de arquitetura do entorno: gateway, autenticação, APIs com que a aplicação se comunica e contexto AWS.
- Plano de deploy documentado: ambientes, estratégia de implantação, migrations compatíveis com a versão anterior, rollback e checklist.

## Fora do escopo
- Autorização na SEFAZ, geração do XML ou DANFE, cancelamento, carta de correção e inutilização de numeração.
- Integração real com estoque, registro, entrega e financeiro (são simulados).
- Deploy real na AWS e infraestrutura como código (só desenho e plano de deploy).
- Inferir o modo de tributação pelo conteúdo do payload.
- Autenticação implementada na aplicação (ela fica no gateway).
- Captura de mudanças do banco (CDC) para publicação de eventos: aparece só no desenho, como evolução.
- Interface gráfica.

## Pontos em aberto
- Payload oficial ou projeto base: aguardando resposta da recrutadora. Se chegar, só o contrato de entrada e seu mapeamento mudam.
- Confirmação da fórmula do modo `POR_DENTRO`.
