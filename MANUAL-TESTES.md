# Testes manuais HTTP

## Preparar o ambiente

Suba o ambiente completo com o script, que cria o `.env` com senhas locais aleatórias
quando ele não existe, sobe PostgreSQL, Kafka, API e worker e espera o readiness dos
dois processos:

```bash
bash scripts/subir-ambiente.sh              # use --replica para incluir a réplica de leitura
bash scripts/subir-ambiente.sh --verificar  # opcional: emite uma nota de teste de ponta a ponta
```

Sem o script:

1. Copie `.env.example` para `.env` e defina `DB_PASSWORD` e `REPLICATION_PASSWORD` com valores locais fictícios ou de desenvolvimento.
2. Inicie a API, o worker e as dependências com `docker compose up --build -d`.
3. Confirme a prontidão da API em `http://localhost:8081/actuator/health/readiness` e do worker em `http://localhost:8082/actuator/health/readiness`.

As métricas Prometheus de cada processo ficam em `http://localhost:8081/actuator/prometheus` (API) e `http://localhost:8082/actuator/prometheus` (worker).

## Executar os exemplos

Os exemplos com `curl`, incluindo os valores esperados da nota, idempotência, cache com `ETag`, erros de validação e a inspeção do banco e do Kafka, estão na seção [Testar manualmente com curl](README.md#testar-manualmente-com-curl) do README.

Os mesmos cenários existem como arquivos para o HTTP Client do IntelliJ ou a extensão REST Client do VS Code:

- [Solicitações](docs/exemplos/solicitacoes.http): emissão PF e PJ, consulta de estado, idempotência, validações e respostas de erro.
- [Notas fiscais](docs/exemplos/notas-fiscais.http): consulta da nota, cache com `ETag`, isolamento por cliente e respostas de erro.

Nem os curls do README nem os arquivos `.http` usam variáveis: os valores estão escritos em cada requisição, prontos para colar no terminal, no Postman (**Import → Raw text**, no caso dos curls) ou no editor. Troque estes valores conforme o seu teste:

| Valor nos exemplos | O que é e quando trocar |
|---|---|
| `X-Client-Id: cliente-local` | Identificador do cliente; qualquer texto. Os dados de um cliente não aparecem para outro |
| `Idempotency-Key: 3f6c2a1e-...` | Um UUID por pedido. A mesma chave com o mesmo pedido devolve a mesma solicitação; com outro pedido, `422`. Para criar uma solicitação nova, troque a chave |
| `019a9a2e-6f3c-7b51-9d2e-4b8f0c1a2d3e` | Troque pelo `id` devolvido no `POST` |
| `/invoices/1?series=1` | Troque pelo `links.invoice` devolvido quando a solicitação fica `EMITIDA` |
| `If-None-Match: "1-1"` | Troque pelo valor do header `ETag` da consulta da nota, com as aspas |

Os exemplos usam clientes e documentos fictícios, gerados apenas com dígitos verificadores válidos. Cada requisição de negócio deve informar `X-Client-Id`; o POST também exige `Idempotency-Key` como UUID. Espere `202` na emissão e consulte `Location`/`Retry-After`; depois do processamento, confira o estado e siga o link da nota. Em nova tentativa do POST, reutilize a mesma chave.

Para encerrar o ambiente, execute `bash scripts/subir-ambiente.sh --parar` (ou `docker compose down`); `--limpar` também apaga os dados do banco.
