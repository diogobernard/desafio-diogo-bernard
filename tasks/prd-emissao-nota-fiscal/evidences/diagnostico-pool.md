# Diagnóstico de conexões na carga — 2026-11-15 (revisado na tarefa 13)

> **Revisão.** A versão original concluía que o pool de escrita era o ponto de fila e sugeria separar API e worker. Esta revisão confere cada número contra os dados brutos (`app/build/reports/diagnostico/`, coletados em 2026-11-15) e corrige o que a coleta não sustentava. As correções estão marcadas como **Correção**.

## Método

Repeti a carga Gatling 10k em um Compose isolado, com as mesmas configurações de carga (`LOCAL_CONSUMER_CONCURRENCY=2`, PostgreSQL 2 CPU/1 GiB, Kafka 2 CPU/1 GiB). A API ficou em `http://localhost:3000`; PostgreSQL e Kafka usaram as portas 3500 e 3900. Os serviços preexistentes não foram alterados. Nessa rodada a aplicação ainda era um único processo (`app`), antes da separação em API e worker da tarefa 11.

Durante o teste, coletei Prometheus/Hikari e `pg_stat_activity` a cada 2 a 8 s, além de `docker stats`. Artefatos brutos sanitizados em `app/build/reports/diagnostico/`.

**Correção — limite de CPU do app.** O Método original dizia "app 2 CPU/2 GiB", mas o Resultado comparava a CPU com um limite de 4 CPUs. Os dois não podem descrever o mesmo container: as amostras chegam a 449% de CPU, valor impossível sob um limite de 2 CPUs (200%). Os artefatos brutos não registram o limite efetivamente aplicado (`ambiente.txt` guarda só projeto e portas), então o limite real daquela rodada é **indeterminado**. As conclusões abaixo não dependem dele; a comparação de CPU entre rodadas só vale para as rodadas com limite declarado (a tarefa 11 fixou 2 CPUs e 2 GiB para API e worker).

## Resultado

- Gatling: 10.000 POSTs e 10.000 GETs concluídos, 0 KO. Relatório: `app/build/reports/gatling/emissaocargasimulation-20261115204239599/index.html`.
- **Correção — janela cega do Hikari.** As quatro primeiras coletas (de 20:42:31 a 20:42:48, cerca de 25 s até a primeira leitura válida às 20:42:56) retornaram `metrics_unavailable`: o actuator dividia o conector HTTP com a carga saturada. A primeira leitura válida já mostrava 5.424 aquisições pendentes e 4.658 aquisições concluídas, então **5.424 é um limite inferior** do pico real, e o tempo máximo de aquisição de 17,91 s também.
- Pool `notafiscal-write`: máximo configurado 30; amostras com 30 ativas, 0 ociosas, 5.424 pendentes (primeira leitura válida) e 204 pendentes cinco segundos depois. `connections_timeout_total=0`. Pool `notafiscal-read`: pico de 15 ativas, 0 pendentes, zero timeouts.
- **Correção — espera por aquisição.** Na primeira leitura a média acumulada era de 8,19 s por aquisição; às 20:43:01, ao fim da rajada, chegou a **~9,5 s** (93.866 s de espera somados em 9.866 aquisições). Depois da rajada (a partir das 20:43:05), 18.496 aquisições somaram **0,03 s** de espera: o pool deixou de ser disputado assim que o ingresso terminou. A espera vem do ingresso, não da disputa entre o tráfego HTTP e os trabalhos assíncronos.
- PostgreSQL: até 51 sessões totais e 7 sessões em execução simultânea. **Correção:** a coleta de `pg_stat_activity` tem **apenas 6 amostras** (20:42:31 a 20:43:01), o que não permite afirmar ausência de esgotamento do limite do servidor nem de picos breves. Também não separou sessões `idle in transaction`.
- **Correção — CPU.** A versão original citava 137,70%, que é a **última** amostra (a rajada já tinha terminado). As seis amostras do app foram 338,13%, 202,49%, 448,91%, 449,14%, 428,15% e 137,70%; as três de 428% a 449% são as amostras 3 a 5 de 6, com 1,18 a 1,97 GiB de memória, compatíveis com a rajada (as amostras não têm carimbo de tempo). A API esteve saturada de CPU, e não em "cerca de 1,38 CPU". Kafka chegou a 154% e 759 MiB/1 GiB.
- Containers: `OOMKilled=false`, exit code 0. O app chegou a 1,966 GiB (98,30%) de memória.
- PostgreSQL ficou com 10.000 solicitações emitidas e zero pendentes, mas ainda havia **9.230 linhas de outbox sem publicação** ao fim da coleta.

