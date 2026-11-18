package com.itau.desafio_diogo_bernard.repository.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.PostLoad
import jakarta.persistence.PostPersist
import jakarta.persistence.SequenceGenerator
import jakarta.persistence.Table
import jakarta.persistence.Transient
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import org.springframework.data.domain.Persistable
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "outbox")
class OutboxEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "outbox-id")
    @SequenceGenerator(name = "outbox-id", sequenceName = "outbox_id_seq", allocationSize = 50)
    var id: Long = 0,
    @Column(name = "evento_id", nullable = false, unique = true)
    var eventId: UUID = UUID.randomUUID(),
    @Column(name = "topico", nullable = false, length = 120)
    var topic: String = "",
    @Column(name = "chave", nullable = false, length = 64)
    var key: String = "",
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    var payload: String = "{}",
    @Column(length = 64)
    var traceparent: String? = null,
    @Column(name = "criado_em", nullable = false)
    var createdAt: Instant = Instant.EPOCH,
    @Column(name = "publicado_em") var
    publishedAt: Instant? = null,
) : Persistable<Long> {
    @Transient private var newEntry = true

    override fun getId(): Long = id

    override fun isNew(): Boolean = newEntry

    @PostLoad @PostPersist
    fun markPersisted() {
        newEntry = false
    }
}
