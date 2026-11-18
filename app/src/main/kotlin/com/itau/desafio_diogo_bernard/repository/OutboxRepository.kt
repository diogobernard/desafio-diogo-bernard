package com.itau.desafio_diogo_bernard.repository

import com.itau.desafio_diogo_bernard.repository.entity.OutboxEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

interface OutboxRepository : JpaRepository<OutboxEntity, Long> {
    @Query(
        value =
            "SELECT * FROM outbox WHERE topico = :topico AND publicado_em IS NULL " +
                "ORDER BY id LIMIT :limite FOR UPDATE SKIP LOCKED",
        nativeQuery = true,
    )
    fun selectPendingByTopic(
        @Param("topico") topic: String,
        @Param("limite") limit: Int,
    ): List<OutboxEntity>

    @Modifying
    @Query(value = "UPDATE outbox SET publicado_em = :agora WHERE id = ANY(:ids)", nativeQuery = true)
    fun markPublished(
        @Param("ids") ids: Array<Long>,
        @Param("agora") now: Instant,
    ): Int

    @Modifying
    @Query("delete from OutboxEntity o where o.publishedAt < :limite")
    fun deletePublished(
        @Param("limite") limit: Instant,
    ): Int

    @Transactional(readOnly = true)
    @Query(
        value = "SELECT topico AS topic, count(*) AS total FROM outbox WHERE publicado_em IS NULL GROUP BY topico",
        nativeQuery = true,
    )
    fun countPendingByTopic(): List<PendingByTopic>
}
