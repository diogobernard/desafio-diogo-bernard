# Documento de Requisitos do Produto (PRD) — API de Emissão de Nota Fiscal

## Visão geral

A API de Emissão de Nota Fiscal recebe um pedido, calcula os tributos e o frete, gera a nota fiscal com numeração única e notifica os sistemas de estoque, registro, entrega e financeiro. Ela substitui uma aplicação legada, descrita no enunciado do desafio técnico "Desafio Nota Fiscal - Kotlin", que acumula itens de execuções anteriores na resposta, fica lenta com pedidos de mais de 6 itens e após várias execuções, processa requisições simultâneas uma de cada vez, entrega valores inconsistentes ao centavo, gera notas com numeração duplicada e responde `500` sem explicação, ou gera notas sem itens ou sem frete, quando recebe dados incompletos.

O produto se destina aos sistemas que solicitam notas (machine-to-machine), aos sistemas que as consomem e às pessoas que mantêm, operam e avaliam a aplicação. Ele entrega notas exatas ao centavo, com numeração sequencial sem duplicidade nem lacunas, erros de entrada explicados por campo e um código testado, fácil de manter e simples de executar localmente. O desempenho é caracterizado por medições no ambiente documentado.

**Premissa principal:** o projeto é implementado do zero, porque o repositório base não foi recebido. O contrato de entrada é definido a partir dos termos do enunciado e deve poder ser substituído pelo payload oficial sem alterar as regras de cálculo.

## Objetivos

- **Medição de latência:** registrar p50, p95, p99 e máxima do POST e do tempo entre `RECEBIDA` e `EMITIDA` para pedidos com até 50 itens; os valores observados são apresentados no relatório, sem limites de aceitação predefinidos.
- **Carga e vazão:** executar rodadas de 1.000, 5.000 e 10.000 solicitações a uma taxa de chegada configurada de 1.000 req/s, em um ambiente documentado (CPU, memória e recursos dos containers). Registrar vazão e uso de recursos em cada rodada para caracterizar o comportamento observado.
- **Relatório do fluxo de emissão (POST e processamento):** vazão sustentada; latência p50, p95, p99 e máxima; taxa de erro por código; tempo de `RECEBIDA` a `EMITIDA`; tempo de escoamento até todas as solicitações estarem `EMITIDA`; pico de notificações pendentes; duplicidades e lacunas de numeração.
- **Relatório das consultas (GETs):** latência p50, p95 e p99 por nível de carga, medida durante a carga de emissão. Com a réplica de leitura ativa, o relatório inclui o lag de replicação e a taxa de fallback para o banco de escrita.
- **Consistência das consultas:** zero `404` falsos para solicitações aceitas, em todas as rodadas de carga.
- **Isolamento:** em execuções sequenciais e concorrentes, 100% das respostas contêm somente os itens do próprio pedido.
- **Numeração:** zero números duplicados e zero lacunas em teste concorrente com várias instâncias.
- **Centavos:** em 100% das notas, a soma das linhas é igual aos totais de tributo e de itens, e os valores dos eventos são idênticos aos da nota.
- **Erros:** zero respostas `500` para entradas inválidas; 100% das rejeições têm mensagem explicativa por campo.
- **Qualidade:** cobertura de testes de 100% de linhas e de branches em cada módulo (`app` e `domain`), medida separadamente por módulo, com o CI bloqueando merges abaixo desse valor. Fica excluído somente o ponto de entrada da aplicação.

## Histórias de usuário

**Sistemas solicitantes (principal)**

- US1: Como sistema solicitante, quero enviar um pedido e receber imediatamente um identificador de solicitação para não ficar bloqueado durante o cálculo e a emissão.
- US2: Como sistema solicitante, quero reenviar o mesmo pedido com a mesma `Idempotency-Key` após uma falha de rede para ter a garantia de que nenhuma nota duplicada será criada.
- US3: Como sistema solicitante, quero consultar o status da solicitação e saber quando consultar de novo para acompanhar a emissão sem sobrecarregar a API.
- US4: Como sistema solicitante, quero obter a nota completa por um link estável para usar os valores exatos de itens, tributos, frete e totais.
- US5: Como sistema solicitante, quero receber uma explicação por campo quando o pedido for inválido para corrigir os dados sem precisar de suporte.
- US6: Como sistema solicitante, quero enxergar somente minhas solicitações e notas para que os dados de outros clientes permaneçam protegidos.

