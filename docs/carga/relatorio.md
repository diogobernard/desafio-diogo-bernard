# Relatório de carga — emissão de notas fiscais

## Rodada iniciada em 18/11/2026 às 01:38 UTC

POST, consumo e combinado rodaram entre 01:38 e 01:58 UTC (22:38 a 22:58 no horário local,
UTC−3) e passaram. A **consulta mudou durante a rodada**: depois de duas tentativas do cenário
anterior (a primeira reprovou na preparação da base e a segunda foi medida), ela passou a ser um
teste só de GET, sem Kafka, worker nem `POST`, com as notas semeadas por SQL. O cenário novo teve
cinco execuções (A a D e F, de 02:35 a 03:31 UTC) e um experimento que derrubou a API; todas
estão na seção da consulta. A execução F é a final: só o PostgreSQL e a API, sem réplica. A execução usou projetos Compose isolados, com 8 CPUs disponíveis no Docker.
A API recebeu 4 CPUs em todos os cenários e o worker recebeu 4 CPUs no consumo e no combinado.
Foram mantidos o perfil de carga e os aquecimentos padrão, exceto na consulta, que ganhou a rampa
descrita abaixo. O IntelliJ estava aberto, e o pico da carga do host passou de 8 em todas as
rajadas medidas (de 8,63 a 30,54), então os percentis são descritivos. A rodada das 22:02 UTC,
logo abaixo, continua registrada: as duas não se substituem.

### Resultados do POST isolado

As seis rajadas ofereceram **1.000 POST/s** com o worker parado. A coluna de vazão
usa o `Cnt/s` do Gatling na janela de medição. Todos os 32.000 `POST` retornaram `202`,
com máximo de 2.567 ms (5k da rodada 1), abaixo da meta de 3 s. A verificação SQL
confirmou um evento de outbox por solicitação, sem chaves duplicadas e sem eventos
ausentes ou repetidos.
[Coletas da rodada 1](../../tasks/prd-emissao-nota-fiscal/evidences/runtime-separados-tarefa16/post-oficial-1-20261118T014300Z/)
e [coletas da rodada 2](../../tasks/prd-emissao-nota-fiscal/evidences/runtime-separados-tarefa16/post-oficial-2-20261118T014345Z/).

| Rodada | POSTs aceitos | Vazão (POST/s) | p50/p95/p99/máx (ms) | Carga do host (início/pico) | Gatling |
|---:|---:|---:|---:|---:|---|
| 1 | 1.000/1.000 | 500 | 610/835/913/953 | 7,45/11,18 | [1k](../../app/build/reports/gatling/postingressloadsimulation-20261118014220778/index.html) |
| 1 | 5.000/5.000 | 833,33 | 1.279/2.241/2.435/2.567 | 10,45/11,93 | [5k](../../app/build/reports/gatling/postingressloadsimulation-20261118014230105/index.html) |
| 1 | 10.000/10.000 | 909,09 | 82/481/641/799 | 10,92/23,79 | [10k](../../app/build/reports/gatling/postingressloadsimulation-20261118014245293/index.html) |
| 2 | 1.000/1.000 | 500 | 74/137/166/175 | 23,79/23,79 | [1k](../../app/build/reports/gatling/postingressloadsimulation-20261118014304231/index.html) |
| 2 | 5.000/5.000 | 833,33 | 51/180/229/308 | 21,21/22,38 | [5k](../../app/build/reports/gatling/postingressloadsimulation-20261118014313869/index.html) |
| 2 | 10.000/10.000 | 1.000 | 12/168/318/421 | 20,67/21,67 | [10k](../../app/build/reports/gatling/postingressloadsimulation-20261118014328114/index.html) |

A rodada 1 foi a mais lenta nos volumes de 1k e 5k (p50 de 610 e 1.279 ms, contra 74 e
51 ms na rodada 2), embora a carga do host no início dela fosse menor (7,45 e 10,45,
contra 23,79 e 21,21). O 5k da rodada 1 deixou 433 ms de folga até a meta de 3 s. A
causa dessa diferença não foi investigada. Os percentis medem o tempo até o aceite HTTP.

### Resultados do consumo isolado

Os tempos de emissão e notificação começam quando o worker é retomado. O
escoamento é o tempo da verificação de outbox e lag Kafka após a última
notificação. O banco confirmou todas as notas e os quatro pares de notificação
por nota, sem duplicidade ou lacuna de numeração. As quatro DLQs ficaram vazias.

| Notas emitidas | Notificações | Até emitir | Até notificar | Vazão de emissão | Escoamento | Carga do host (pico) | Evidências |
|---:|---:|---:|---:|---:|---:|---:|---|
| 1.000 | 4.000 | 5 s | 8 s | 200 notas/s | 1 s | 10,74 | [1k](../../tasks/prd-emissao-nota-fiscal/evidences/runtime-separados-tarefa16/consumo-1000-20261118T014749Z/) |
| 5.000 | 20.000 | 8 s | 27 s | 625 notas/s | 1 s | 14,29 | [5k](../../tasks/prd-emissao-nota-fiscal/evidences/runtime-separados-tarefa16/consumo-5000-20261118T014904Z/) |
| 10.000 | 40.000 | 18 s | 57 s | 555,56 notas/s | 6 s | 16,00 | [10k](../../tasks/prd-emissao-nota-fiscal/evidences/runtime-separados-tarefa16/consumo-10000-20261118T015051Z/) |

Contra a rodada das 22:02 UTC, o tempo até emitir foi de 5, 8 e 18 s (antes, 4, 11 e
20 s) e o tempo até notificar foi de 8, 27 e 57 s (antes, 6, 16 e 60 s). O resultado
ficou melhor em parte dos volumes e pior em outros, e a carga do host variou entre as
rodadas (pico de 10,74 a 16,00 nesta e de 7,58 a 9,57 na anterior).

### Resultados da consulta isolada

A consulta teve duas formas nesta rodada. O cenário anterior montava a base por `POST`,
worker e Kafka (tentativas 1 e 2, no fim desta seção). Como a consulta deve medir só o
GET, ela passou a ser um teste sem Kafka, worker nem `POST`. A forma final (execução F) segue
quatro passos: sobem apenas o PostgreSQL e a API (4 CPUs); o banco é alimentado uma vez com
5.000 solicitações emitidas e as 5.000 notas fiscais (`scripts/seed-consulta.sql`); a JVM é
aquecida em rampa; e as consultas rodam com 1k, 5k e 10k usuários sobre a mesma base, que o
feeder reaproveita em ciclo. Entre os volumes o script espera o `TIME_WAIT` do cliente baixar.

#### Cenário novo, execução final (F, 03:25–03:31 UTC): só PostgreSQL e API, base única de 5k

A carga do host ficou entre 5,00 e 7,00. Todos os GETs retornaram `200`, o contador de `404`
falso ficou em zero e `ListenOverflows` e `ListenDrops` da API, também. Sem réplica, a API lê
do próprio banco, e os contadores de fallback não se aplicam (ficaram em zero). A vazão é o
total dos dois GETs.

| Usuários | GETs OK | GET solicitação p50/p95/p99/máx (ms) | GET nota fiscal p50/p95/p99/máx (ms) | Vazão (GET/s) | Carga do host (início/pico) | `TIME_WAIT` antes/depois | Evidências |
|---:|---:|---:|---:|---:|---:|---:|---|
| 1.000 | 2.000 | 209/342/451/483 | 196/314/406/576 | 1.000 | 5,00/7,00 | 5/1.010 | [1k](../../tasks/prd-emissao-nota-fiscal/evidences/runtime-separados-tarefa16/consulta-1000-20261118T032924Z/) |
| 5.000 | 10.000 | 58/274/335/494 | 31/232/312/516 | 2.000 | 5,37/5,37 | 2/5.012 | [5k](../../tasks/prd-emissao-nota-fiscal/evidences/runtime-separados-tarefa16/consulta-5000-20261118T033005Z/) |
| 10.000 | 20.000 | 3/72/116/181 | 2/43/83/179 | 2.000 | 5,69/6,92 | 68/10.017 | [10k](../../tasks/prd-emissao-nota-fiscal/evidences/runtime-separados-tarefa16/consulta-10000-20261118T033048Z/) |

A API usou em média 239%, 179% e 72% de CPU (picos de 471%, 530% e 163%) e o PostgreSQL, 6%,
25% e 21%. O pool de leitura chegou a 24 e 25 conexões ativas no 5k e no 10k, com no máximo
63 e 24 pedidos pendentes; a maior espera por conexão, acumulada do processo, foi de 0,4 s.
O p50 do GET de solicitação por segundo foi de 183 e 250 ms no 1k; 53, 178, 170, 23 e 2 ms no
5k; e 54, 26, 44, 9, 4 e depois 2 ms até o fim no 10k. Como nas execuções anteriores, a API
leva cerca de 3 s para chegar ao regime de 2 ms depois que a carga entra de uma vez; o 1k
termina antes disso e o 5k passa a maior parte do tempo nessa fase.

#### Cenário novo, execução D (03:10–03:12 UTC): com réplica e uma base por volume

O aquecimento sobe de 10 a 1.000 usuários/s em 30 s e segue 10 s em patamar sobre 3.000
notas reaproveitadas em círculo pelo feeder (cerca de 50 mil GETs). Cada rajada tem 1.000
usuários por segundo, com dois GETs por usuário. A vazão é o total dos dois GETs. Todos
retornaram `200`; os contadores de `404` falso e de fallback (`recente`, `negado` e
`ultimo_numero`) ficaram em zero, a réplica terminou em `streaming` sem bytes pendentes e
`ListenOverflows` e `ListenDrops` da API ficaram em zero nas três rajadas.

| Notas na base | GETs OK | GET solicitação p50/p95/p99/máx (ms) | GET nota fiscal p50/p95/p99/máx (ms) | Vazão (GET/s) | Carga do host (início/pico) | `TIME_WAIT` antes/depois | Evidências |
|---:|---:|---:|---:|---:|---:|---:|---|
| 1.000 | 2.000 | 357/687/846/994 | 272/482/633/844 | 1.000 | 9,23/11,86 | 10/1.018 | [1k](../../tasks/prd-emissao-nota-fiscal/evidences/runtime-separados-tarefa16/consulta-1000-20261118T031028Z/) |
| 5.000 | 10.000 | 73/1.034/1.235/1.481 | 39/266/681/1.469 | 1.428,57 | 10,41/12,54 | 962/5.014 | [5k](../../tasks/prd-emissao-nota-fiscal/evidences/runtime-separados-tarefa16/consulta-5000-20261118T031109Z/) |
| 10.000 | 20.000 | 3/66/106/232 | 1/46/80/177 | 1.818,18 | 11,64/14,31 | 728/10.019 | [10k](../../tasks/prd-emissao-nota-fiscal/evidences/runtime-separados-tarefa16/consulta-10000-20261118T031154Z/) |

A API usou em média 257%, 105% e 61% de CPU nas três rajadas (picos de 509%, 312% e 150%
para um limite de 400%) e a réplica, 19%, 32% e 23%. O pool de leitura (25 conexões) chegou
a 25 conexões ativas em todas, com no máximo 28, 427 e 81 pedidos pendentes; a maior espera
por conexão registrada foi de 0,8 s no 1k e de 1,1 s no 5k e no 10k (o valor do 10k repete o
do 5k, então é acumulado do processo). O `TIME_WAIT` do cliente ao fim de cada rajada foi
de cerca de uma porta por usuário. O host seguiu com carga acima de 8, e os percentis são
descritivos.

