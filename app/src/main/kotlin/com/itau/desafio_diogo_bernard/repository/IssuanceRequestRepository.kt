package com.itau.desafio_diogo_bernard.repository

import com.itau.desafio_diogo_bernard.repository.entity.IssuanceRequestEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant
import java.util.UUID

interface IssuanceRequestRepository : JpaRepository<IssuanceRequestEntity, UUID> {
    fun findByClientIdAndKey(
        clientId: String,
        key: UUID,
    ): IssuanceRequestEntity?

    @Query(
        """
        select new com.itau.desafio_diogo_bernard.repository.IssuanceRequestStateRecord(
            s.requestId, s.clientId, s.status, s.receivedAt, s.updatedAt,
            s.invoiceSeries, s.invoiceNumber, s.failureReason
        )
        from IssuanceRequestEntity s
        where s.requestId = :id
        """,
    )
    fun findStatus(
        @Param("id") id: UUID,
    ): IssuanceRequestStateRecord?

    @Query(
        value = """
        WITH entrada AS (
            SELECT * FROM unnest(
                CAST(:#{#l.ids} AS uuid[]), CAST(:#{#l.clients} AS varchar[]), CAST(:#{#l.keys} AS uuid[]),
                CAST(:#{#l.fingerprints} AS varchar[]), CAST(:#{#l.orders} AS text[]),
                CAST(:#{#l.receivedTimes} AS timestamptz[]), CAST(:#{#l.updatedTimes} AS timestamptz[]),
                CAST(:#{#l.eventIds} AS uuid[]), CAST(:#{#l.topics} AS varchar[]),
                CAST(:#{#l.eventKeys} AS varchar[]), CAST(:#{#l.payloads} AS text[]),
                CAST(:#{#l.traceparents} AS varchar[]), CAST(:#{#l.createdTimes} AS timestamptz[])
            ) AS e(id, cliente, chave, impressao, pedido, recebida, atualizada,
                   evento_id, topico, chave_evento, payload, traceparent, criado_em)
        ), nova AS (
            INSERT INTO solicitacao (
                id, cliente_id, idempotency_key, impressao_digital, pedido, status, recebida_em, atualizada_em
            )
            SELECT id, cliente, chave, impressao, CAST(pedido AS jsonb), 'RECEBIDA', recebida, atualizada
            FROM entrada
            ON CONFLICT (cliente_id, idempotency_key) DO NOTHING
            RETURNING id
        )
        INSERT INTO outbox (evento_id, topico, chave, payload, traceparent, criado_em)
        SELECT e.evento_id, e.topico, e.chave_evento, CAST(e.payload AS jsonb), e.traceparent, e.criado_em
        FROM entrada e JOIN nova n ON n.id = e.id
        RETURNING evento_id
        """,
        nativeQuery = true,
    )
    fun insertBatch(
        @Param("l") batch: IssuanceBatchParameters,
    ): List<UUID>

    @Modifying(clearAutomatically = true)
    @Query(
        value = """
            update IssuanceRequestEntity s set
             s.status = 'PROCESSANDO',
             s.updatedAt = :agora
            where s.id = :id and s.status in ('RECEBIDA', 'PROCESSANDO')
        """,
    )
    fun start(
        @Param("id") id: UUID,
        @Param("agora") now: Instant,
    ): Int

    @Query(
        value = """
        SELECT status, nota_serie AS "invoiceSeries", nota_numero AS "invoiceNumber"
        FROM solicitacao WHERE id = :id FOR UPDATE
        """,
        nativeQuery = true,
    )
    fun lockForIssuance(
        @Param("id") id: UUID,
    ): IssuanceStateRecord?

    @Modifying
    @Query(
        "update IssuanceRequestEntity s set s.status = 'EMITIDA', " +
            "s.invoiceSeries = :serie, s.invoiceNumber = :numero, " +
            "s.issuedAt = :emitidaEm, s.updatedAt = :emitidaEm where s.id = :id",
    )
    fun markIssued(
        @Param("id") id: UUID,
        @Param("serie") series: Int,
        @Param("numero") number: Int,
        @Param("emitidaEm") issuedAt: Instant,
    ): Int

    @Modifying
    @Query(
        "update IssuanceRequestEntity s set s.status = 'FALHA', s.failureReason = :motivo, " +
            "s.updatedAt = :agora where s.id = :id and s.status <> 'EMITIDA'",
    )
    fun markFailed(
        @Param("id") id: UUID,
        @Param("motivo") reason: String,
        @Param("agora") now: Instant,
    ): Int
}