**Sistemas consumidores (secundário)**

- US7: Como sistema de estoque, registro, entrega ou financeiro, quero receber uma notificação por nota emitida, com a lista completa de itens e valores idênticos aos da nota, para processar a nota sem divergências de centavos.
- US8: Como sistema consumidor, quero que minha lentidão ou falha não atrase os demais sistemas e que nenhuma notificação se perca para que cada integração evolua de forma independente.

**Desenvolvedores, operação e avaliadores**

- US9: Como desenvolvedor, quero subir o ambiente completo com um único comando e ter testes confiáveis para evoluir as regras de cálculo com segurança.
- US10: Como operador, quero rastrear uma solicitação de ponta a ponta e acompanhar métricas de negócio para diagnosticar falhas e atrasos rapidamente.
- US11: Como avaliador do desafio, quero executar a aplicação localmente e consultar relatórios de carga e documentação para verificar os critérios sem ambiguidade.

**Casos de borda**

- US12: Como sistema solicitante, quando envio duas requisições simultâneas com a mesma chave, quero que apenas uma solicitação seja criada.
- US13: Como sistema solicitante, quando consulto uma solicitação logo após recebê-la, quero ver `RECEBIDA` ou `PROCESSANDO`, nunca `404`.
- US14: Como sistema solicitante PJ com regime `OUTROS`, quero receber `422` com o motivo, em vez de uma nota incorreta.

## Principais funcionalidades

### 1. Emissão assíncrona de nota fiscal

Recebe o pedido, valida-o de forma síncrona e registra a solicitação para processamento assíncrono. Isso desacopla o tempo de resposta do POST do tempo de cálculo, numeração e notificação, eliminando o processamento serial do legado.

- **RF1:** A API deve expor um endpoint `POST` de emissão que recebe o pedido e o header `Idempotency-Key`.
- **RF2:** A validação do pedido deve ser síncrona. Um pedido inválido deve ser rejeitado com `400` ou `422` e nada deve ser persistido.
- **RF3:** Um pedido válido deve ser registrado e respondido com `202`, contendo o identificador técnico da solicitação e os headers `Location` (onde consultar) e `Retry-After` (quando consultar).
- **RF4:** O processamento assíncrono deve calcular tributos e frete, atribuir a numeração e gerar a nota.
- **RF5:** A solicitação deve seguir os estados `RECEBIDA → PROCESSANDO → EMITIDA | FALHA`. Não existe estado de rejeição. `FALHA` deve trazer um motivo legível, sem detalhes internos.
- **RF6:** Cada execução deve conter somente os itens do próprio pedido, sem acúmulo de dados de execuções anteriores.

### 2. Idempotência da emissão

Garante que repetições do mesmo pedido não gerem notas duplicadas.

- **RF7:** O header `Idempotency-Key` é obrigatório; sua ausência resulta em `400`.
- **RF8:** A mesma chave com o mesmo payload deve devolver o mesmo `202` com o mesmo id, sem criar nova solicitação.
- **RF9:** A mesma chave com payload diferente deve resultar em `422`.
- **RF10:** Requisições simultâneas com a mesma chave devem criar uma única solicitação.
- **RF11:** A chave nunca expira e nunca pode ser reutilizada para outro pedido. O cliente gera um UUID por pedido.

### 3. Consulta de solicitação e de nota

Permite acompanhar a emissão e obter a nota completa, com isolamento por cliente.