No 10k, o p50 do GET (3 ms) ficou abaixo do p50 do `POST` de 10k (12 ms na rodada 2 e 82 ms
na rodada 1 do `POST`), como se espera de uma leitura por chave. O 1k e o 5k medem sobretudo o
início da rajada, quando a carga entra de uma vez a 1.000 usuários/s: na execução A o 10k
mostrou p50 de 23 a 187 ms nos primeiros 5 s e de 2 a 9 ms, estável a 1.000 req/s, depois
disso, e o 1k dura 1 a 3 s.

p50/p95 do GET de solicitação por segundo de rajada na execução D, com as respostas concluídas
em cada segundo entre colchetes:

- **1k (dura cerca de 2 s):** 287/732 ms [213] e 393/651 ms [787]. A rajada termina antes de a API
  ganhar velocidade.
- **5k:** 96/161 [396], 182/340 [878], 31/114 [1.220], 7/119 [994], 37/1.129 [794] e 953/1.245 [74].
  Depois de 3 s a API responde em poucos ms, mas no quinto segundo a vazão caiu a 74 respostas
  (as 644 do sexto segundo são as retardatárias). As pausas de GC da janela somaram 198 ms, com
  máximo de 107 ms, então não explicam o tropeço, e a causa não foi identificada.
- **10k:** 38/85 [479], 21/113 [943], 28/86 [1.072] e, a partir do quarto segundo, 2 a 4 ms de
  p50 e 3 a 12 ms de p95, estável a 1.000 respostas por segundo até o fim.

Os percentis de cada volume cobrem a rajada inteira: no 10k, 7 dos 10 s são de regime estável e
puxam o p50 para 3 ms; no 1k, todas as requisições ocorrem na fase inicial. A causa dessa fase
inicial de alguns segundos não foi isolada. Candidatas: as 1.000 conexões novas por segundo, o
pool e as threads começando do zero e a API ociosa por 23 a 30 s (a espera do `TIME_WAIT`) antes
de cada rajada.

#### Como o cenário novo chegou a essa forma (execuções A a D e experimento E)

Cada execução corrigiu um problema da anterior, e todas estão registradas. Percentis em ms
(p50/p95) do GET de solicitação.

| Execução | O que mudou | 1k | 5k | 10k | KO | Evidências |
|---|---|---:|---:|---:|---:|---|
| A (02:35) | Primeira execução: aquecimento de 3.000 notas a 1.000 usuários/s (3 s) | 1.515/2.009 | 670/894 | 13/210 | 0 | [1k](../../tasks/prd-emissao-nota-fiscal/evidences/runtime-separados-tarefa16/consulta-1000-20261118T023528Z/), [5k](../../tasks/prd-emissao-nota-fiscal/evidences/runtime-separados-tarefa16/consulta-5000-20261118T023545Z/), [10k](../../tasks/prd-emissao-nota-fiscal/evidences/runtime-separados-tarefa16/consulta-10000-20261118T023608Z/) |
| E (02:39, experimento) | Aquecimento de 15.000 notas a 1.000 usuários/s (15 s), só no 1k | não medido | — | — | — | [estouro de memória](../../tasks/prd-emissao-nota-fiscal/evidences/runtime-separados-tarefa16/consulta-aquecimento-15000-20261118T024014Z-oom/) |
| B (02:56) | Aquecimento em rampa (30 s) mais patamar (10 s) | 214/356 | 27/320 | 8/302 | 73 no 10k | não arquivada (o script abortou); relatório Gatling local |
| C (03:02) | B, esperando o `TIME_WAIT` antes de cada rajada e amostrando-o a cada segundo durante ela | 435/692 | 31/310 | 457/1.984 | 0 | [1k](../../tasks/prd-emissao-nota-fiscal/evidences/runtime-separados-tarefa16/consulta-1000-20261118T030234Z/), [5k](../../tasks/prd-emissao-nota-fiscal/evidences/runtime-separados-tarefa16/consulta-5000-20261118T030314Z/), [10k](../../tasks/prd-emissao-nota-fiscal/evidences/runtime-separados-tarefa16/consulta-10000-20261118T030357Z/) |
| D (03:10) | C sem a amostragem durante a rajada | 357/687 | 73/1.034 | 3/66 | 0 | tabela acima |
| F (03:29) | Execução final: só PostgreSQL e API (sem réplica), banco alimentado uma vez com 5k notas, consultas sobre essa base | 209/342 | 58/274 | 3/72 | 0 | tabela no início da seção |

- **A.** O 1k ficou em 1.515 ms (p50) e o 10k, em 13 ms. A ordem inversa ao volume sugeriu aquecimento
  insuficiente: 3.000 notas são 3 s de carga, e o 10k leva cerca de 5 s para estabilizar.
- **E.** Aquecer 15.000 notas a 1.000 usuários/s com a JVM fria derrubou a API. O pool de leitura
  saturou e os pedidos pendentes foram de 435 para 11.169 em 20 s (espera de até 21 s por
  conexão); o heap (1.536 MB, 75% do limite de 2 GiB) encheu de requisições em espera, a
  primeira Full GC veio aos 36 s de vida e a JVM entrou em loop (2.558 Full GCs, cerca de 620 s
  coletando) até o `OutOfMemoryError`. O script ficou 7 min parado num `curl` sem timeout e foi
  encerrado à mão; os `curl` agora têm `--max-time`, e o script confere a readiness da API depois do
  aquecimento. A correção foi o aquecimento em rampa.
- **B.** Com a rampa, o 1k caiu de 1.515 para 214 ms (p50). O 10k teve 73 falhas (0,36%), todas de
  conexão: 69 `Connection reset by peer (connect failed)` e 4 `Premature close`. A hipótese é o
  esgotamento das portas efêmeras do cliente: no macOS elas vão de 49152 a 65535 (16.384) e o
  `TIME_WAIT` dura 30 s (`net.inet.tcp.msl` de 15 s), cada usuário do Gatling abre uma conexão
  nova e o 10k vinha 14 s depois do 5k. Não foi medida nessa execução (a evidência não foi
  arquivada); o que a sustenta é que a execução D mostrou cerca de uma porta em `TIME_WAIT` por
  usuário (1.018, 5.014 e 10.019 ao fim das rajadas), que o script precisou de 23 a 30 s para
  esvaziar o `TIME_WAIT` antes de cada rajada e que as falhas não se repetiram com essa espera.
  Os contadores de fila de conexões da API ficaram em zero, então não foi o servidor.
- **C.** Sem falhas, mas o 10k teve p50 de 457 ms: a vazão por rota caiu de cerca de 1.000 para
  169 req/s entre 4 s e 7 s de rajada, enquanto a carga do host ia de 9,7 a 27,2. A amostragem
  por segundo do `TIME_WAIT` que eu adicionei (`netstat` sobre 4 a 9 mil sockets) levava cerca de
  1 s por amostra e deixou um intervalo de 2 s entre duas amostras. Atribuí a queda à
  instrumentação e a removi.
- **D.** Sem a amostragem durante a rajada, o 10k voltou a 3 ms (p50). Isso sustenta a atribuição, mas
  não a prova: a carga do host também variou entre as execuções.

**Achado sobre a aplicação: a leitura não tem contrapressão.** O `POST` tem fila limitada e devolve
`503` quando ela enche, mas o GET não tem limite de espera pelo pool de leitura: com carga acima da
capacidade, como na execução E, os pedidos se acumulam em threads virtuais esperando conexão até
esgotar a memória, em vez de serem recusados rapidamente. Não alterei a aplicação; limitar a espera
pelo pool e devolver `503` com `Retry-After` é um possível próximo passo.

#### Cenário anterior (com `POST`, worker e Kafka), tentativa 1 (01:50 UTC): reprovada na preparação

O cenário `consulta` aquece o caminho de leitura criando antes uma base de 3.000
notas com 3.000 `POST` (cliente `consulta-aquecimento`), com o worker ativo para
emiti-las. Nessa etapa, **24 dos 3.000 `POST` (0,8%) falharam** com
`SocketException: Connection reset by peer (connect failed)`. A asserção de 100% de
sucesso reprovou (99,2%), o Gradle terminou com `BUILD FAILED` e o script saiu com
código 1 sem executar o aquecimento de GETs nem as rajadas de 1k, 5k e 10k.

| POSTs de preparação | OK | KO | p50/p95/p99/máx (ms) | Vazão (req/s) | Carga do host (início/fim do cenário) | Evidências |
|---:|---:|---:|---:|---:|---:|---|
| 3.000 | 2.976 | 24 | 3.614/4.184/4.266/4.391 | 375 | 8,52/11,67 | [falha na preparação](../../tasks/prd-emissao-nota-fiscal/evidences/runtime-separados-tarefa16/consulta-aquecimento-20261118T015349Z-preparacao-falhou/) |

A preparação do `consulta` roda com o worker emitindo e dividiu as 8 CPUs do Docker com
a API, o Gatling, o banco e o Kafka. No cenário `post`, com o worker parado, o p50 dos
`POST` nas seis rajadas ficou entre 12 e 1.279 ms, contra 3.614 ms aqui. O mesmo erro
(`Connection reset by peer`) já tinha reprovado a preparação do 10k na execução B de
17/11. A causa raiz não foi confirmada: não há logs da API desta falha, porque o Compose
isolado é removido ao final da execução. A hipótese, não verificada, é saturação da fila
de conexões da API durante a rajada de `POST` com o worker emitindo ao mesmo tempo.

#### Cenário anterior, tentativa 2 (02:09–02:16 UTC): medida

O Gatling iniciou 1.000 usuários por segundo, com dois GETs por usuário, após aquecer o
caminho de leitura com 3.000 notas (6.000 GETs). A vazão é o total dos dois GETs. Todos
os GETs retornaram `200`. O contador de `404` falso e os três contadores de fallback
(`recente`, `negado` e `ultimo_numero`) ficaram em zero. A réplica terminou cada rajada
em `streaming`, sem bytes pendentes entre envio e replay do WAL.

| Notas na base | GETs OK | GET solicitação p50/p95/p99/máx (ms) | GET nota fiscal p50/p95/p99/máx (ms) | Vazão (GET/s) | Carga do host (início/pico) | Evidências |
|---:|---:|---:|---:|---:|---:|---|
| 1.000 | 2.000 | 380/657/856/1.040 | 331/664/864/1.137 | 1.000 | 23,73/23,73 | [1k](../../tasks/prd-emissao-nota-fiscal/evidences/runtime-separados-tarefa16/consulta-1000-20261118T021333Z/) |
| 5.000 | 10.000 | 2.864/4.476/5.501/7.191 | 1.894/3.740/5.262/7.165 | 1.111,11 | 26,25/29,08 | [5k](../../tasks/prd-emissao-nota-fiscal/evidences/runtime-separados-tarefa16/consulta-5000-20261118T021430Z/) |
| 10.000 | 20.000 | 221/1.619/2.014/3.068 | 195/1.546/1.632/3.021 | 1.666,67 | 20,54/30,54 | [10k](../../tasks/prd-emissao-nota-fiscal/evidences/runtime-separados-tarefa16/consulta-10000-20261118T021544Z/) |

