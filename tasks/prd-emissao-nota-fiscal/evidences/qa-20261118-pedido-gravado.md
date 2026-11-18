# Evidência QA — leitura do pedido gravado (18/11/2026)

Ambiente: `bash scripts/subir-ambiente.sh` (API em localhost:8080), com volume preexistente contendo uma solicitação gravada antes da renomeação do contrato HTTP. Documento fictício.

```text
1) Reenvio do mesmo pedido com a chave já usada (…c01), gravada em 17/11 17:48 UTC:
202 {"id":"01a1ea9f-d90d-7001-a2a9-5f56a2dec0b7","status":"EMITIDA","received_at":"2026-11-17T17:48:26.765190Z","updated_at":"2026-11-17T17:48:27.863226Z","invoice":{"number":1,"series":1,"issued_at":"2026-11-17T17:48:27.863226Z"},"links":{"self":"/issuance-reques

2) Mesma chave com outro pedido (quantity 5):
422 {"type":"https://api.notafiscal.local/problemas/422","title":"Idempotency-Key já utilizada","status":422,"detail":"A chave foi usada com outro pedido.","instance":"/issuance-requests","trace_id":"f306cea729f6b74d2cbd5ab4030520bf","errors":[{"field":"Idempoten

3) Corpo vazio com chave nova (validação no controller):
400 {"type":"https://api.notafiscal.local/problemas/400","title":"Requisição inválida","status":400,"detail":"O pedido contém 5 erro(s).","instance":"/issuance-requests","trace_id":"32bea2c5ffb9a5cf3de4621b74c97f04","errors":[{"field":"recipient.person_type","

4) Nota da solicitação reenviada (GET /invoices/1?series=1):
200
```