- **RF12:** `GET /issuance-requests/{id}` deve responder:
  - `RECEBIDA` ou `PROCESSANDO` → `200` com status, datas e `Retry-After`;
  - `EMITIDA` → `200` com status, número, série, data de emissão e link para `GET /invoices/{number}`, sem a nota completa;
  - `FALHA` → `200` com o motivo;
  - id inexistente → `404`.
- **RF13:** `GET /invoices/{number}` deve ser a única representação da nota completa: número, série, itens com tributo, frete, totais e data de emissão. Número inexistente → `404`. A resposta pode ser cacheada pelo cliente, porque a nota não muda depois de emitida.
- **RF14:** Seguir o link de uma solicitação `EMITIDA` nunca pode resultar em `404`.
- **RF15:** Um cliente só enxerga as próprias solicitações e notas; as de outro cliente retornam `404`.
- **RF16:** As consultas aceitam consistência eventual (por exemplo, `PROCESSANDO` por alguns instantes após a emissão), mas nunca podem devolver um `404` falso para uma solicitação aceita.
- **RF17:** A consulta de um id inexistente ou antigo não pode gerar carga no banco de escrita, para impedir abuso por varredura de ids.

### 4. Validação e erros padronizados

Garante que entradas inválidas sejam explicadas e nunca resultem em `500` ou em notas incompletas. Regra geral: dado ausente ou malformado → `400`; dado presente que impede a emissão → `422`.

- **RF18:** As respostas de erro devem seguir o formato Problem Details (RFC 9457), com mensagem por campo.
- **RF19:** Devem resultar em `400`: JSON malformado; campos obrigatórios ausentes; pedido sem itens; pedido sem endereço de entrega; PJ sem regime de tributação; `Idempotency-Key` ausente; quantidade não inteira ou ≤ 0; valores monetários negativos (premissa).
- **RF20:** Devem resultar em `422`: PJ com regime `OUTROS`; pedido com mais de 500 itens; `Idempotency-Key` reutilizada com payload diferente.
- **RF21:** Um pedido inválido nunca pode resultar em `500` nem em uma nota sem itens ou sem frete.

### 5. Contrato de entrada

Define os dados do pedido a partir dos termos do enunciado, substituível pelo payload oficial sem alterar as regras de cálculo.

- **RF22:** O pedido deve conter o tipo de pessoa (física ou jurídica) e, para pessoa jurídica, o regime de tributação (Simples Nacional, Lucro Real, Lucro Presumido ou Outros).
- **RF23:** Cada item deve conter `unit_price` e `quantity` (sempre inteira).
- **RF24:** O pedido deve conter `freight_value`.
- **RF25:** O pedido deve conter endereços com finalidade e região (NORTE, NORDESTE, CENTRO_OESTE, SUDESTE, SUL). Premissa: existe um único endereço de entrega, com finalidade `ENTREGA` ou `COBRANCA_ENTREGA`; sua região define o frete, e os demais endereços não entram no cálculo.
- **RF26:** O contrato de entrada não pode ser alterado em relação ao definido.

### 6. Cálculo de tributos e frete

Aplica as regras do enunciado com valores exatos ao centavo, eliminando divergências entre a nota e os eventos.

- **RF27:** Todos os valores monetários são exatos, com 2 casas decimais e arredondamento HALF_EVEN.
- **RF28:** `unit_price` com mais de 2 casas deve ser aceito e normalizado (HALF_EVEN, 2 casas) na entrada; a nota e os eventos carregam o unitário normalizado.
- **RF29:** `total_item = valor_unitario_normalizado × quantidade` e `total_itens = Σ total_item`. O `items_total` define a faixa da alíquota.
- **RF30:** `tributo_item = round(total_item × alíquota, 2, HALF_EVEN)`, arredondado ao fim da conta de cada item; `tributo_nota = Σ tributo_item`, normalizado em escala 2. A soma das linhas sempre bate com o total.
- **RF31:** `frete = valor_frete + round(valor_frete × percentual_região, 2, HALF_EVEN)`, com acréscimo por região: NORTE 8%, NORDESTE 8,5%, CENTRO_OESTE 7%, SUDESTE 4,8%, SUL 6%.
- **RF32:** Alíquota para pessoa física, pelo total dos itens:

  | Faixa | Alíquota |
  |---|---|
  | até 499,99 | 0% |
  | 500,00 a 2.000,00 | 12% |
  | 2.000,01 a 3.500,00 | 15% |
  | acima de 3.500,00 | 17% |

