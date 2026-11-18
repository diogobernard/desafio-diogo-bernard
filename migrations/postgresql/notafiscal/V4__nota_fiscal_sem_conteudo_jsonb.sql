SET search_path TO notafiscal, public;

-- A nota passa a ser lida das colunas e de nota_fiscal_item; o documento jsonb deixa de existir.
ALTER TABLE nota_fiscal
    ADD COLUMN destinatario_tipo_pessoa varchar(8),
    ADD COLUMN destinatario_documento varchar(20),
    ADD COLUMN destinatario_regime varchar(20),
    ADD COLUMN frete_informado numeric(15, 2),
    ADD COLUMN frete_regiao varchar(12),
    ADD COLUMN frete_percentual numeric(6, 4);

ALTER TABLE nota_fiscal_item
    ADD COLUMN descricao text,
    ADD COLUMN valor_liquido numeric(15, 2);

UPDATE nota_fiscal
SET destinatario_tipo_pessoa = conteudo #>> '{destinatario,tipo_pessoa}',
    destinatario_documento = conteudo #>> '{destinatario,documento_mascarado}',
    destinatario_regime = conteudo #>> '{destinatario,regime_tributacao}',
    frete_informado = (conteudo #>> '{frete,valor_informado}')::numeric,
    frete_regiao = conteudo #>> '{frete,regiao}',
    frete_percentual = (conteudo #>> '{frete,percentual}')::numeric;

UPDATE nota_fiscal_item i
SET descricao = item.valor ->> 'descricao',
    valor_liquido = (item.valor ->> 'valor_liquido')::numeric
FROM nota_fiscal n,
     jsonb_array_elements(n.conteudo -> 'itens') WITH ORDINALITY AS item(valor, sequencia)
WHERE i.serie = n.serie
  AND i.numero = n.numero
  AND i.sequencia = item.sequencia;

ALTER TABLE nota_fiscal
    ALTER COLUMN destinatario_tipo_pessoa SET NOT NULL,
    ALTER COLUMN destinatario_documento SET NOT NULL,
    ALTER COLUMN frete_informado SET NOT NULL,
    ALTER COLUMN frete_regiao SET NOT NULL,
    ALTER COLUMN frete_percentual SET NOT NULL,
    DROP COLUMN conteudo;

ALTER TABLE nota_fiscal_item
    ALTER COLUMN descricao SET NOT NULL;
