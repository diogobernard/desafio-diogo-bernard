SET search_path TO notafiscal, public;
CREATE INDEX ix_outbox_pendente_topico ON outbox (topico, id) WHERE publicado_em IS NULL;