- **RF33:** Alíquota para pessoa jurídica, pelo total dos itens:

  | Faixa | Simples Nacional | Lucro Real | Lucro Presumido |
  |---|---|---|---|
  | até 999,99 | 3% | 3% | 3% |
  | 1.000,00 a 2.000,00 | 7% | 9% | 9% |
  | 2.000,01 a 5.000,00 | 13% | 15% | 16% |
  | acima de 5.000,00 | 19% | 20% | 20% |

- **RF34:** PJ sem regime ou com regime `OUTROS` e pedidos sem endereço de entrega não podem ter a nota gerada.

### 7. Modo de tributação configurável

Suporta tributo acrescentado ou embutido no valor dos itens, sempre de forma explícita.

- **RF35:** No modo `POR_FORA` (padrão), o tributo é acrescentado ao valor dos itens, e o total da nota é `total_itens + tributo_nota + frete`.
- **RF36:** No modo `POR_DENTRO`, o valor recebido já contém o tributo. A faixa é definida pelo total bruto dos itens; o tributo é extraído por item com `round(bruto_item × alíquota / (1 + alíquota), 2, HALF_EVEN)`; o líquido é `bruto_item − tributo_item`; e o total da nota é `total_itens_bruto + frete`. Esta fórmula é uma premissa a confirmar.
- **RF37:** O modo nunca pode ser inferido do payload (por exemplo, por um campo nulo). Ele é definido por configuração ou por um campo explícito, caso o payload oficial tenha um.
- **RF38:** Os dois modos devem ser implementados.

### 8. Numeração fiscal

Elimina a numeração duplicada do legado.

- **RF39:** Cada nota deve ter um número sequencial, único e sem lacunas por série, compatível com o campo nNF da NF-e (de 1 a 999.999.999).
- **RF40:** Não pode haver duplicidade nem lacuna com processamento concorrente e várias instâncias.
- **RF41:** O identificador técnico da solicitação é distinto do número fiscal.

### 9. Notificação dos sistemas consumidores

Entrega cada nota emitida aos quatro sistemas de forma confiável e independente.

- **RF42:** Toda nota emitida deve ser notificada a estoque, registro, entrega e financeiro ao menos uma vez, mesmo com falhas momentâneas.
- **RF43:** Deve haver uma notificação por nota, com a lista completa de itens, e não uma por item.
- **RF44:** Os valores notificados devem ser idênticos aos da nota.
- **RF45:** Os quatro sistemas são independentes: a lentidão ou falha de um não atrasa os outros.
- **RF46:** Os consumidores são simulados com latência artificial, que não pode ser removida (exigência do enunciado) e não pode bloquear o processamento de outras notas.
- **RF47:** Uma falha persistente em um consumidor não pode ser perdida; ela deve ficar isolada para reprocessamento.

### 10. Operação, observabilidade e entrega

- **RF48:** O rastreamento deve ser de ponta a ponta, da requisição ao consumo pelos quatro sistemas, correlacionado pelo id da solicitação.
- **RF49:** Devem existir métricas de negócio: notas emitidas, falhas, fila pendente de notificação, atraso dos consumidores e tempo até a emissão.
- **RF50:** Os logs devem ser estruturados, com CPF e CNPJ mascarados.
- **RF51:** Um único comando deve subir o ambiente local completo (banco, mensageria e aplicação).
- **RF52:** Devem ser entregues: documentação navegável do contrato HTTP gerada por testes com Spring REST Docs, README, ADRs das decisões, exemplos de requisição, relatórios de carga, desenho de arquitetura do entorno (gateway, autenticação, APIs com que a aplicação se comunica e contexto AWS) e plano de deploy (ambientes, estratégia de implantação, migrations compatíveis com a versão anterior, rollback e checklist).
- **RF53:** O CI deve executar build, testes unitários, de integração e de carga, verificação de cobertura mínima e análise estática.
- **RF54:** API HTTP e workers devem poder ser iniciados em processos independentes, com pools de conexões próprios e limites configuráveis, respeitando o orçamento agregado de conexões do banco.

