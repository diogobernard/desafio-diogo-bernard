CREATE SCHEMA IF NOT EXISTS notafiscal;
SET search_path TO notafiscal, public;

CREATE TABLE solicitacao (
    id uuid PRIMARY KEY,
    cliente_id varchar(64) NOT NULL,
    idempotency_key uuid NOT NULL,
    impressao_digital char(64) NOT NULL,
    pedido jsonb NOT NULL,
    status varchar(12) NOT NULL
        CHECK (status IN ('RECEBIDA', 'PROCESSANDO', 'EMITIDA', 'FALHA')),
    motivo_falha varchar(200),
    nota_serie int,
    nota_numero int,
    recebida_em timestamptz NOT NULL,
    atualizada_em timestamptz NOT NULL,
    emitida_em timestamptz,
    CONSTRAINT uk_solicitacao_chave UNIQUE (cliente_id, idempotency_key)
);

CREATE TABLE contador_numeracao (
    serie int PRIMARY KEY,
    ultimo_numero int NOT NULL DEFAULT 0
        CHECK (ultimo_numero BETWEEN 0 AND 999999999)
);

CREATE TABLE nota_fiscal (
    serie int NOT NULL,
    numero int NOT NULL CHECK (numero BETWEEN 1 AND 999999999),
    solicitacao_id uuid NOT NULL UNIQUE REFERENCES solicitacao(id),
    cliente_id varchar(64) NOT NULL,
    modo_tributacao varchar(10) NOT NULL,
    aliquota numeric(6, 4) NOT NULL,
    total_itens numeric(15, 2) NOT NULL,
    tributo numeric(15, 2) NOT NULL,
    frete numeric(15, 2) NOT NULL,
    total_nota numeric(15, 2) NOT NULL,
    conteudo jsonb NOT NULL,
    emitida_em timestamptz NOT NULL,
    PRIMARY KEY (serie, numero)
);

CREATE TABLE nota_fiscal_item (
    serie int NOT NULL,
    numero int NOT NULL,
    sequencia int NOT NULL,
    codigo varchar(60) NOT NULL,
    valor_unitario numeric(15, 2) NOT NULL,
    quantidade int NOT NULL,
    total_item numeric(15, 2) NOT NULL,
    tributo_item numeric(15, 2) NOT NULL,
    PRIMARY KEY (serie, numero, sequencia),
    FOREIGN KEY (serie, numero) REFERENCES nota_fiscal(serie, numero)
);

CREATE TABLE outbox (
    id bigserial PRIMARY KEY,
    evento_id uuid NOT NULL UNIQUE,
    topico varchar(120) NOT NULL,
    chave varchar(64) NOT NULL,
    payload jsonb NOT NULL,
    traceparent varchar(64),
    criado_em timestamptz NOT NULL,
    publicado_em timestamptz
);

CREATE INDEX ix_outbox_pendente ON outbox (id) WHERE publicado_em IS NULL;

CREATE TABLE notificacao_recebida (
    consumidor varchar(20) NOT NULL,
    evento_id uuid NOT NULL,
    serie int NOT NULL,
    numero int NOT NULL,
    recebida_em timestamptz NOT NULL,
    entregas int NOT NULL DEFAULT 1,
    PRIMARY KEY (consumidor, evento_id)
);

INSERT INTO contador_numeracao (serie, ultimo_numero) VALUES (1, 0);
