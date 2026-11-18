-- Semeia notas fiscais já emitidas para o cenário de consulta (E2E-14), sem API, worker nem Kafka.
-- Uso: psql -v ON_ERROR_STOP=1 -v cliente=<id> -v quantidade=<n> -v serie=<serie> -f seed-consulta.sql
-- Os dados são fictícios. A numeração continua de contador_numeracao, e o contador avança no fim.
-- Cada nota tem um item de 10,00, destinatário pessoa física na região Sudeste (alíquota 0% e frete de 4,8%).
SET search_path TO notafiscal, public;

BEGIN;

SELECT ultimo_numero AS base FROM contador_numeracao WHERE serie = :serie FOR UPDATE \gset

CREATE TEMP TABLE semente ON COMMIT DROP AS
SELECT ordem,
       gen_random_uuid() AS id,
       :base + ordem AS numero,
       now() - interval '10 minutes' + ordem * interval '1 millisecond' AS recebida_em
FROM generate_series(1, :quantidade) AS ordem;

INSERT INTO solicitacao (id, cliente_id, idempotency_key, impressao_digital, pedido, status,
                         nota_serie, nota_numero, recebida_em, atualizada_em, emitida_em)
SELECT id,
       :'cliente',
       gen_random_uuid(),
       encode(sha256(convert_to(id::text, 'UTF8')), 'hex'),
       '{"destinatario": {"tipo_pessoa": "FISICA", "documento": "52998224725", "regime_tributacao": null},
         "itens": [{"codigo": "SKU-CARGA", "descricao": "Item de carga", "valor_unitario": 10.00, "quantidade": 1}],
         "valor_frete": 0,
         "enderecos": [{"finalidade": "ENTREGA", "regiao": "SUDESTE"}]}'::jsonb,
       'EMITIDA',
       :serie,
       numero,
       recebida_em,
       recebida_em + interval '1 second',
       recebida_em + interval '1 second'
FROM semente;

INSERT INTO nota_fiscal (serie, numero, solicitacao_id, cliente_id, modo_tributacao, aliquota,
                         total_itens, tributo, frete, total_nota, emitida_em,
                         destinatario_tipo_pessoa, destinatario_documento, destinatario_regime,
                         frete_informado, frete_regiao, frete_percentual)
SELECT :serie,
       numero,
       id,
       :'cliente',
       'POR_FORA',
       0.0000,
       10.00,
       0.00,
       0.48,
       10.48,
       recebida_em + interval '1 second',
       'FISICA',
       '***.***.***-25',
       NULL,
       0.00,
       'SUDESTE',
       0.0480
FROM semente;

INSERT INTO nota_fiscal_item (serie, numero, sequencia, codigo, descricao, valor_unitario,
                              quantidade, total_item, tributo_item, valor_liquido)
SELECT :serie, numero, 1, 'SKU-CARGA', 'Item de carga', 10.00, 1, 10.00, 0.00, 10.00
FROM semente;

UPDATE contador_numeracao SET ultimo_numero = ultimo_numero + :quantidade WHERE serie = :serie;

COMMIT;