## Critérios de aceitação

**Emissão e idempotência**

- CA-01 (US1, RF1–RF3): Dado um pedido válido com `Idempotency-Key` nova, quando o POST é enviado, então a resposta é `202` com o id da solicitação, `Location` e `Retry-After`.
- CA-02 (US5, RF2, RF19): Dado um pedido sem itens, sem endereço de entrega, com JSON malformado, com campo obrigatório ausente, com quantidade não inteira ou ≤ 0, com valor monetário negativo ou de PJ sem regime, quando o POST é enviado, então a resposta é `400` em Problem Details com mensagem por campo, e nenhuma solicitação é persistida.
- CA-03 (US14, RF20): Dado um pedido de PJ com regime `OUTROS` ou com mais de 500 itens, quando o POST é enviado, então a resposta é `422` com o motivo, e nenhuma solicitação é persistida.
- CA-04 (RF7): Dado um POST sem `Idempotency-Key`, quando é enviado, então a resposta é `400`.
- CA-05 (US2, RF8): Dado um pedido já aceito, quando o mesmo payload é reenviado com a mesma chave, então a resposta é o mesmo `202` com o mesmo id, e nenhuma nova solicitação é criada.
- CA-06 (RF9): Dada uma chave já usada, quando um payload diferente é enviado com ela, então a resposta é `422`.
- CA-07 (US12, RF10): Dadas requisições simultâneas com a mesma chave e o mesmo payload, quando são processadas, então existe uma única solicitação e todas as respostas trazem o mesmo id.
- CA-08 (RF21): Dado qualquer conjunto de entradas inválidas da suíte de testes, quando enviado, então nenhuma resposta é `500` e nenhuma nota é gerada sem itens ou sem frete.

**Consulta**

- CA-09 (US3, RF12): Dada uma solicitação `RECEBIDA` ou `PROCESSANDO`, quando consultada, então a resposta é `200` com status, datas e `Retry-After`.
- CA-10 (US4, RF12, RF14): Dada uma solicitação `EMITIDA`, quando consultada, então a resposta traz status, número, série, data de emissão e um link que, seguido, retorna `200` com a nota completa.
- CA-11 (RF12): Dada uma solicitação em `FALHA`, quando consultada, então a resposta é `200` com um motivo legível, sem detalhes internos.
- CA-12 (US13, RF16): Dada uma solicitação que acabou de receber `202`, quando consultada imediatamente, em qualquer rodada de carga, então a resposta nunca é `404`.
- CA-13 (US6, RF15): Dadas solicitações e notas de outro cliente, quando consultadas, então a resposta é `404`.
- CA-14 (RF12, RF13): Dado um id de solicitação ou um número de nota inexistente, quando consultado, então a resposta é `404`.
- CA-15 (RF17): Dada uma varredura de ids inexistentes, quando executada, então ela não gera consultas no banco de escrita.

**Cálculo**

