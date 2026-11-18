package com.itau.desafio_diogo_bernard.repository.entity

import com.itau.desafio_diogo_bernard.model.dinheiro.Money
import com.itau.desafio_diogo_bernard.model.nota.CalculatedItem
import jakarta.persistence.Column
import jakarta.persistence.Embeddable
import jakarta.persistence.EmbeddedId
import jakarta.persistence.Entity
import jakarta.persistence.PostLoad
import jakarta.persistence.PostPersist
import jakarta.persistence.Table
import jakarta.persistence.Transient
import org.springframework.data.domain.Persistable
import java.math.BigDecimal

@Embeddable
data class InvoiceItemId(
    @Column(name = "serie") var series: Int = 1,
    @Column(name = "numero") var number: Int = 1,
    @Column(name = "sequencia") var sequence: Int = 1,
)

@Entity
@Table(name = "nota_fiscal_item")
class InvoiceItemEntity(
    @EmbeddedId var itemKey: InvoiceItemId = InvoiceItemId(),
    @Column(name = "codigo", nullable = false, length = 60) var code: String = "",
    @Column(name = "descricao", nullable = false, columnDefinition = "text") var description: String = "",
    @Column(name = "valor_unitario", nullable = false, precision = 15, scale = 2)
    var unitValue: BigDecimal = BigDecimal.ZERO,
    @Column(name = "quantidade", nullable = false) var quantity: Int = 1,
    @Column(name = "total_item", nullable = false, precision = 15, scale = 2)
    var itemTotal: BigDecimal = BigDecimal.ZERO,
    @Column(name = "tributo_item", nullable = false, precision = 15, scale = 2)
    var itemTax: BigDecimal = BigDecimal.ZERO,
    @Column(name = "valor_liquido", precision = 15, scale = 2) var netValue: BigDecimal? = null,
) : Persistable<InvoiceItemId> {
    @Transient private var newEntry = true

    override fun getId(): InvoiceItemId = itemKey

    override fun isNew(): Boolean = newEntry

    @PostLoad @PostPersist
    fun markPersisted() {
        newEntry = false
    }

    fun toDomain(): CalculatedItem =
        CalculatedItem(
            code,
            description,
            Money.of(unitValue),
            quantity,
            Money.of(itemTotal),
            Money.of(itemTax),
            netValue?.let(Money::of),
        )

    companion object {
        fun fromDomain(
            series: Int,
            number: Int,
            sequence: Int,
            item: CalculatedItem,
        ): InvoiceItemEntity =
            InvoiceItemEntity(
                InvoiceItemId(series, number, sequence),
                item.code,
                item.description,
                item.unitValue.value,
                item.quantity,
                item.itemTotal.value,
                item.itemTax.value,
                item.netValue?.value,
            )
    }
}
