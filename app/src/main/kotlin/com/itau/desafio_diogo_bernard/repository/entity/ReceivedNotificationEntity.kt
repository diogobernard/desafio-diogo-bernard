package com.itau.desafio_diogo_bernard.repository.entity

import jakarta.persistence.Column
import jakarta.persistence.Embeddable
import jakarta.persistence.EmbeddedId
import jakarta.persistence.Entity
import jakarta.persistence.PostLoad
import jakarta.persistence.PostPersist
import jakarta.persistence.Table
import jakarta.persistence.Transient
import org.springframework.data.domain.Persistable
import java.time.Instant
import java.util.UUID

@Embeddable
data class ReceivedNotificationId(
    @Column(name = "consumidor") var consumer: String = "",
    @Column(name = "evento_id") var eventId: UUID = UUID.randomUUID(),
)

@Entity
@Table(name = "notificacao_recebida")
class ReceivedNotificationEntity(
    @EmbeddedId var notificationKey: ReceivedNotificationId = ReceivedNotificationId(),
    @Column(name = "serie", nullable = false) var series: Int = 1,
    @Column(name = "numero", nullable = false) var number: Int = 1,
    @Column(name = "recebida_em", nullable = false) var receivedAt: Instant = Instant.EPOCH,
    @Column(name = "entregas", nullable = false) var deliveries: Int = 1,
) : Persistable<ReceivedNotificationId> {
    @Transient private var newEntry = true

    override fun getId(): ReceivedNotificationId = notificationKey

    override fun isNew(): Boolean = newEntry

    @PostLoad @PostPersist
    fun markPersisted() {
        newEntry = false
    }
}