- CA-16 (RF29–RF33): Dados pedidos de PF e de PJ (cada regime) com totais nos limites das faixas (499,99; 500,00; 2.000,00; 2.000,01; 3.500,00; 3.500,01; 999,99; 1.000,00; 5.000,00; 5.000,01), quando a nota é emitida, então a alíquota aplicada corresponde à faixa da tabela.
- CA-17 (RF31): Dado um pedido para cada região, quando a nota é emitida, então o frete é `freight_value` acrescido do percentual da região, arredondado com HALF_EVEN em 2 casas.
- CA-18 (RF27, RF30, RF44): Dada qualquer nota emitida, quando seus valores são conferidos, então a soma dos tributos por item é igual ao tributo da nota, a soma dos totais por item é igual ao total dos itens, e os valores dos eventos são idênticos aos da nota.
- CA-19 (RF28): Dado um `unit_price` com mais de 2 casas, quando a nota é emitida, então a nota e os eventos trazem o unitário normalizado com HALF_EVEN em 2 casas.
- CA-20 (RF35–RF37): Dado o mesmo pedido, quando emitido com o modo configurado `POR_FORA` e depois `POR_DENTRO`, então os totais seguem as fórmulas de cada modo, e o modo não muda com o conteúdo do payload.
- CA-21 (US1, RF6): Dadas execuções sequenciais e concorrentes de pedidos distintos, quando as notas são consultadas, então 100% delas contêm somente os itens do próprio pedido.

**Numeração**

- CA-22 (RF39–RF41): Dado um teste concorrente com várias instâncias, quando todas as solicitações são emitidas, então os números da série são únicos, sequenciais, sem lacunas, entre 1 e 999.999.999 e distintos dos ids das solicitações.

**Notificação**

- CA-23 (US7, RF42–RF44): Dada uma nota emitida, quando o processamento termina, então cada um dos quatro sistemas recebe ao menos uma notificação dessa nota, com a lista completa de itens.
- CA-24 (US8, RF45, RF46): Dado um consumidor lento ou indisponível, quando novas notas são emitidas, então os demais consumidores continuam sendo notificados sem atraso e a emissão de outras notas não é bloqueada.
- CA-25 (RF47): Dada uma falha persistente em um consumidor, quando as tentativas se esgotam, então a notificação fica isolada e disponível para reprocessamento, sem perda.

**Desempenho e carga**

- CA-26 (Objetivos): Dadas rodadas de 1.000, 5.000 e 10.000 solicitações em um ambiente documentado, quando executadas, então o relatório registra a vazão, as distribuições de latência do POST e do GET, os erros por código HTTP, o tempo entre `RECEBIDA` e `EMITIDA`, o escoamento, o uso de recursos e a integridade da numeração. Os percentis são resultados observados, sem limites de latência predefinidos; a numeração apresenta zero duplicidades e zero lacunas.
- CA-27 (Objetivos): Dada a carga de emissão em execução, quando os GETs são medidos, então o relatório traz p50, p95 e p99 por nível de carga e, com a réplica ativa, o lag de replicação e a taxa de fallback.

**Operação e entrega**

- CA-28 (US10, RF48–RF50): Dada uma solicitação emitida, quando o rastreamento é consultado pelo id da solicitação, então ele mostra o fluxo da requisição até o consumo pelos quatro sistemas, e os logs não contêm CPF ou CNPJ sem máscara.
- CA-29 (US9, US11, RF51–RF53): Dado o repositório recém-clonado, quando o comando único de ambiente é executado, então banco, mensageria e aplicação ficam disponíveis; e o CI bloqueia merges com cobertura de linhas ou de branches abaixo de 100% em qualquer módulo.
- CA-30 (RF49, RF51, RF54): Dado o ambiente de referência com API e workers em processos separados, quando uma rodada de 10.000 solicitações for executada, então cada processo usa pools próprios e limitados, a soma dos limites não excede 30 conexões de escrita e 35 de leitura, e o relatório registra resultados e limitações observados. A rodada registra latências e vazão já exigidas em CA-26, uso e espera dos pools por processo, sessões `idle in transaction`, retenção de conexões, backlog do outbox/Kafka, recursos, timeouts e integridade da numeração; não há meta de latência predefinida nem obrigação de comparação com uma rodada anterior.

## Experiência do usuário

O produto não possui interface gráfica: a experiência é a da API para integradores e a do ambiente para desenvolvedores e avaliadores.

**Perfis e necessidades**