O pool de leitura da API (25 conexões) chegou a 25 conexões ativas nas três rajadas, com
no máximo 375, 3.152 e 1.256 pedidos pendentes, e a maior espera por conexão registrada
foi de 3,5 s no 1k e de 7,1 s no 5k e no 10k (o valor do 10k é igual ao do 5k e pode ser
acumulado do processo). Nenhuma espera virou timeout: zero KO nas três rajadas.

O 5k voltou a ficar acima do 1k e do 10k (p50 do GET de solicitação de 2.864 ms, contra
380 ms e 221 ms), mesmo com o aquecimento de 3.000 notas, o que enfraquece a hipótese de
JIT frio levantada na comparação de 17/11; a causa não foi investigada. A carga do host
ficou entre 20 e 31 durante toda a tentativa, o que é bem pior que na tentativa 1, e os
percentis são descritivos. Em relação à execução C de 17/11 (10k: 491/989/1.573/2.391 ms
no GET de solicitação, 2.000 GET/s), o 10k desta tentativa teve vazão menor (1.666,67 GET/s)
e cauda maior (p95 de 1.619 ms e máximo de 3.068 ms), e o 1k e o 5k tiveram vazões de
1.000 e 1.111,11 GET/s, contra 666,67 e 714,29.

### Resultados do cenário combinado

Na medição de 1.000 solicitações, todos os `POST` retornaram `202` e os 1.000 GETs
imediatos retornaram `200`. O banco confirmou as 1.000 emissões, zero falhas,
pendências, duplicidades ou lacunas de numeração (notas 3.004 a 4.003 da série 1; as
anteriores são do aquecimento de 3.000). O outbox e o lag Kafka zeraram em 4 s na
verificação iniciada após a espera pela emissão.

| Operação | Vazão | p50 (ms) | p95 (ms) | p99 (ms) | Máx. (ms) |
|---|---:|---:|---:|---:|---:|
| `POST` até o aceite | 250 req/s | 1.163 | 1.708 | 2.223 | 2.281 |
| GET imediato | 250 req/s | 796 | 1.153 | 1.454 | 1.672 |
| `RECEBIDA` até `EMITIDA` | — | 3.237,89 | 3.742,21 | 3.841,56 | — |

O contador de `404` falso terminou em zero. O contador acumulado de fallback por
`recente` terminou em 8 e inclui os 3.000 pedidos de aquecimento e a medição.
A carga média do host foi de 8,13 no início a 8,63 no pico.
[Gatling](../../app/build/reports/gatling/postgetissuanceloadsimulation-20261118015738937/index.html),
[estado no banco](../../app/build/reports/load/rodada-1000.txt) e
[métricas](../../app/build/reports/load/metricas-1000.txt).

## Rodada iniciada em 17/11/2026 às 22:02 UTC

Os quatro cenários passaram entre 22:02 e 22:26 UTC. A execução usou projetos
Compose isolados, com 8 CPUs disponíveis no Docker. A API recebeu 4 CPUs em todos
os cenários e o worker recebeu 4 CPUs no consumo e no combinado. Foram mantidos
o perfil de carga e os aquecimentos padrão.

### Resultados do POST isolado

As seis rajadas ofereceram **1.000 POST/s** com o worker parado. A coluna de vazão
usa o `Cnt/s` do Gatling na janela de medição. Todas as solicitações retornaram `202`
com máximo abaixo de 3 s. A verificação SQL confirmou um evento de outbox por
solicitação, sem chaves duplicadas ou eventos ausentes ou repetidos.
[Coletas da rodada 1](../../tasks/prd-emissao-nota-fiscal/evidences/runtime-separados-tarefa16/post-oficial-1-20261117T220727Z/)
e [coletas da rodada 2](../../tasks/prd-emissao-nota-fiscal/evidences/runtime-separados-tarefa16/post-oficial-2-20261117T220815Z/).

| Rodada | POSTs aceitos | Vazão (POST/s) | p50/p95/p99/máx (ms) | Carga do host (início/pico) | Gatling |
|---:|---:|---:|---:|---:|---|
| 1 | 1.000/1.000 | 500 | 125/212/241/254 | 6,05/6,44 | [1k](../../app/build/reports/gatling/postingressloadsimulation-20261117220647733/index.html) |
| 1 | 5.000/5.000 | 833,33 | 41/484/603/728 | 6,40/16,69 | [5k](../../app/build/reports/gatling/postingressloadsimulation-20261117220656246/index.html) |
| 1 | 10.000/10.000 | 909,09 | **22/130/188/251** | 16,39/24,00 | [10k](../../app/build/reports/gatling/postingressloadsimulation-20261117220712997/index.html) |
| 2 | 1.000/1.000 | 500 | 21/96/141/185 | 22,96/24,25 | [1k](../../app/build/reports/gatling/postingressloadsimulation-20261117220731994/index.html) |
| 2 | 5.000/5.000 | 833,33 | 52/1.391/1.701/1.808 | 23,91/23,91 | [5k](../../app/build/reports/gatling/postingressloadsimulation-20261117220739904/index.html) |
| 2 | 10.000/10.000 | 909,09 | 17/248/366/446 | 20,67/20,67 | [10k](../../app/build/reports/gatling/postingressloadsimulation-20261117220755449/index.html) |

O destaque é a rajada de 10.000 com o menor p95 desta execução. Os percentis medem
o tempo até o aceite HTTP.

### Resultados do consumo isolado

Os tempos de emissão e notificação começam quando o worker é retomado. O
escoamento é o tempo da verificação de outbox e lag Kafka após a última
notificação. O banco confirmou todas as notas e os quatro pares de notificação
por nota, sem duplicidade ou lacuna de numeração. As quatro DLQs ficaram vazias.

| Notas emitidas | Notificações | Até emitir | Até notificar | Vazão de emissão | Escoamento | Carga do host (pico) | Evidências |
|---:|---:|---:|---:|---:|---:|---:|---|
| 1.000 | 4.000 | 4 s | 6 s | 250 notas/s | 1 s | 8,58 | [1k](../../tasks/prd-emissao-nota-fiscal/evidences/runtime-separados-tarefa16/consumo-1000-20261117T221153Z/) |
| 5.000 | 20.000 | 11 s | 16 s | 454,55 notas/s | 2 s | 7,58 | [5k](../../tasks/prd-emissao-nota-fiscal/evidences/runtime-separados-tarefa16/consumo-5000-20261117T221256Z/) |
| 10.000 | 40.000 | 20 s | 60 s | 500 notas/s | 3 s | 9,57 | [10k](../../tasks/prd-emissao-nota-fiscal/evidences/runtime-separados-tarefa16/consumo-10000-20261117T221452Z/) |

### Resultados da consulta isolada

O Gatling iniciou 1.000 usuários por segundo, com dois GETs por usuário, após
aquecer o caminho de leitura com 6.000 GETs. A vazão é o `Cnt/s` total do Gatling.
Todos os GETs retornaram `200`. O contador de `404` falso e os três contadores de
fallback ficaram em zero. A réplica terminou cada rajada em `streaming`, com
zero bytes entre envio e replay do WAL.

| Notas na base | GETs OK | GET solicitação p50/p95/p99/máx (ms) | GET nota fiscal p50/p95/p99/máx (ms) | Vazão (GET/s) | Carga do host (início/pico) | Evidências |
|---:|---:|---:|---:|---:|---:|---|
| 1.000 | 2.000 | 572/928/1.089/1.334 | 480/736/1.040/1.262 | 1.000 | 22,06/22,06 | [1k](../../tasks/prd-emissao-nota-fiscal/evidences/runtime-separados-tarefa16/consulta-1000-20261117T221924Z/) |
| 5.000 | 10.000 | 1.200/1.758/2.573/3.396 | 1.156/1.849/2.571/3.620 | 1.428,57 | 14,61/15,55 | [5k](../../tasks/prd-emissao-nota-fiscal/evidences/runtime-separados-tarefa16/consulta-5000-20261117T222024Z/) |
| 10.000 | 20.000 | 4.448/7.120/8.946/12.954 | 2.618/5.958/6.449/11.785 | 1.250 | 13,07/22,54 | [10k](../../tasks/prd-emissao-nota-fiscal/evidences/runtime-separados-tarefa16/consulta-10000-20261117T222144Z/) |

Na rodada de 10.000 notas, a vazão caiu de 1.818 GET/s na execução C para
1.250 GET/s, com aumento de latência nos dois endpoints. As duas rodadas registraram
contenção no host compartilhado, e a vazão variou mesmo com os mesmos volumes.

### Resultados do cenário combinado

Na medição de 1.000 solicitações, todos os `POST` retornaram `202` e os 1.000 GETs
imediatos retornaram `200`. O banco confirmou as 1.000 emissões, zero falhas,
pendências, duplicidades ou lacunas de numeração. O outbox e o lag Kafka zeraram
em 3 s na verificação iniciada após a espera pela emissão.

| Operação | Vazão | p50 (ms) | p95 (ms) | p99 (ms) | Máx. (ms) |
|---|---:|---:|---:|---:|---:|
| `POST` até o aceite | 333,33 req/s | 805 | 1.012 | 1.306 | 1.390 |
| GET imediato | 333,33 req/s | 726 | 1.296 | 1.568 | 1.699 |
| `RECEBIDA` até `EMITIDA` | — | 4.502,94 | 5.113,83 | 5.358,71 | — |

O contador de `404` falso terminou em zero. O contador acumulado de fallback por
`recente` terminou em 778 e inclui os 3.000 pedidos de aquecimento e a medição.
A carga média do host foi de 8,78 no início a 13,67 no pico.
[Gatling](../../app/build/reports/gatling/postgetissuanceloadsimulation-20261117222539896/index.html),
[estado no banco](../../app/build/reports/load/rodada-1000.txt) e
[métricas](../../app/build/reports/load/metricas-1000.txt).

## Consulta isolada após as otimizações de leitura (17/11/2026, 18:21–20:11 UTC)

Quatro execuções do cenário `consulta` (E2E-14), com a API em 4 CPUs (`LOAD_INSTANCIA_CPUS`, confirmado por `docker inspect`) e o mesmo perfil de carga das rodadas anteriores. Entre as execuções mudaram:

- **A (18:21):** configuração anterior: pool de leitura da API com 10 conexões e réplica com 1 CPU e 768 MiB. O IntelliJ estava aberto.
- **B (18:41):** réplica com os mesmos recursos da primária (2 CPUs e 1 GiB) e pool de leitura da API com 25 conexões, igual ao de escrita. O orçamento agregado de leitura passou de 20 para 35 conexões (CA-30 e TechSpec).
- **C (20:02):** B mais as otimizações de código: o `GET /issuance-requests/{id}` projeta só as colunas da resposta, sem ler nem desserializar o `pedido`, e o `GET /invoices/{n}` lê a nota das colunas de `nota_fiscal` e de `nota_fiscal_item`, sem o documento `conteudo` (jsonb), removido pela migração `V4`. Também entraram as mudanças no worker, nos consumidores e no `POST`, que só afetam a preparação da base.
- **D (20:11):** repetição do 5k isolado (`-PloadRequests=5000`) com o código de C, iniciada com a carga do host abaixo de 8.

Percentis em ms (p50/p95/p99/máx). Vazão é o total de GETs dividido pela duração da rajada; o teste oferece ~2.000 GETs/s (1.000 usuários/s com dois GETs cada).

