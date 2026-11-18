package com.itau.desafio_diogo_bernard.repository.entity

import com.itau.desafio_diogo_bernard.entrypoint.notas.notaFiscalFixture
import com.itau.desafio_diogo_bernard.model.dinheiro.Money
import com.itau.desafio_diogo_bernard.model.dinheiro.TaxRate
import com.itau.desafio_diogo_bernard.model.nota.CalculatedItem
import com.itau.desafio_diogo_bernard.model.pedido.InvoiceRecipient
import com.itau.desafio_diogo_bernard.model.pedido.PersonType
import com.itau.desafio_diogo_bernard.model.pedido.TaxRegime
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import kotlin.test.assertEquals

class InvoiceEntityTest {
    @Test
    fun `reconstroi a nota das colunas e dos itens sem perder valores`() {
        val items = listOf(item(netValue = Money.of("18.00")), item(netValue = null))
        val base = notaFiscalFixture()
        val invoice =
            base.copy(
                calculated =
                    base.calculated.copy(
                        items = items,
                        taxRate = TaxRate.percentage("12"),
                        freightPercentage = TaxRate.percentage("4.8"),
                    ),
                recipient = InvoiceRecipient(PersonType.JURIDICA, "**.345.678/0001-**", TaxRegime.LUCRO_REAL),
            )
        val reference = invoice.reference
        val itemLines =
            items.mapIndexed { index, item ->
                InvoiceItemEntity.fromDomain(reference.series.value, reference.number.value, index + 1, item)
            }

        val readBack = InvoiceEntity.fromDomain(invoice).toDomain(itemLines.map(InvoiceItemEntity::toDomain))

        assertEquals(invoice, readBack)
    }

    @Test
    fun `remove zeros de escala das aliquotas lidas das colunas numeric`() {
        val entity = InvoiceEntity.fromDomain(notaFiscalFixture())
        entity.taxRate = BigDecimal("0.1200")
        entity.delivery.percentage = BigDecimal("0.0480")

        val readBack = entity.toDomain(emptyList())

        assertEquals("0.12", readBack.calculated.taxRate.value.toPlainString())
        assertEquals("0.048", readBack.calculated.freightPercentage.value.toPlainString())
    }

    private fun item(netValue: Money?): CalculatedItem =
        CalculatedItem(
            "SKU",
            "Produto",
            Money.of("10.00"),
            2,
            Money.of("20.00"),
            Money.of("2.00"),
            netValue,
        )
}
