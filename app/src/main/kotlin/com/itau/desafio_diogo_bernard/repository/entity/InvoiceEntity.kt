package com.itau.desafio_diogo_bernard.repository.entity

import com.itau.desafio_diogo_bernard.model.dinheiro.Money
import com.itau.desafio_diogo_bernard.model.dinheiro.TaxRate
import com.itau.desafio_diogo_bernard.model.nota.CalculatedInvoice
import com.itau.desafio_diogo_bernard.model.nota.CalculatedItem
import com.itau.desafio_diogo_bernard.model.nota.FiscalNumber
import com.itau.desafio_diogo_bernard.model.nota.Invoice
import com.itau.desafio_diogo_bernard.model.nota.InvoiceReference
import com.itau.desafio_diogo_bernard.model.nota.Series
import com.itau.desafio_diogo_bernard.model.nota.TaxationMode
import com.itau.desafio_diogo_bernard.model.solicitacao.ClientId
import com.itau.desafio_diogo_bernard.model.solicitacao.IssuanceRequestId
import jakarta.persistence.Column
import jakarta.persistence.Embeddable
import jakarta.persistence.Embedded
import jakarta.persistence.EmbeddedId
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.PostLoad
import jakarta.persistence.PostPersist
import jakarta.persistence.Table
import jakarta.persistence.Transient
import org.springframework.data.domain.Persistable
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

@Embeddable
data class InvoiceId(
    @Column(name = "serie") var series: Int = 1,
    @Column(name = "numero") var number: Int = 1,
)

@Entity
@Table(name = "nota_fiscal")
class InvoiceEntity(
    @EmbeddedId var invoiceKey: InvoiceId = InvoiceId(),
    @Column(name = "solicitacao_id", nullable = false, unique = true) var requestId: UUID = UUID.randomUUID(),
    @Column(name = "cliente_id", nullable = false) var clientId: String = "",
    @Enumerated(EnumType.STRING)
    @Column(name = "modo_tributacao", nullable = false, length = 10)
    var taxationMode: TaxationMode = TaxationMode.POR_FORA,
    @Column(name = "aliquota", nullable = false, precision = 6, scale = 4) var taxRate: BigDecimal = BigDecimal.ZERO,
    @Column(name = "total_itens", nullable = false, precision = 15, scale = 2)
    var itemsTotal: BigDecimal = BigDecimal.ZERO,
    @Column(name = "tributo", nullable = false, precision = 15, scale = 2) var tax: BigDecimal = BigDecimal.ZERO,
    @Column(name = "frete", nullable = false, precision = 15, scale = 2) var freight: BigDecimal = BigDecimal.ZERO,
    @Column(name = "total_nota", nullable = false, precision = 15, scale = 2)
    var invoiceTotal: BigDecimal = BigDecimal.ZERO,
    @Column(name = "emitida_em", nullable = false) var issuedAt: Instant = Instant.EPOCH,
    @Embedded var recipient: InvoiceRecipientColumns = InvoiceRecipientColumns(),
    @Embedded var delivery: InvoiceFreightColumns = InvoiceFreightColumns(),
) : Persistable<InvoiceId> {
    @Transient private var newEntry = true

    override fun getId(): InvoiceId = invoiceKey

    override fun isNew(): Boolean = newEntry

    @PostLoad @PostPersist
    fun markPersisted() {
        newEntry = false
    }

    fun toDomain(items: List<CalculatedItem>): Invoice =
        Invoice(
            InvoiceReference(FiscalNumber(invoiceKey.number), Series(invoiceKey.series)),
            IssuanceRequestId(requestId.toString()),
            ClientId(clientId),
            CalculatedInvoice(
                items,
                Money.of(itemsTotal),
                Money.of(tax),
                TaxRate.fraction(taxRate),
                Money.of(delivery.providedValue),
                TaxRate.fraction(delivery.percentage),
                Money.of(freight),
                Money.of(invoiceTotal),
                taxationMode,
                delivery.region,
            ),
            issuedAt,
            recipient.toDomain(),
        )

    companion object {
        fun fromDomain(n: Invoice): InvoiceEntity =
            InvoiceEntity(
                InvoiceId(n.reference.series.value, n.reference.number.value),
                UUID.fromString(n.requestId.value),
                n.clientId.value,
                n.calculated.mode,
                n.calculated.taxRate.value,
                n.calculated.itemsTotal.value,
                n.calculated.tax.value,
                n.calculated.freight.value,
                n.calculated.invoiceTotal.value,
                n.issuedAt,
                InvoiceRecipientColumns.fromDomain(n.recipient),
                InvoiceFreightColumns.fromDomain(n.calculated),
            )
    }
}