## Interpretação

A fila estava no ingresso: o pool de escrita acumulou milhares de aquisições pendentes durante a rajada, com espera média de ~9,5 s, e a espera praticamente desapareceu depois dela. Sem limite de concorrência, as virtual threads aceitam todas as requisições ao mesmo tempo e elas disputam 30 conexões e uma CPU saturada. A versão original atribuía isso, com cautela, à contenção entre a API e os trabalhos assíncronos no mesmo processo; os dados mostram o contrário, pois depois da rajada a disputa é nula.

**O publicador do outbox limita o escoamento.** `PublicadorOutboxRepository` fazia `kafka.send(...).get()` e um `UPDATE` por evento. Nos clientes Kafka 4.x o `linger.ms` padrão é 5 ms, então cada evento custa cerca de 8 ms e o teto sequencial é de **~125 eventos/s**. Com 9.230 linhas pendentes ao fim da rodada, o backlog era maior que o publicador conseguia escoar. A rodada de linha de base da tarefa 13 (`20261116023731227`) tem a série de pendentes de 2 em 2 s em `app/build/reports/load/atividade-10000.tsv`: o outbox só deixa de crescer depois da rajada e a média líquida de escoamento entre 02:37:34 e 02:39:29 foi de ~95 eventos/s (10.933 publicados em 115 s), abaixo do teto teórico e coerente com ele, e 4.679 eventos ainda estavam pendentes na coleta final. A fila é única e ordenada por `id`, então os eventos `NotaEmitida` esperam atrás de todo o backlog de solicitações.

**Hipótese de separar API e worker: testada na tarefa 11 e descartada como solução.** Com API e worker em processos separados (2 CPUs e 2 GiB cada), a rodada `20261116001800664` manteve a saturação da API (≈211% de CPU, 667 aquisições pendentes no pico observado, 578 POSTs com falha) e o backlog de outbox (7.083 itens e lag Kafka de 3.743 mensagens na coleta final). Separar os processos isola o worker, mas o gargalo está no ingresso e no publicador, que a separação não altera. Os resultados completos estão em [`docs/carga/relatorio.md`](../../../docs/carga/relatorio.md) e em [`runtime-10k/`](runtime-10k/).

O PostgreSQL não mostrou limite de conexões atingido nas amostras, mas a cobertura é pequena (seis amostras). Isso não comprova a causa exata dos 81 `Premature close` observados na execução anterior; eles não se reproduziram na repetição instrumentada.

## Limitações

- Métricas foram amostradas a cada 2 a 8 s, então picos breves podem não ter sido capturados; os primeiros ~25 s do Hikari não foram coletados.
- `pg_stat_activity` teve 6 amostras e não separou `idle in transaction`; `docker stats` não tem carimbo de tempo por amostra.
- O limite de CPU efetivo do app nesta rodada não foi registrado (ver **Correção — limite de CPU do app**).
- Não foi coletado heap dump, thread dump, nem a distribuição detalhada de estados/esperas do PostgreSQL.
- O teto de ~125 eventos/s é uma estimativa por evento (5 ms de `linger.ms` mais a ida e volta), confirmada em ordem de grandeza pela série de pendentes, e não uma medição direta do publicador.
- Os serviços temporários foram encerrados ao fim; os containers preexistentes permaneceram ativos.

## Ações derivadas

As correções de ingresso, transação do `POST`, publicador por tópico, custo de observabilidade e medição estão na tarefa 13 e em [`docs/carga/melhorias-desempenho.md`](../../../docs/carga/melhorias-desempenho.md).
