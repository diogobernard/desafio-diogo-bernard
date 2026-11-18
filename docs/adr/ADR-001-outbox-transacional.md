# ADR-001: Outbox transacional com polling

- **Status:** aceito
- **Contexto:** a solicitação precisa ser persistida e processada mesmo se o broker
  estiver indisponível durante a gravação HTTP.
- **Decisão:** gravar solicitação e evento na tabela `outbox` na mesma transação
  PostgreSQL. Um publicador reivindica lotes com `FOR UPDATE SKIP LOCKED`, envia ao
  Kafka e marca o evento publicado após confirmação.
- **Consequências:** evita transação distribuída e permite várias instâncias; a
  entrega é pelo menos uma vez, portanto consumidores são idempotentes. O polling
  acrescenta latência e precisa de retenção/limpeza.
- **Alternativa:** CDC com Debezium; fica como evolução quando a operação justificar
  infraestrutura e menor latência.
- **Atualização (tarefa 13):** o publicador tem um ciclo independente por tópico, para que
  `NotaEmitida` não espere atrás do backlog de `SolicitacaoRecebida`. Cada ciclo envia o
  lote ao Kafka sem bloquear por registro, aguarda as confirmações uma vez (até
  `nota-fiscal.outbox.espera-confirmacao`) e marca só os confirmados com um único
  `UPDATE`. Um registro recusado ou sem confirmação continua pendente e é republicado
  (pelo menos uma vez, mesmo `evento_id`), sem bloquear os demais. O envio bloqueante por
  registro limitava a publicação a cerca de 125 eventos/s.