| Execução | Volume | GET solicitação | GET nota fiscal | KO | Vazão (GETs/s) | Carga do host (início/pico) | Evidências |
|---|---:|---:|---:|---:|---:|---:|---|
| A | 1k | 3.259/4.735/5.178/5.373 | 1.243/2.447/2.814/3.607 | 0 | 333 | 6,1/14,7 | — |
| A | 5k | 5.305/8.278/8.583/10.966 | 2.503/5.222/6.410/11.701 | 0 | 769 | 11,1/21,4 | — |
| A | 10k | 4.805/9.081/9.994/15.173 | 4.131/5.829/9.621/13.669 | 0 | 1.111 | 14,3/23,0 | — |
| B | 1k | 1.344/2.343/2.585/2.796 | 1.012/1.752/2.165/2.427 | 0 | 500 | 9,5/10,6 | — |
| B | 5k | 2.631/3.937/5.231/7.569 | 1.785/3.199/4.346/6.906 | 0 | 1.111 | 18,6/24,0 | — |
| B | 10k | não medido: 1 dos 10.000 `POST` de preparação teve falha de conexão (`Connection reset by peer`) | — | — | — | —/20,7 | — |
| C | 1k | 1.014/1.646/1.854/2.150 | 1.029/1.404/1.922/2.126 | 0 | 500 | 8,2/10,9 | [1k](../../tasks/prd-emissao-nota-fiscal/evidences/runtime-separados-tarefa16/consulta-1000-20261117T200246Z/) |
| C | 5k | 3.209/5.136/5.976/8.107 | 2.170/3.775/4.980/8.012 | 0 | 1.000 | 12,6/23,2 | [5k](../../tasks/prd-emissao-nota-fiscal/evidences/runtime-separados-tarefa16/consulta-5000-20261117T200345Z/) |
| C | 10k | **467/1.060/1.278/1.994** | **374/859/1.190/2.317** | 0 | **1.818** | 15,9/28,9 | [10k](../../tasks/prd-emissao-nota-fiscal/evidences/runtime-separados-tarefa16/consulta-10000-20261117T200456Z/) |
| D | 5k | 2.780/6.878/8.162/10.724 | 3.818/5.664/7.698/11.251 | 0 | 769 | 12,1/19,3 | [5k](../../tasks/prd-emissao-nota-fiscal/evidences/runtime-separados-tarefa16/consulta-5000-20261117T201120Z/) |

Em todas as execuções medidas: zero KO, zero `404` falso, zero fallback para a primária (`recente`, `negado` e `ultimo_numero`) e réplica em streaming sem bytes pendentes no fim. A maior espera por conexão do pool de leitura foi de 14,9 s em A, 7,5 s em B, 7,9 s em C e 11,2 s em D, sem nenhum timeout. O pico de CPU da réplica foi de 46% no 5k de B para 226% no 10k de C, ainda dentro dos 2 CPUs. As evidências de A e B não foram arquivadas no repositório.

**O que as execuções mostram.**

- **O 10k da consulta passou a ser aprovado.** Em C, a API atendeu 1.818 GETs/s, contra 1.111 em A, quase acompanhando a taxa oferecida. A fila praticamente sumiu: o p50 do GET de solicitação caiu de 4,8 s para 0,47 s e o máximo, de 15,2 s para 2,0 s.
- **O 1k melhorou nas duas etapas.** O p50 do GET de solicitação caiu de 3,3 s (A) para 1,3 s com o pool e a réplica (B) e para 1,0 s com as otimizações de código (C).
- **O 5k não melhorou.** O p50 do GET de solicitação foi 2,6 s em B, 3,2 s em C e 2,8 s em D. A hipótese, não verificada, é aquecimento da JVM: o cenário não aquece o caminho de leitura antes de medir (só 3 `POST`), então o 5k mede a primeira rajada pesada de GETs com o JIT ainda frio, enquanto o 10k de C rodou depois de 12.000 GETs. Para confirmar, aquecer os GETs no `carga-consulta.sh` antes das medições ou perfilar o 5k com `LOAD_JFR=1`.
- **O host seguiu saturado.** A carga passou de 19 em todas as rajadas de 5k e 10k, mesmo com o IntelliJ fechado em B, C e D; o Gatling e os contêineres dividem os 8 CPUs com a API.

### Reexecução de A–D com evidências arquivadas (17/11/2026, 22:40–23:01 UTC)

As execuções abaixo repetem a comparação acima e preservam relatórios Gatling, configuração, pools, fallbacks, lag da réplica, recursos e carga do host em cada diretório. A API recebeu 4 CPUs em todas. A usou pool de leitura 10 e réplica com 1 CPU/768 MiB; B usou pool 25 e réplica com 2 CPUs/1 GiB; C usou a configuração de B com as otimizações de código; D repetiu 5k com a configuração de C. O IntelliJ estava aberto em A e fechado em B–D. A carga do host excedeu 8 em todas as rajadas, então os percentis são descritivos.

Percentis em ms (p50/p95/p99/máx). A vazão soma as duas rotas GET. Cada usuário executa um GET de solicitação e um GET de nota fiscal.

| Execução | Volume | GET solicitação | GET nota fiscal | KO no GET | Vazão (GETs/s) | Carga do host (início/pico) | Evidências |
|---|---:|---:|---:|---:|---:|---:|---|
| A | 1k | 428/786/908/1.239 | 337/610/879/1.148 | 0 | 1.000 | 13,0/13,0 | [1k](../../tasks/prd-emissao-nota-fiscal/evidences/runtime-separados-tarefa16/consulta-1000-20261117T224126Z/) |
| A | 5k | 3.984/5.797/6.735/8.070 | 1.605/4.289/5.915/7.981 | 0 | 1.000 | 13,7/15,2 | [5k](../../tasks/prd-emissao-nota-fiscal/evidences/runtime-separados-tarefa16/consulta-5000-20261117T224227Z/) |
| A | 10k | 89/545/844/1.700 | 62/532/783/1.424 | 0 | 1.818 | 9,5/11,0 | [10k](../../tasks/prd-emissao-nota-fiscal/evidences/runtime-separados-tarefa16/consulta-10000-20261117T224342Z/) |
| B | 1k | 680/863/1.189/1.389 | 434/867/1.195/1.389 | 0 | 667 | 11,8/11,8 | [1k](../../tasks/prd-emissao-nota-fiscal/evidences/runtime-separados-tarefa16/consulta-1000-20261117T224757Z/) |
| B | 5k | 1.615/3.248/4.138/6.432 | 1.303/2.880/4.100/5.641 | 0 | 1.111 | 9,6/14,1 | [5k](../../tasks/prd-emissao-nota-fiscal/evidences/runtime-separados-tarefa16/consulta-5000-20261117T224859Z/) |
| B | 10k | não medido: 1 dos 10.000 POSTs de preparação falhou com `Connection reset by peer` | — | 1 POST KO | — | registrado na evidência | [falha na preparação](../../tasks/prd-emissao-nota-fiscal/evidences/runtime-separados-tarefa16/consulta-10000-20261117T224902Z-preparacao-falhou/) |
| C | 1k | 520/879/1.001/1.094 | 453/847/930/1.237 | 0 | 667 | 13,8/13,8 | [1k](../../tasks/prd-emissao-nota-fiscal/evidences/runtime-separados-tarefa16/consulta-1000-20261117T225347Z/) |
| C | 5k | 2.890/6.602/9.447/10.971 | 2.787/6.033/7.744/10.955 | 0 | 714 | 11,4/23,0 | [5k](../../tasks/prd-emissao-nota-fiscal/evidences/runtime-separados-tarefa16/consulta-5000-20261117T225455Z/) |
| C | 10k | 491/989/1.573/2.391 | 438/963/1.472/2.685 | 0 | 2.000 | 15,6/15,6 | [10k](../../tasks/prd-emissao-nota-fiscal/evidences/runtime-separados-tarefa16/consulta-10000-20261117T225611Z/) |
| D | 5k | 4.901/7.304/7.942/10.025 | 3.044/5.204/7.257/11.711 | 0 | 769 | 15,0/21,1 | [5k](../../tasks/prd-emissao-nota-fiscal/evidences/runtime-separados-tarefa16/consulta-5000-20261117T230130Z/) |

As consultas medidas tiveram zero KO, zero fallback para a primária e réplica em streaming sem bytes pendentes no encerramento. A falha de B em 10k ocorreu antes da medição GET; o relatório Gatling da preparação contém 9.999 POSTs aceitos e 1 KO. Os arquivos brutos permitem conferir os percentis e as condições de cada execução. A e B da comparação original continuam sem arquivos recuperáveis; a tabela acima documenta as reexecuções correspondentes, não altera os números históricos.

## Rodada completa dos quatro cenários (17/11/2026, 16:24–17:13 UTC)

Os quatro cenários foram executados novamente, na ordem oficial `post` → `consumo` → `consulta` → `combinado`, no mesmo ambiente: Docker Desktop no macOS com 8 CPUs e 7,7 GiB, instância medida com 4 CPUs (`LOAD_INSTANCIA_CPUS`) e o perfil de carga de sempre (fila de 10.000, espera de 50 s, Tomcat com 12.000 conexões e `accept-count` 4.096, traces a 10%).

**O que mudou desde a tarefa 16.**

- **API com 4 CPUs também no consumo.** O consumo dava 4 CPUs só ao worker, e a API ficava com os 2 CPUs do Compose de referência. A API monta os backlogs a 1.000 req/s antes da medição, e na primeira tentativa desta rodada, com o host carregado, recusou 100 conexões no backlog de 5k ([evidência](../../tasks/prd-emissao-nota-fiscal/evidences/runtime-separados-tarefa16/consumo-1000-20261117T163330Z-api-2cpus/LEIA-ME.txt)). Agora a API recebe 4 CPUs em todos os cenários e o worker, no consumo e no combinado. Durante a medição do consumo a API fica sem tráfego. O `instancia.txt` de cada evidência registra os serviços com o perfil dedicado (`servicos_dedicados`).
- **Actuator do worker no Compose de referência.** A configuração vem do override de carga (porta de gerenciamento e servlet só para o actuator) e passou para o `docker-compose.yml`, publicada em `8082`. O perfil de carga não mudou.

**Condições do host.** A carga média do host ficou entre 9 e 24 durante as medições, e entre 15 e 24 em 16 das 18 rajadas do `POST`, contra 8 a 11 nas duas rodadas oficiais do `POST` da tarefa 16. Além do Docker e do Gatling, o IntelliJ (com picos acima de 300% ao indexar as evidências geradas) e o `mediaanalysisd` do macOS (~100%) disputaram CPU. Os percentis desta rodada refletem essa contenção e não devem ser comparados diretamente com os da tarefa 16.