- **Sistemas solicitantes:** respostas rápidas, idempotência segura para retries, orientação clara de polling e erros explicativos por campo.
- **Sistemas consumidores:** uma notificação por nota, completa e com valores idênticos aos da nota, entregue de forma independente dos demais.
- **Desenvolvedores e operação:** ambiente local com um comando, testes confiáveis, rastreamento de ponta a ponta e métricas de negócio.
- **Avaliadores:** execução local simples, documentação navegável e relatórios de carga comparáveis.

**Fluxo principal**

1. O solicitante gera um UUID e envia o POST com o pedido e o header `Idempotency-Key`.
2. Se o pedido for inválido, recebe `400` ou `422` com explicação por campo e pode corrigi-lo.
3. Se for válido, recebe `202` com o id, `Location` e `Retry-After`.
4. O solicitante consulta `Location` respeitando `Retry-After` até o status ser `EMITIDA` ou `FALHA`.
5. Em `EMITIDA`, segue o link para obter a nota completa, que pode guardar em cache.
6. Os quatro consumidores recebem a notificação da nota, cada um em seu ritmo.

**Diretrizes de experiência da API**

- Nomes de recursos, campos e mensagens consistentes, no vocabulário do domínio fiscal.
- Erros em Problem Details (RFC 9457), com mensagem por campo, legíveis por pessoas e processáveis por máquinas, sem detalhes internos.
- Headers padrão (`Location`, `Retry-After`) para orientar o cliente, evitando polling agressivo.
- Contrato HTTP documentado com Spring REST Docs, com exemplos gerados por testes para cenários de sucesso e erro.
- Acessibilidade: não se aplica a interfaces gráficas; a documentação (README, REST Docs e relatórios) deve ser textual, estruturada e navegável.

## Restrições técnicas de alto nível

- **Tecnologia:** Java 21, Kotlin 2.x e a versão mais recente do Spring Boot, usando recursos novos quando fizer sentido (por exemplo, virtual threads).
- **Persistência:** banco relacional PostgreSQL. Solicitações e notas são persistidas sem expiração, por serem dado fiscal. As leituras podem ser servidas por uma réplica de leitura; escrita, numeração e idempotência usam sempre o banco principal.
- **Mensageria:** Kafka para a notificação dos sistemas consumidores.
- **Escala:** a aplicação não guarda estado entre requisições e pode rodar com várias instâncias; o comportamento é medido com carga configurada de até 1.000 req/s no ambiente documentado, sem limites de latência predefinidos.
- **Contexto AWS (desenho):** API Gateway, autenticação OAuth2 client credentials com JWT e scopes (Cognito), rate limiting por cliente, aplicação acessível somente pelo gateway, banco gerenciado, Kafka gerenciado e orquestração de containers.
- **Segurança:** a autenticação ocorre no gateway; a aplicação confia na identidade do cliente propagada por ele e a usa para isolar os dados por cliente.
- **Privacidade:** CPF e CNPJ são mascarados nos logs.
- **Conformidade fiscal:** numeração compatível com o campo nNF da NF-e (até 9 dígitos).
- **Integrações:** os quatro consumidores são simulados com latência artificial, que não pode ser removida.
- **Observabilidade:** rastreamento de ponta a ponta correlacionado pelo id da solicitação, métricas de negócio e logs estruturados.

## Fora do escopo

- Autorização na SEFAZ, geração de XML ou DANFE, cancelamento, carta de correção e inutilização de numeração.
- Integração real com estoque, registro, entrega e financeiro (são simulados).
- Deploy real na AWS e infraestrutura como código (somente desenho e plano de deploy).
- Inferência do modo de tributação pelo conteúdo do payload.
- Autenticação implementada na aplicação (fica no gateway).
- Captura de mudanças do banco (CDC) para publicação de eventos: aparece somente no desenho, como evolução.
- Interface gráfica.

**Pontos em aberto**

- Payload oficial ou projeto base: aguardando resposta da recrutadora. Se chegar, só o contrato de entrada e seu mapeamento mudam.
- Confirmação da fórmula do modo `POR_DENTRO`.
