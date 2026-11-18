package com.itau.desafio_diogo_bernard.repository

import com.itau.desafio_diogo_bernard.repository.entity.ReceivedNotificationEntity
import com.itau.desafio_diogo_bernard.repository.entity.ReceivedNotificationId
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant
import java.util.UUID

interface ReceivedNotificationRepository : JpaRepository<ReceivedNotificationEntity, ReceivedNotificationId> {
    @Modifying
    @Query(
        value =
            "INSERT INTO notificacao_recebida (consumidor, evento_id, serie, numero, recebida_em) " +
                "VALUES (:consumidor, :evento, :serie, :numero, :recebida) " +
                "ON CONFLICT (consumidor, evento_id) DO UPDATE SET entregas = notificacao_recebida.entregas + 1",
        nativeQuery = true,
    )
    fun register(
        @Param("consumidor") consumer: String,
        @Param("evento") event: UUID,
        @Param("serie") series: Int,
        @Param("numero") number: Int,
        @Param("recebida") received: Instant,
    ): Int
}