| Cenário | Resultado |
|---|---|
| POST isolado (E2E-12) | **Não aprovado.** Três execuções completas, com duas rodadas de 1k, 5k e 10k cada (18 rajadas): 14 rajadas dentro da meta; 3 passaram do máximo de 3 s (3.089, 3.383 e 8.087 ms) e uma teve 69 KO de transporte. Nenhuma execução aprovou as duas rodadas inteiras. Em todas as rajadas, toda solicitação que chegou à API foi aceita, e a escrita ficou íntegra: um evento de outbox por solicitação, sem chave nem evento repetidos. |
| Consumo isolado (E2E-13) | **Aprovado** em 1k, 5k e 10k: todas as notas emitidas e notificadas aos 4 consumidores, sem pendências, pares ausentes ou duplicados, eventos repetidos, duplicidade ou lacuna de numeração; DLQs vazias; escoamento em 2 s. |
| Consulta isolada (E2E-14) | **Aprovada em 1k e 5k** (zero KO, zero `404` falso e zero fallback para a primária). **Reprovada em 10k** em duas tentativas: na primeira, 3 de 10.000 POST de preparação tiveram falha de transporte; na segunda, 1.019 de 20.000 GETs (5,1%) responderam `500` por espera do pool de leitura além de 30 s. |
| Combinado (E2E-08/09), 1k | **Aprovado:** 1.000 POST aceitos e emitidos, 1.000 GETs imediatos `200`, 0 KO, zero `404` falso, zero fallback; escoamento em 6 s; numeração íntegra. |

### POST isolado: as 18 rajadas

Percentis p50/p95/p99/máximo em ms. Rajadas fora da meta (100% aceito e máximo de 3.000 ms) em negrito. A carga do host é o pico durante a rajada.

| Execução | Rodada | 1k | 5k | 10k | Carga do host (1k/5k/10k) | Evidências |
|---|---|---|---|---|---|---|
| 1 (16:24) | 1 | 1.139/1.524/1.621/1.657 | 564/1.052/1.307/1.381 | 314/1.572/1.863/2.062 | 5,5/7,4/17,6 | [rodada 1](../../tasks/prd-emissao-nota-fiscal/evidences/runtime-separados-tarefa16/post-oficial-1-20261117T162836Z/) |
| 1 | 2 | 153/335/371/385 | **997/2.519/2.846/3.089** | 102/1.451/1.997/2.371 | 20,7/21,9/20,6 | [rodada 2](../../tasks/prd-emissao-nota-fiscal/evidences/runtime-separados-tarefa16/post-oficial-2-20261117T162921Z/) |
| 2 (16:38) | 1 | **1.788/3.215/3.282/3.383** | 565/905/1.030/1.174 | **5.181/6.545/6.954/8.087** | 21,7/20,8/23,9 | [rodada 1](../../tasks/prd-emissao-nota-fiscal/evidences/runtime-separados-tarefa16/post-oficial-1-20261117T164316Z/) |
| 2 | 2 | 159/270/302/417 | 71/329/397/452 | 21/104/157/205 | 20,6/19,9/19,7 | [rodada 2](../../tasks/prd-emissao-nota-fiscal/evidences/runtime-separados-tarefa16/post-oficial-2-20261117T164356Z/) |
| 3 (17:07) | 1 | 657/949/1.004/1.116 | 148/330/370/479 | 555/1.398/1.583/1.719 | 15,3/16,2/20,2 | [rodada 1](../../tasks/prd-emissao-nota-fiscal/evidences/runtime-separados-tarefa16/post-oficial-1-20261117T171204Z/) |
| 3 | 2 | 1.370/2.452/2.509/2.552 | **591/1.061/1.201/1.369; 69 KO** | 38/422/612/720 | 19,0/17,1/18,6 | [rodada 2](../../tasks/prd-emissao-nota-fiscal/evidences/runtime-separados-tarefa16/post-oficial-2-20261117T171257Z/) |

A latência é bimodal, como na tarefa 15: a mesma rajada ficou abaixo de 0,5 s numa rodada e passou de 3 s em outra, sem mudança de configuração. Os 69 KO da execução 3 foram recusas na conexão (`Connection reset by peer` e `Premature close`). O socket da API não registrou transbordo nem SYN descartado, então a recusa ocorreu antes de a conexão chegar à API. A hipótese, não verificada, é o encaminhamento de portas do Docker Desktop com o host saturado. As 4.931 solicitações aceitas têm 4.931 eventos no outbox.

### Consumo isolado

| Volume | Notas emitidas | Notificações | Até emitir tudo | Até notificar tudo | Vazão de emissão | Escoamento | Carga do host (pico) | Evidências |
|---:|---:|---:|---:|---:|---:|---:|---:|---|
| 1k | 1.000 | 4.000 | 4 s | 7 s | 250 notas/s | 2 s | 9,0 | [1k](../../tasks/prd-emissao-nota-fiscal/evidences/runtime-separados-tarefa16/consumo-1000-20261117T164834Z/) |
| 5k | 5.000 | 20.000 | 12 s | 31 s | 417 notas/s | 2 s | 17,2 | [5k](../../tasks/prd-emissao-nota-fiscal/evidences/runtime-separados-tarefa16/consumo-5000-20261117T164956Z/) |
| 10k | 10.000 | 40.000 | 28 s | 36 s | 357 notas/s | 2 s | 13,0 | [10k](../../tasks/prd-emissao-nota-fiscal/evidences/runtime-separados-tarefa16/consumo-10000-20261117T165133Z/) |

Os tempos ficaram próximos dos da tarefa 16 (7/14/29 s até emitir e 8/27/37 s até notificar).

### Consulta isolada

| Volume | GET solicitação p50/p95/p99/máx (ms) | GET nota fiscal p50/p95/p99/máx (ms) | KO | `404` falso e fallbacks | Réplica no fim | Carga do host (pico) | Evidências |
|---:|---:|---:|---|---|---|---:|---|
| 1k | 1.956/3.078/3.492/3.714 | 1.142/1.997/2.750/3.018 | 0 | 0 e 0 | streaming, 0 bytes pendentes, replay 2 ms | 11,7 | [1k](../../tasks/prd-emissao-nota-fiscal/evidences/runtime-separados-tarefa16/consulta-1000-20261117T165531Z/) |
| 5k | 7.834/11.597/12.614/14.293 | 3.543/7.282/8.205/13.393 | 0 | 0 e 0 | streaming, 0 bytes pendentes (intervalo indisponível) | 13,9 | [5k](../../tasks/prd-emissao-nota-fiscal/evidences/runtime-separados-tarefa16/consulta-5000-20261117T165651Z/) |
| 10k (reprovada) | 21.783/31.331/32.433/34.383 | 8.426/24.197/29.534/32.664 | 1.019 `500` (947 e 72) | não coletados | não coletado | 21,6 | [10k](../../tasks/prd-emissao-nota-fiscal/evidences/runtime-separados-tarefa16/consulta-10000-20261117T170729Z-reprovada/) |

No 10k, os GETs chegam a 1.000 req/s, e cerca de 9.000 usuários ficaram ativos ao mesmo tempo, disputando as 10 conexões do pool de leitura da API. Com o host saturado, a espera passou do `connection-timeout` de 30 s, e essas consultas viraram `500`. É o mesmo gargalo descrito na seção da tarefa 14. Na rodada da tarefa 16, com pico de carga do host de 13,9 na coleta final, os 20.000 GETs do 10k responderam `200`.

### O que esta rodada mostra

- **As garantias de integridade se mantiveram em todos os cenários:** nenhuma duplicidade ou lacuna de numeração, nenhum `404` falso, nenhuma notificação perdida ou duplicada, DLQs vazias e outbox consistente com as solicitações aceitas.
- **As metas de latência e o 10k da consulta dependem da contenção do host.** Com a carga média entre 15 e 24, o `POST` isolado oscilou entre rajadas abaixo de 0,5 s e acima de 3 s, e a consulta de 10k esgotou a espera do pool de leitura. Para usar os números como referência oficial, repita `post` e `consulta` (`-PloadRequests=10000`) com o host ocioso (IntelliJ fechado e `uptime` abaixo de 8).

## Tabelas atualizadas pelos scripts

O `scripts/carga.sh` (cenário combinado) e o `scripts/carga-post.sh` atualizam as duas tabelas abaixo: cada linha é a rodada mais recente daquele volume, e as linhas dos outros volumes são preservadas. Desde a tarefa 16 o combinado roda só 1k; as linhas de 5k e 10k da tarefa 14 saíram desta tabela e continuam na seção da tarefa 14. Os links apontam para os relatórios Gatling e os artefatos em `app/build/reports/`, que não são versionados; as cópias versionadas estão nas evidências citadas acima.

<!-- RESULTADOS-CARGA:INICIO -->
## Últimos resultados por volume

Cada linha mostra o relatório mais recente daquele volume. Os relatórios Gatling e os arquivos SQL guardam os detalhes da rodada.

| Solicitações | Rodada UTC | POST | GET imediato | KO por tipo | Estado final no banco | Emissão p50/p95/p99; integridade | Escoamento (outbox e lag Kafka) | Recursos do app | Evidências |
|---:|---|---|---|---|---|---|---|---|---|
| 1000 | `20261118015738937` | 1000/1000 OK, 0 KO; p50/p95/p99/máx 1163/1708/2223/2281 ms | 1000/1000 OK, 0 KO; p50/p95/p99/máx 796/1153/1454/1672 ms | 0 KO | 1000 aceitas, 1000 emitidas, 0 falhas, 0 pendentes | 3237.89/3742.21/3841.56 ms; 0 dup./0 lacunas | escoado em 4 s após a carga (prazo 300 s) | CPU 30.85%; memória 1.70GiB / 4.00GiB (42.41%) | Prometheus disponível; traces 10%; fila 10000, espera 50s, Tomcat 12000 conexões e accept-count 4096, 2 escritores, lote até 100; métricas de ingresso: fila pico 0, não vazia em 0 de 14 amostras de 1 s; 429 lotes, tamanho médio 9.3; gravação por lote média 40 ms; 0 rejeitadas; instância 4.0 CPUs (Docker 8); carga média do host início/pico/fim 8.13/8.63/8.42; [Gatling](../../app/build/reports/gatling/postgetissuanceloadsimulation-20261118015738937/index.html); [artefatos SQL](../../app/build/reports/load/) |
<!-- RESULTADOS-CARGA:FIM -->

## POST isolado (tarefa 15)

O `scripts/carga-post.sh` (`./gradlew :app:loadTest -PloadScenario=post`) mede só o `POST`: o worker fica parado durante as rajadas, então a API não divide a máquina com a emissão nem com consultas. A meta é aceitar 100% dos `POST` com latência máxima de cada requisição até 3 s (`LOAD_POST_MAX_MS`), verificada por asserção do Gatling; a rodada reprova quando a meta não é atendida. A consistência da escrita é verificada no banco depois de cada rajada: cada solicitação aceita tem exatamente um evento no outbox, sem chave de idempotência nem evento repetidos.

<!-- RESULTADOS-CARGA-POST:INICIO -->
## Últimos resultados do POST isolado por volume

Cada linha mostra a rodada mais recente daquele volume em `scripts/carga-post.sh`: só o `POST` recebe tráfego e o worker fica parado.

