# ADR-003: Idempotência por cliente e chave

- **Status:** aceito
- **Contexto:** retries do cliente não podem criar solicitações ou notas duplicadas.
- **Decisão:** exigir `Idempotency-Key` UUID e impor unicidade por
  `(cliente_id, idempotency_key)`, persistindo também a impressão digital do pedido.
- **Consequências:** a mesma chave e o mesmo conteúdo devolvem a solicitação existente;
  conteúdo diferente resulta em `422`. A restrição do banco torna a decisão atômica
  sob concorrência e escopada ao cliente.
- **Alternativa rejeitada:** lock em memória, que não coordena réplicas da aplicação.
