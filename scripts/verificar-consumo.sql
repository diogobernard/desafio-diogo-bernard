WITH alvo AS (
    SELECT id, nota_serie, nota_numero
    FROM notafiscal.solicitacao
    WHERE cliente_id = :'cliente'
), eventos AS (
    SELECT o.evento_id, o.chave::uuid AS solicitacao_id
    FROM notafiscal.outbox o
    JOIN alvo a ON a.id = o.chave::uuid
    WHERE o.topico = 'notafiscal.notas.v1'
), consumidores(nome) AS (
    VALUES ('estoque'), ('registro'), ('entrega'), ('financeiro')
), notificacoes AS (
    SELECT n.consumidor, n.evento_id, n.entregas
    FROM notafiscal.notificacao_recebida n
    JOIN eventos e USING (evento_id)
)
SELECT 'solicitacoes' AS verificacao, count(*)::text AS valor FROM alvo
UNION ALL SELECT 'emitidas', count(*)::text FROM alvo WHERE nota_numero IS NOT NULL
UNION ALL SELECT 'eventos_nota', count(*)::text FROM eventos
UNION ALL SELECT 'eventos_repetidos', (count(*) - count(DISTINCT evento_id))::text FROM eventos
UNION ALL SELECT 'notificacoes', count(*)::text FROM notificacoes
UNION ALL SELECT 'pares_consumidor_evento_ausentes', (count(*) * 4 - (SELECT count(*) FROM notificacoes))::text FROM eventos
UNION ALL SELECT 'pares_consumidor_evento_duplicados', count(*)::text FROM notificacoes WHERE entregas <> 1
UNION ALL SELECT 'consumidores_ausentes', count(*)::text
    FROM eventos e CROSS JOIN consumidores c
    WHERE NOT EXISTS (SELECT 1 FROM notificacoes n WHERE n.evento_id = e.evento_id AND n.consumidor = c.nome)
UNION ALL SELECT 'duplicidades_numeracao', (count(*) - count(DISTINCT (serie, numero)))::text
    FROM notafiscal.nota_fiscal WHERE cliente_id = :'cliente'
UNION ALL SELECT 'lacunas_numeracao', coalesce(max(numero) - min(numero) + 1 - count(DISTINCT numero), 0)::text
    FROM notafiscal.nota_fiscal WHERE cliente_id = :'cliente';