| Solicitações | Rodada UTC | POST | Meta (100% aceito e máximo) | KO por tipo | Consistência da escrita | Fila e lotes | Pool de escrita da API | Recursos da API | Configuração e evidências |
|---:|---|---|---|---|---|---|---|---|---|
| 1000 | `20261118014304231` | 1000/1000 OK, 0 KO; p50/p95/p99/máx 74/137/166/175 ms | atendida: máximo 175 ms para meta de 3000 ms; 1000/1000 aceitos | 0 KO | íntegra: 1000 solicitações com 1000 eventos no outbox, sem duplicidade | métricas de ingresso: fila pico 37, não vazia em 1 de 5 amostras de 1 s; 1739 lotes, tamanho médio 11.5; gravação por lote média 23 ms; 0 rejeitadas | ativas 0, pendentes 0, aquisição máx 19 ms, uso máx 308 ms | CPU máx 2%, memória máx 1.33GiB; fila de conexões: 0 transbordos, 0 SYN descartados | fila 10000, espera 50s, Tomcat 12000 conexões e accept-count 4096, 2 escritores, lote até 100; traces 10%; uma conexão por POST; aquecimento de 3000 POST antes das medições; instância 4.0 CPUs (Docker 8); carga média do host início/pico/fim 23.79/23.79/22.45; ambiente padrão do Compose; [Gatling](../../app/build/reports/gatling/postingressloadsimulation-20261118014304231/index.html) |
| 5000 | `20261118014313869` | 5000/5000 OK, 0 KO; p50/p95/p99/máx 51/180/229/308 ms | atendida: máximo 308 ms para meta de 3000 ms; 5000/5000 aceitos | 0 KO | íntegra: 5000 solicitações com 5000 eventos no outbox, sem duplicidade | métricas de ingresso: fila pico 22, não vazia em 3 de 9 amostras de 1 s; 2579 lotes, tamanho médio 9.7; gravação por lote média 18 ms; 0 rejeitadas | ativas 1, pendentes 0, aquisição máx 19 ms, uso máx 308 ms | CPU máx 217%, memória máx 1.33GiB; fila de conexões: 0 transbordos, 0 SYN descartados | fila 10000, espera 50s, Tomcat 12000 conexões e accept-count 4096, 2 escritores, lote até 100; traces 10%; uma conexão por POST; aquecimento de 3000 POST antes das medições; instância 4.0 CPUs (Docker 8); carga média do host início/pico/fim 21.21/22.38/22.38; ambiente padrão do Compose; [Gatling](../../app/build/reports/gatling/postingressloadsimulation-20261118014313869/index.html) |
| 10000 | `20261118014328114` | 10000/10000 OK, 0 KO; p50/p95/p99/máx 12/168/318/421 ms | atendida: máximo 421 ms para meta de 3000 ms; 10000/10000 aceitos | 0 KO | íntegra: 10000 solicitações com 10000 eventos no outbox, sem duplicidade | métricas de ingresso: fila pico 79, não vazia em 5 de 15 amostras de 1 s; 6671 lotes, tamanho médio 5.2; gravação por lote média 9 ms; 0 rejeitadas | ativas 2, pendentes 0, aquisição máx 19 ms, uso máx 308 ms | CPU máx 229%, memória máx 1.05GiB; fila de conexões: 0 transbordos, 0 SYN descartados | fila 10000, espera 50s, Tomcat 12000 conexões e accept-count 4096, 2 escritores, lote até 100; traces 10%; uma conexão por POST; aquecimento de 3000 POST antes das medições; instância 4.0 CPUs (Docker 8); carga média do host início/pico/fim 20.67/21.67/21.61; ambiente padrão do Compose; [Gatling](../../app/build/reports/gatling/postingressloadsimulation-20261118014328114/index.html) |
<!-- RESULTADOS-CARGA-POST:FIM -->

Os percentis do Gatling consideram as respostas do respectivo cenário; falhas de transporte são contadas como KO e aparecem detalhadas no HTML. Quando nenhum POST é aceito, os percentis de GET e de emissão ficam indisponíveis, pois não houve consultas nem notas emitidas.

## Rodadas da tarefa 14 (fila limitada e gravação em lote) × tarefa 13 `20261116043744421`

A tarefa 14 trocou o `Semaphore` por uma fila em memória com capacidade e espera máxima, consumida por 2 escritores que gravam as solicitações em lotes (um comando por lote, até 100 itens); cada `POST` continua individual e só responde depois do commit. As rodadas usam o **perfil de carga** de `scripts/carga.sh`: fila de 10.000, espera máxima de 50 s e Tomcat com 12.000 conexões (os padrões da aplicação são 1.000 e 8 s, e uma rajada de 10k a 1.000 req/s só cabe inteira neles se a vazão de gravação passar de ~900 req/s). Ambiente, rajada, aquecimento, pools e consumidores simulados são os mesmos da seção seguinte. Dados brutos sanitizados e o resumo de cada rodada: [`evidences/runtime-10k-tarefa14/`](../../tasks/prd-emissao-nota-fiscal/evidences/runtime-10k-tarefa14/). A meta da tarefa é zero KO no `POST` nas rodadas de 1k, 5k e 10k; não há meta de latência no PRD.

| Métrica | Tarefa 13 (oficial, 10k) | 1k | 5k | 10k (a) | 10k (b) |
|---|---|---|---|---|---|
| Rodada UTC | `20261116043744421` | `20261116121708017` | `20261116123415456` | `20261116122342452` | `20261116124243358` |
| Ingresso | 50 vagas, espera 8 s | fila 10.000, espera 50 s | idem | idem | idem |
| POST aceitos (202) | 1.897 de 10.000 | 1.000/1.000 | 5.000/5.000 | **10.000/10.000** | **10.000/10.000** |
| KO do POST | 8.098 HTTP 503 e 5 transporte | **0** | **0** | **0** | **0** |
| GET imediato OK | 1.897/1.897 | 1.000/1.000 | 5.000/5.000 | 9.903/10.000 (97 HTTP 500) | 10.000/10.000 |
| POST p50/p95/p99/máx | 13,7/15,2/16,8 s (inclui os `503`) | 4,3/5,8/5,9/6,0 s | 10,8/14,4/15,1/17,9 s | 12,6/14,8/14,9/15,8 s | 16,5/24,1/24,6/26,7 s |
| GET p50/p95/p99/máx | 0,33/4,0/4,5 s | 2,2/5,9/6,0/6,0 s | 6,2/10,0/15,8/17,7 s | 33,9/36,4/39,7/44,7 s | 8,7/24,5/25,1/31,1 s |
| Tempo até `EMITIDA` p50/p95/p99 | 2,3/4,7/5,6 s (1.897 notas) | 5,6/7,5/8,0 s | 16,9/26,3/27,8 s | 55,5/71,8/74,6 s | 35,2/48,2/49,5 s |
| Pool de escrita da API: pendentes, uso máx., aquisições | 25; 5,3 s; — | 0; 0,21 s; 374 | 0; 0,72 s; 978 | 0; 0,36 s; 1.373 | 0; 0,28 s; 1.585 |
| Pool de leitura da API: pendentes, uso máx., timeouts | 29; 5,6 s; 0 | 939; 2,3 s; 0 | 2.603; 2,6 s; 0 | 7.181; 18,6 s; 97 | 7.562; 3,1 s; 0 |
| Pool de escrita do worker: pendentes, uso máx. | 0; 0,45 s | 0; 0,28 s | 0; 0,86 s | 1; 5,0 s | 1; 0,39 s |
| Pico de pendentes no outbox | 15 | 26 | 74 | 64 | 79 |
| Pico de solicitações aguardando o worker | 350 | 693 | 3.900 | 8.479 | 8.136 |
| Sessões `idle in transaction` (máx.; mais antiga) | 24; 5,1 s | 4; 0,06 s | 9; 1,3 s | 12; 12,6 s | 10; 1,9 s |
| Escoamento (outbox; lag Kafka em 300 s) | outbox 0; lag 1.861 | escoado em 176 s | outbox 0; lag 10.865 | outbox 0; lag 22.753 | outbox 0; lag 7.879 |
| CPU máx. API/worker | 223%/193% | 214%/199% | 233%/218% | 300%/742%\* | 235%/231% |
| Memória máx. API | 1,76 GiB | 0,66 GiB | 1,26 GiB | 1,92 GiB | 1,92 GiB |
| Numeração (duplicidades/lacunas) | 0/0 | 0/0 | 0/0 | 0/0 | 0/0 |

\* Amostra isolada de `docker stats` do worker; nas demais rodadas o pico ficou em ~200%.

**O que mudou.** Todas as rodadas aceitaram 100% dos `POST`, sem `503`, falha de transporte nem timeout: 10.000 aceitas, 10.000 emitidas, 0 falhas e numeração íntegra nas duas rodadas de 10k (a tarefa 13 aceitava 19%). A gravação deixou de ser o gargalo do ingresso: a API usa 2 conexões de escrita, com no máximo 0,72 s de uso e nenhum pendente (contra 25 pendentes e 5,3 s), e fez entre 978 e 1.585 aquisições do pool para gravar 5.000 a 10.000 solicitações, ou seja, pelo menos ~5 solicitações por comando em média (o lote cresce com a carga: ~2,7 no 1k). A espera da rajada passou a ser absorvida pela fila: a maior espera de um `POST` foi de 15,8 s e 26,7 s nas rodadas de 10k, abaixo da espera máxima de 50 s e do timeout de 60 s do Gatling. O custo é a latência: o `POST` p50 foi de 12,6 s a 16,5 s no 10k, e o tempo até `EMITIDA` (35 s a 55 s no 10k) volta ao nível da linha de base da tarefa 13 (55 s com 7.803 notas), porque o worker agora emite 10.000 notas e não 1.897.

**O que ainda limita (fora da meta de 0 KO no `POST`).**

- **`GET` imediato e pool de leitura.** O gargalo passou para a leitura: o pool de 10 conexões acumulou 7.181 e 7.562 pendentes no 10k, e o `GET` teve p50 de 8,7 s a 33,9 s. Na rodada (a), 97 `GET` esperaram mais que o `connection-timeout` de 30 s e viraram HTTP 500 (1% dos `GET`); na rodada (b) o pior `GET` levou 31,1 s, no limite. A hipótese, ainda não medida com profiler, é CPU: a API ficou em 235% a 300% com milhares de requisições em memória, e as conexões de leitura ficam retidas enquanto a thread espera CPU.
- **Memória da API.** O pico foi de 1,92 GiB para um limite de 2 GiB nas duas rodadas de 10k, com ~8.000 requisições paradas na fila. A margem é de ~4%; uma capacidade maior ou pedidos maiores arriscam o encerramento do contêiner por falta de memória.
- **Consumidores simulados.** O lag Kafka não zerou em 300 s (concorrência 2, latência de 50 a 300 ms), então o escoamento completo não foi medido; o outbox zerou em todas as rodadas.

**Variação e limitações.** As duas rodadas de 10k tiveram a mesma configuração e resultados bem diferentes (`POST` p50 de 12,6 s e 16,5 s; `GET` p50 de 33,9 s e 8,7 s; 97 `500` contra nenhum), então o resultado dos `GET` não é estável nessa carga; há só uma rodada de 1k e uma de 5k. A JVM começa fria, com poucas requisições de aquecimento. O Docker roda em macOS e o gerador de carga divide a máquina com a pilha. O perfil de carga (fila de 10.000, espera de 50 s) não é o padrão da aplicação. Os artefatos brutos da rodada (a) foram sobrescritos localmente pela rodada seguinte; as cópias sanitizadas estão nas evidências.

## Rodada de 10k da tarefa 13 × linha de base `20261116023731227`

