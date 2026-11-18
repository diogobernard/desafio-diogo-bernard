-- Consistência da escrita do POST para um cliente de carga: cada solicitação aceita tem exatamente
-- um evento SolicitacaoRecebida no outbox, sem chave de idempotência nem evento repetidos.
WITH s AS (
    SELECT id, idempotency_key FROM notafiscal.solicitacao WHERE cliente_id = :'cliente'
),
o AS (
    SELECT o.chave
    FROM notafiscal.outbox o
    JOIN s ON o.chave = s.id::text
    WHERE o.topico = 'notafiscal.solicitacoes.v1'
)
SELECT 'solicitacoes', count(*)::text FROM s
UNION ALL SELECT 'chaves_duplicadas', (count(*) - count(DISTINCT idempotency_key))::text FROM s
UNION ALL SELECT 'eventos_solicitacao', count(*)::text FROM o
UNION ALL SELECT 'solicitacoes_sem_evento', count(*)::text
    FROM s WHERE NOT EXISTS (SELECT 1 FROM o WHERE o.chave = s.id::text)
UNION ALL SELECT 'eventos_repetidos', (count(*) - count(DISTINCT chave))::text FROM o;
