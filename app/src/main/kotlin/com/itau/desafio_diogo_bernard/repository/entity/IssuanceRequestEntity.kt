package com.itau.desafio_diogo_bernard.repository.entity

import com.itau.desafio_diogo_bernard.model.nota.FiscalNumber
import com.itau.desafio_diogo_bernard.model.nota.InvoiceReference
import com.itau.desafio_diogo_bernard.model.nota.Series
import com.itau.desafio_diogo_bernard.model.solicitacao.ClientId
import com.itau.desafio_diogo_bernard.model.solicitacao.FailureReason
import com.itau.desafio_diogo_bernard.model.solicitacao.Fingerprint
import com.itau.desafio_diogo_bernard.model.solicitacao.IdempotencyKey
import com.itau.desafio_diogo_bernard.model.solicitacao.IssuanceRequest
import com.itau.desafio_diogo_bernard.model.solicitacao.IssuanceRequestId
import com.itau.desafio_diogo_bernard.model.solicitacao.IssuanceRequestStatus
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.PostLoad
import jakarta.persistence.PostPersist
import jakarta.persistence.Table
import jakarta.persistence.Transient
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import org.springframework.data.domain.Persistable
import tools.jackson.databind.json.JsonMapper
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "solicitacao")
class IssuanceRequestEntity(
    @Id @Column(name = "id") var requestId: UUID = UUID.randomUUID(),
    @Column(name = "cliente_id", nullable = false) var clientId: String = "",
    @Column(name = "idempotency_key", nullable = false) var key: UUID = UUID.randomUUID(),
    @Column(name = "impressao_digital", nullable = false, length = 64, columnDefinition = "char(64)")
    @JdbcTypeCode(SqlTypes.CHAR)
    var fingerprint: String = "",
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "pedido", nullable = false, columnDefinition = "jsonb")
    var order: String = "{}",
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    var status: IssuanceRequestStatus = IssuanceRequestStatus.RECEBIDA,
    @Column(name = "motivo_falha", length = 200) var failureReason: String? = null,
    @Column(name = "nota_serie") var invoiceSeries: Int? = null,
    @Column(name = "nota_numero") var invoiceNumber: Int? = null,
    @Column(name = "recebida_em", nullable = false) var receivedAt: Instant = Instant.EPOCH,
    @Column(name = "atualizada_em", nullable = false) var updatedAt: Instant = Instant.EPOCH,
    @Column(name = "emitida_em") var issuedAt: Instant? = null,
) : Persistable<UUID> {
    @Transient private var newEntry = true

    override fun getId(): UUID = requestId

    override fun isNew(): Boolean = newEntry

    @PostLoad @PostPersist
    fun markPersisted() {
        newEntry = false
    }

    fun toDomain(json: JsonMapper): IssuanceRequest {
        val series = invoiceSeries
        val number = invoiceNumber
        val reference =
            if (series == null || number == null) null else InvoiceReference(FiscalNumber(number), Series(series))
        return IssuanceRequest(
            IssuanceRequestId(requestId.toString()),
            ClientId(clientId),
            IdempotencyKey(key.toString()),
            Fingerprint(fingerprint.trim()),
            json.readValue(order, StoredOrder::class.java).toDomain(),
            status,
            receivedAt,
            updatedAt,
            reference,
            failureReason?.let(::FailureReason),
        )
    }

    companion object {
        fun fromDomain(
            s: IssuanceRequest,
            json: JsonMapper,
        ): IssuanceRequestEntity =
            IssuanceRequestEntity(
                UUID.fromString(s.id.value),
                s.clientId.value,
                UUID.fromString(s.key.value),
                s.fingerprint.value,
                json.writeValueAsString(StoredOrder.fromDomain(s.order)),
                status = s.status,
                failureReason = s.failureReason?.value,
                invoiceSeries = s.invoiceReference?.series?.value,
                invoiceNumber = s.invoiceReference?.number?.value,
                receivedAt = s.receivedAt,
                updatedAt = s.updatedAt,
            )
    }
}
