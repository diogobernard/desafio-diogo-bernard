# Evidências de execução — QA da API de emissão

Data: 2026-11-18. Execução sanitizada: sem corpos completos de pedidos, documentos, credenciais ou chaves.

## Validações executadas

| Comando | Resultado observado |
|---|---|
| `./gradlew test detekt ktlintCheck --rerun-tasks` | `BUILD SUCCESSFUL`; 147 testes app + 49 domain; detekt e ktlint passaram. |
| `./gradlew :app:integrationTest --rerun-tasks` | `BUILD SUCCESSFUL`; 53 testes, sem falhas. Testcontainers PostgreSQL/Kafka. |
| `./gradlew :app:e2eTest --rerun-tasks` | `BUILD SUCCESSFUL`; 7 testes HTTP, sem falhas. |
| `./gradlew koverHtmlReport koverVerify` | `BUILD SUCCESSFUL`; app: 100% linhas (1587/1587) e branches (341/341); domain: 100% linhas (486/486) e branches (222/222). |
| `bash scripts/smoke-e2e.sh` | Passou: readiness, API e worker separados, uma nota emitida, quatro notificações e métricas Prometheus. O script encerrou o Compose isolado no trap de saída. |
| `git diff --check` | Sem erros de whitespace. |

Contagens extraídas dos XML JUnit: 147 app unidade, 49 domain unidade, 53 integração e 7 E2E HTTP; 256 no total, zero falhas/erros/ignorados.

## Portas e limpeza

O smoke usou o projeto isolado `notafiscal-smoke-37276`; `scripts/lib-compose-isolated.sh` seleciona portas livres nas faixas 3000–3099 (API/management), 3100–3199 (management do worker), 3500–3599 (PostgreSQL), 3600–3699 (réplica) e 3900–3999 (Kafka). O script não imprime os números escolhidos. Após a saída, não restaram containers do projeto; a verificação de listeners nas portas da faixa 30** não encontrou processo escutando. Nenhum serviço preexistente foi parado.

## Interface e evidência visual

O PRD define somente uma API machine-to-machine. Não há frontend, telas, navegação por teclado ou estados visuais; portanto, capturas de navegador e verificações visuais/acessíveis não se aplicam. As respostas e erros foram exercitados por testes HTTP.

## Evidência de carga

As rodadas e seus relatórios detalhados estão em [`docs/carga/relatorio.md`](carga/relatorio.md).

| Cenário | Resultado final desta execução |
|---|---|
| E2E-12, POST 1k/5k/10k, duas rodadas | 100% aceitos, zero KO; máximos por rodada/volume: 742/781/177 ms e 176/150/154 ms. Escrita íntegra. |
| E2E-14, GET 1k/5k/10k com réplica | 2.000/10.000/20.000 GETs `200`; zero KO/404/5xx. Lag final 0 bytes; fallback recente/negado/ultimo_numero: 0/0/0. |
| E2E-13, consumo isolado | As rodadas documentadas passaram em 1k/5k/10k, sem pendências, perdas, duplicidades ou mensagens em DLQ. |

A primeira reexecução de E2E-14 confirmou que o script não iniciava a réplica especificada na TechSpec. O setup foi corrigido e o cenário completo foi repetido com o serviço de réplica ativo; os resultados corrigidos e os artefatos Gatling por volume estão arquivados em `tasks/prd-emissao-nota-fiscal/evidences/runtime-separados-tarefa16/`.