A linha de base é a rodada de 10.000 solicitações depois da tarefa 12 (Spring Data JPA), sem as melhorias da tarefa 13: ingresso sem limite, publicador do outbox sequencial, `POST` em três comandos, tracing a 100%. Todas as rodadas usam o mesmo ambiente: API e worker com 2 CPUs e 2 GiB cada, PostgreSQL com 2 CPUs e 1 GiB, Kafka com 2 CPUs e 1 GiB, Gatling a 1.000 req/s por 10 s e JVM com apenas 3 requisições de aquecimento. Pools: API 25 escrita/10 leitura e worker 5 escrita; consumidores simulados com concorrência 2. Os dados brutos sanitizados estão em [`evidences/runtime-10k-tarefa13/`](../../tasks/prd-emissao-nota-fiscal/evidences/runtime-10k-tarefa13/). Não há meta de latência no PRD; os números abaixo são descritivos.

| Métrica | Linha de base | Sem limite (isolamento) | Espera de 2 s (padrão da TechSpec) | **Calibrada: espera de 8 s (oficial)** |
|---|---|---|---|---|
| Rodada UTC | `20261116023731227` | `20261116040928096` | `20261116042001982` (e `20261116035844444`) | `20261116043744421` (e `20261116042836978`) |
| Ingresso | sem limite | 5.000 vagas, espera 30 s | 50 vagas, espera 2 s | 50 vagas, espera 8 s |
| Timeout do pool de escrita | 30 s | 30 s | 5 s | 10 s |
| Amostragem de traces | 100% | 10% | 10% | 10% |
| POST aceitos (202) | 7.803 | 8.297 | 396 (544) | **1.897** (2.159) |
| KO do POST por tipo | 1.678 transporte, 519 HTTP 500 | 1.699 transporte, 4 HTTP 500 | 9.604 HTTP 503 (na rodada anterior: 9.450 HTTP 503 e 6 HTTP 500) | 8.098 HTTP 503 e 5 transporte (na rodada `…42836978`: 7.841 HTTP 503) |
| GET imediato OK | 7.803/7.803 | 5.867/8.297; 2.430 HTTP 500\* | 396/396 | 1.897/1.897 |
| POST p50/p95/p99 (inclui os KO) | 39,1/50,9/52,0 s | 17,6/31,0/35,0 s | 9,0/9,9/10,4 s | 13,7/15,2/16,8 s |
| GET p50/p95/p99 | 4,8/24,8/30,3 s | 6,3/14,3/16,3 s | 7,6/12,4/13,2 s | 0,33/4,0/4,5 s |
| Tempo até `EMITIDA` p50/p95/p99 | 55,0/74,2/75,8 s | 28,8/42,1/47,8 s | 2,0/4,0/4,3 s | 2,3/4,7/5,6 s |
| Pool de escrita da API: pendentes, uso máx., timeouts | 5.496; 3,6 s; 519 | 4.310; 11,7 s; 4 | 25; 3,4 s; 0 | 25; 5,3 s; 0 |
| Pool de leitura da API: pendentes, uso máx., timeouts | 1.737; 3,3 s; 0 | 2.568; 11,4 s; 2.430\* | 223; 3,5 s; 0 | 29; 5,6 s; 0 |
| Pool de escrita do worker: pendentes, uso máx., timeouts | 1; 9,2 s; 0 | 0; 0,6 s; 0 | 0; 0,26 s; 0 | 0; 0,45 s; 0 |
| Sessões `idle in transaction` (máx.; mais antiga) | 27; 5,7 s | 21; 7,1 s | 20; 1,7 s | 24; 5,1 s |
| Pico de pendentes no outbox | 6.537\*\* | 35 | 7 | **15** |
| Pico de solicitações aguardando o worker | 5.142 | 4.765 | 31 | 350 |
| Escoamento (outbox; lag Kafka em 300 s) | 4.679 pendentes na coleta final | outbox 0; lag 9.963 | outbox 0; lag 14 | outbox 0; lag 1.861 |
| CPU máx. API/worker | 269%/236% | 238%/193% | 235%/184% | 223%/193% |
| Memória máx. API | 1,89 GiB | 1,90 GiB | 1,38 GiB | 1,76 GiB |
| Numeração (duplicidades/lacunas) | 0/0 | 0/0 | 0/0 | 0/0 |

\* Nessa rodada de isolamento, o pool de leitura tinha `connection-timeout` de 5 s por padrão (um erro da tarefa, já corrigido para o padrão de 30 s do Hikari): as esperas por CPU viraram `500` nos `GET`.
\*\* A coleta da linha de base terminou antes do escoamento, então esse pico é um limite inferior.

**O que melhorou.** O gargalo do publicador desapareceu: o outbox deixou de acumular (pico de 15 contra 6.537), e o maior tempo de uso de uma conexão do pool de escrita do worker caiu de 9,2 s para 0,45 s (o publicador deixou de segurar a conexão durante envios sequenciais). Com isso o tempo até `EMITIDA` caiu de 55 s para 2,3 s (p50), e o pico de solicitações à espera do worker, de 5.142 para 350. Nas rodadas com limite houve no máximo 6 `500` por esgotamento do pool de escrita (519 na linha de base) e nenhum na rodada oficial; os encerramentos de conexão caíram de 1.678 para 5. A sobrecarga aparece como `503` com `Retry-After`. O `GET` imediato ficou mais rápido (p50 de 0,33 s contra 4,8 s).

**O que piorou, e por quê.** A aceitação caiu de 7.803 para 1.897 `202`: os 10.000 pedidos chegam em 10 s, e a API de 2 CPUs processa apenas uma fração nesse tempo. A linha de base enfileirava tudo (latência p50 de 39 s, e parte falhava com `500` ou fechamento de conexão); o limite recusa em até 8 s o que não consegue atender. O `503` conta como KO no Gatling e **não aumenta a vazão**, como a TechSpec já previa. Mesmo com 80% das requisições recusadas, a API ficou ~220% de CPU, e o pool de escrita ficou com as 25 conexões em uso, com uso máximo de 3 a 7 s por conexão, enquanto o PostgreSQL tinha pico de CPU de 26% a 65% (limite de 200%): as threads que detêm uma vaga passam a maior parte desse tempo esperando CPU, e não o banco. A hipótese, **não medida com profiler**, é que o trabalho feito antes da vaga (leitura e conversão do corpo, e a resposta `503`) disputa as 2 CPUs com quem já a detém. Se for confirmada, o limite precisa ficar antes da leitura do corpo (por exemplo, num interceptador HTTP), o que muda o desenho do ADR-007.

**Calibração de `ingresso.*`.** Com os padrões da TechSpec (50 vagas, espera de 2 s, timeout do pool de 5 s), duas rodadas aceitaram só 544 e 396 pedidos (4% a 5%). A espera de 2 s é muito menor que a rajada de 10 s, então quase tudo é recusado antes de a fila andar. Subir a espera para 8 s (abaixo dos 15 s em que o cliente de carga fecha a conexão) e o timeout do pool de escrita para 10 s (acima da espera) aceitou 1.897 e 2.159 pedidos em duas rodadas (19% a 22%), sem `500` nem fechamentos relevantes. Os novos padrões são `nota-fiscal.ingresso.espera-maxima=8s` e `APP_DB_WRITE_CONNECTION_TIMEOUT=10s`; `max-simultaneas` continua 50, porque não foi testada outra vaga.

**Limitações.** Há uma ou duas rodadas por configuração, então a variação entre rodadas não está caracterizada (544 e 396 com a mesma configuração de 2 s). A JVM começa fria, com poucas requisições de aquecimento. O Docker roda em macOS, e o gerador de carga divide as 8 CPUs com a pilha. Os consumidores simulados (concorrência 2, latência de 50 a 300 ms) limitam o escoamento: o lag Kafka não zerou em 300 s em nenhuma rodada com mais de poucas centenas de notas, então o tempo de escoamento completo não foi medido. A linha de base usava o cliente Gatling com os mesmos limites, mas a coleta dela não esperou o escoamento.

## Evidência anterior: rodada de 10k com API e worker separados

Por orientação do usuário, a configuração desta rodada não foi comparada com a evidência anterior. API e worker receberam **2 CPUs e 2 GiB cada**; PostgreSQL e Kafka, 2 CPUs e 1 GiB cada. Os limites Hikari somam no máximo 30 conexões de escrita (25 API + 5 worker) e 20 de leitura (10 + 10). O smoke com os dois processos separados concluiu uma emissão, quatro notificações e numeração sem lacunas ou duplicidades.

Na carga de 10.000 solicitações, o Gatling recebeu 9.422 respostas HTTP 202 e registrou 578 falhas de POST, principalmente encerramentos de conexão e uma resposta HTTP 500. Os 9.422 GETs iniciados responderam HTTP 200. O banco confirmou as 9.422 emissões, sem falhas de negócio nem solicitações pendentes nesse recorte; a numeração ficou contínua, de 4 a 9.425. Isso não significa que toda a cadeia assíncrona terminou: na coleta final havia 7.083 itens no outbox e o lag Kafka somado dos quatro consumidores de notificação era 3.743 mensagens.

A API atingiu aproximadamente 211% de CPU (cerca de 2,11 CPUs) e 1,805 GiB de memória (90,23% do limite) nas amostras. O worker atingiu 158% de CPU e 494,9 MiB (24,16%). O pool de escrita da API registrou pico de 667 conexões esperando, uma ocorrência de timeout, aquisição máxima de 30,0 s e uso máximo de 10,756 s; o worker não registrou timeouts. O coletor observou até 19 sessões `idle in transaction`, transação mais antiga de 6,274 s e pico de outbox de 9.189. As amostras completas de pools, recursos e backlog, além dos resultados SQL sem IDs de containers, IPs ou traces, estão em [`evidences/runtime-10k/`](../../tasks/prd-emissao-nota-fiscal/evidences/runtime-10k/). O HTML do Gatling e os artefatos detalhados também estão nos caminhos da tabela.

O ensaio confirma que a separação com 2 CPUs e 2 GiB por processo comporta a maior parte das requisições nesta máquina, mas ainda apresentou falhas no ingresso e backlog de notificações sob a taxa configurada. Latências e percentis são descritivos; não há meta de desempenho definida pelo PRD.

O diagnóstico das falhas e as melhorias propostas, ainda não implementadas, estão em [`melhorias-desempenho.md`](melhorias-desempenho.md).

## Ambiente e método

Os cenários usam Compose isolado, selecionam portas livres e aguardam o
readiness pela porta de gerenciamento. O aquecimento depende do cenário e está
descrito em [Cenários de carga separados](#cenários-de-carga-separados-tarefa-16).
Os scripts coletam métricas Prometheus pela porta de gerenciamento e registram
a amostragem de traces (10% por padrão). O cenário combinado usa `POST` seguido
de GET imediato e verifica o escoamento do outbox e do Kafka após a carga
(`DRAIN_TIMEOUT_SECONDS`, 300 s por padrão).

Execute os cenários na ordem abaixo para repetir a rodada completa.

```bash
./gradlew :app:loadTest -PloadScenario=post
./gradlew :app:loadTest -PloadScenario=consumo
./gradlew :app:loadTest -PloadScenario=consulta
./gradlew :app:loadTest -PloadScenario=combinado
```

Use `-PloadRequests=1000` para selecionar apenas um volume. O combinado já usa
1.000 solicitações por padrão.

Os arquivos `rodada-N.txt`, `numeracao-N.txt`, `metricas-N.txt` e `recursos-N*.txt` registram estado de persistência, integridade da numeração, métricas Prometheus e snapshots de recursos. Os relatórios HTML completos ficam em `app/build/reports/gatling/`. O script atualiza os resultados mais recentes por tamanho em cada rodada concluída; relatórios anteriores permanecem no diretório Gatling para comparação.

Os valores caracterizam a máquina e os limites de recursos usados na execução.
O PRD pede as medições de latência. O teste de `POST` isolado usa um limite
operacional de 3 s por padrão, configurado em `LOAD_POST_MAX_MS`.

## Perfil de instância dedicada e saúde do host (tarefa 16)

Os cenários usam um override Compose temporário. `LOAD_INSTANCIA_CPUS` é 4 por padrão,
limitado ao que o Docker disponibiliza. O Compose de referência usa 4 CPUs na API e
2 no worker. `LOAD_API_CPUS` identifica um experimento explícito. O perfil dedicado
grava no relatório normal. A instância medida grava GC e safepoints em
`app/build/reports/load/gc/`. Cada rodada registra a carga média do host no início,
no pico e no fim. Os coletores compartilham o Mac e o Docker Desktop com o Gatling.

A tarefa 15 mostrou por que isso importa. As duas rodadas oficiais de 10k com 2 CPUs falharam o máximo de 3 s (15,5 s e 25,3 s) com carga média do Mac entre 15 e 20. Experimentos com 2 CPUs passaram em 2 de 2 rodadas quando a contenção era menor; três explorações de 10k com 4 CPUs passaram em 2 de 3 (máximos 1,03 s e 0,38 s; a falha teve parada JVM de ~6–8 s sem causa confirmada). Detalhes sanitizados: [`evidences/runtime-separados-tarefa16/tarefa15/`](../../tasks/prd-emissao-nota-fiscal/evidences/runtime-separados-tarefa16/tarefa15/).

## Cenários de carga separados (tarefa 16)

A ordem oficial é `POST` (`post`) → consumo (`consumo`) → consulta (`consulta`) → combinado (`combinado`). O POST isolado roda 1k, 5k e 10k em duas rodadas oficiais, worker parado, após aquecimento. Consumo cria backlog medido com worker pausado, valida solicitações/eventos no outbox e mede emissão, notificações, pools e lag. Consulta usa dados emitidos e replicados, worker parado, e mede status e nota separadamente, depois de aquecer o caminho de leitura com os GETs de uma base separada de 3.000 notas (`LOAD_CONSULTA_AQUECIMENTO`, relatórios do Gatling descartados); as execuções A a D da seção de 17/11 rodaram antes desse aquecimento. Combinado fica somente em 1k, com API/worker em até 4 CPUs e réplica ativa, depois de aquecer com 3.000 solicitações do próprio cenário e esperar o escoamento delas (`LOAD_COMBINADO_AQUECIMENTO`). Os relatórios do Gatling dos aquecimentos são descartados. Os comandos são `./gradlew :app:loadTest -PloadScenario=post|consumo|consulta|combinado`; `-PloadRequests=1000` restringe uma execução ao volume solicitado.

### Resultado inicial de consumo

Uma execução exploratória de 1k em 16/11/2026 criou e emitiu o backlog de aquecimento de 3.000 solicitações. Em 300 s, os consumidores registraram 761 notificações de 12.000 esperadas; a rodada encerrou sem começar a medição. Na segunda tentativa, o worker foi pausado antes de atribuir as partições Kafka; após o `unpause`, o grupo de emissão só avançou depois de um reinício controlado. Em seguida, surgiram retries nos consumidores e a execução foi encerrada sem completar as notificações. A coleta sanitizada do GC/safepoint e os parâmetros estão em [`evidences/runtime-separados-tarefa16/consumo-inicial/`](../../tasks/prd-emissao-nota-fiscal/evidences/runtime-separados-tarefa16/consumo-inicial/) e [`consumo-pausa-inicial/`](../../tasks/prd-emissao-nota-fiscal/evidences/runtime-separados-tarefa16/consumo-pausa-inicial/). O script agora espera a atribuição inicial antes da pausa e separa concorrência do listener de `max-em-voo`. Estas tentativas não são apresentadas como aprovação de E2E-13; a próxima rodada precisa validar o ajuste.

### Diagnóstico e correção do consumo

As tentativas acima, e uma confirmatória com 8.446/12.000 notificações, falharam por três defeitos do worker, não por capacidade:

1. **Travamento na atribuição de partições (intermitente).** Com `spring.threads.virtual.enabled`, o Boot executava os consumidores do grupo `notafiscal-api` em virtual threads. No JDK 21, o cliente Kafka faz log dentro de `synchronized`, e essas threads esperavam o lock do appender de console presas ao carrier. A próxima da fila do lock não era montada, e os 38 consumidores de plataforma dos grupos de notificação ficavam parados atrás dela. O thread dump e o dump de virtual threads (via `jcmd`) confirmaram o quadro. Correção: os consumidores Kafka voltam a threads de plataforma.
2. **Notificações perdidas.** Cada container buscava até 500 registros por `poll`, acima do limite de 200 em voo do simulador, que rejeita o excedente. Na rodada instrumentada, 3.655 registros falharam uma única vez, sem retry nem DLQ (12.000 − 8.359 = 3.641 ausentes). Correção: `max.poll.records = max-em-voo / concorrência` (20 no perfil de carga).
3. **Corrida no simulador.** A conclusão do futuro confirmava o offset antes da liberação da permissão. Correção: a permissão é liberada primeiro.

A rodada de validação em 1k (não oficial, com carga do host em 12,6 no início) foi aprovada. O aquecimento teve 12.000/12.000 notificações. Na medição, 1.000 notas foram emitidas em 6 s (166,67 notas/s), com 4.000 notificações, nenhum par ausente ou duplicado, numeração íntegra, DLQs vazias e lag zero em 2 s, sem nenhum retry no worker. Evidências em [`consumo-correcao-1k/`](../../tasks/prd-emissao-nota-fiscal/evidences/runtime-separados-tarefa16/consumo-correcao-1k/).

Falhas em lote: com `setSeekAfterError(false)` e listener assíncrono, quando vários registros do mesmo lote falhavam, o spring-kafka 4.1.2 só reprocessava o último, e os demais ficavam sem retry nem DLQ (no TI-30, 0 de 40 notificações chegaram à DLQ em 30 s). Corrigido: o retry com backoff e o encaminhamento à DLQ passaram para o próprio consumidor (`ReceiveNotificationWithRetries`), que só conclui o futuro, e libera o offset, depois do registro ou da confirmação da DLQ.

### Resultados finais da tarefa 16 (rodada anterior, 16 e 17/11/2026, madrugada)

Esta foi a referência até a [rodada de 17/11/2026 à tarde](#rodada-completa-dos-quatro-cenários-17112026-16241713-utc). Ela rodou com o host menos carregado no `POST` (carga média de 8 a 11) e com a API nos 2 CPUs do Compose de referência no consumo.

| Cenário | Resultado | Evidências |
|---|---|---|
| POST isolado (E2E-12), 2 rodadas | Em 1k/5k/10k nas duas rodadas: 100% aceitos, nenhum POST acima de 3 s e outbox consistente (um evento por solicitação; sem duplicidade, ausente ou repetido). Instância da API: 4 CPUs. | [Rodada oficial 1](../../tasks/prd-emissao-nota-fiscal/evidences/runtime-separados-tarefa16/post-oficial-1-20261116T232545Z/), [rodada oficial 2](../../tasks/prd-emissao-nota-fiscal/evidences/runtime-separados-tarefa16/post-oficial-2-20261116T232633Z/) |
| Consumo isolado (E2E-13), 1k/5k/10k | 1k/5k/10k notas emitidas; 4k/20k/40k notificações, respectivamente; sem pendências, repetições, pares ausentes/duplicados, lacunas ou duplicidade de numeração; DLQs vazias. Escoamento em 2/2/3 s. Tempo até emitir: 7/14/29 s; até notificar tudo: 8/27/37 s. | [1k](../../tasks/prd-emissao-nota-fiscal/evidences/runtime-separados-tarefa16/consumo-1000-20261117T000538Z/), [5k](../../tasks/prd-emissao-nota-fiscal/evidences/runtime-separados-tarefa16/consumo-5000-20261117T001039Z/), [10k](../../tasks/prd-emissao-nota-fiscal/evidences/runtime-separados-tarefa16/consumo-10000-20261117T0005Z/) |
| Consulta isolada (E2E-14), 1k/5k/10k | 2k/10k/20k GETs responderam 200; zero KO/404/5xx. Fall-back por `recente`, `negado` e `ultimo_numero`: zero em todos os tamanhos; `404` falso: zero. Réplica em streaming com 0 bytes pendentes em cada fim de medição (replay lag 4 ms, indisponível como valor de intervalo no 5k). | [1k](../../tasks/prd-emissao-nota-fiscal/evidences/runtime-separados-tarefa16/consulta-1000-20261117T004908Z/), [5k](../../tasks/prd-emissao-nota-fiscal/evidences/runtime-separados-tarefa16/consulta-5000-20261117T005020Z/), [10k](../../tasks/prd-emissao-nota-fiscal/evidences/runtime-separados-tarefa16/consulta-10000-20261117T005155Z/) |
| Combinado (E2E-08/09), 1k | 1.000 POSTs aceitos e emitidos, 1.000 GETs imediatos 200, 0 KO, 0 `404` falso; 6 fallbacks por `recente`; demais motivos zero. Outbox e Kafka escoados em 2 s; numeração íntegra. API e worker com 4 CPUs. | [Evidências do combinado](../../tasks/prd-emissao-nota-fiscal/evidences/runtime-separados-tarefa16/combinado-1000-20261117T0039Z/) |

Percentis de consulta Gatling (ms), por endpoint, na mesma ordem de volume:

| Volume | GET solicitação p50/p95/p99/máx | GET nota fiscal p50/p95/p99/máx |
|---:|---:|---:|
| 1k | 2.525 / 3.480 / 3.847 / 4.180 | 833 / 1.554 / 2.260 / 2.659 |
| 5k | 3.754 / 6.495 / 7.009 / 9.568 | 2.265 / 4.029 / 6.513 / 9.605 |
| 10k | 5.540 / 10.885 / 11.487 / 18.548 | 4.934 / 6.171 / 10.558 / 16.058 |

Os percentis são descritivos e potencialmente afetados pela contenção do host: a carga inicial/pico foi 17,61/19,46 em 1k, 18,29/22,41 em 5k e 29,23/26,38 em 10k na coleta com o arquivamento completo. Na coleta final com a métrica de WAL corrigida, os picos foram 11,20, 11,35 e 13,86, respectivamente, ainda acima dos 8 núcleos do Docker. A rodada combinada começou com carga 7,30 e atingiu 10,14. As rodadas de consumo também registraram carga inicial/pico acima de 8 (1k: 9,81/10,38; 5k: 11,75/14,09; 10k: 9,77/11,92). Os arquivos de cada rodada incluem carga amostrada, configuração/CPUs, GC e pools; no relatório de consulta, o lag é coletado de `pg_stat_replication` no primário como bytes entre envio e replay e intervalo de replay.

Nenhuma meta de latência foi atribuída a consumo ou consulta. Os percentis não são usados como comprovação de capacidade da máquina, pois o host compartilhado permaneceu carregado.
