package com.itau.desafio_diogo_bernard.usecase.calculo

import com.itau.desafio_diogo_bernard.model.dinheiro.Money
import com.itau.desafio_diogo_bernard.model.item
import com.itau.desafio_diogo_bernard.model.nota.TaxationMode
import com.itau.desafio_diogo_bernard.model.order
import com.itau.desafio_diogo_bernard.model.pedido.OrderItem
import com.itau.desafio_diogo_bernard.model.pedido.PersonType
import com.itau.desafio_diogo_bernard.model.pedido.Region
import com.itau.desafio_diogo_bernard.model.pedido.TaxRegime
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class CalculateInvoiceTest {
    private val calculate = CalculateInvoice()

    @Test
    fun `arredonda tributo por item e soma das linhas igual ao total`() {
        val input =
            order(
                items = listOf(item("A", "0.50"), item("B", "0.50")),
                person = PersonType.JURIDICA,
                regime = TaxRegime.SIMPLES_NACIONAL,
                freightValue = "0",
            )
        val result = calculate.calculate(input, TaxationMode.POR_FORA)
        assertEquals(listOf(Money.of("0.02"), Money.of("0.02")), result.items.map { it.itemTax })
        assertEquals(Money.of("0.04"), result.tax)
        assertEquals(result.tax, result.items.fold(Money.ZERO) { sum, item -> sum + item.itemTax })
        assertEquals(result.itemsTotal, result.items.fold(Money.ZERO) { sum, item -> sum + item.itemTotal })
    }

    @Test
    fun `normaliza valor unitario com mais de duas casas`() {
        val items = listOf(item("A", "100.005"), item("B", "100.015"), item("C", "0.125"))
        assertEquals(
            listOf(Money.of("100.00"), Money.of("100.02"), Money.of("0.12")),
            items.map { it.unitValue },
        )
    }

    @Test
    fun `calcula total da nota por fora`() {
        val result = calculate.calculate(order(itensExemplo()), TaxationMode.POR_FORA)
        assertEquals("SKU-001", result.items.first().code)
        assertEquals("Produto SKU-001", result.items.first().description)
        assertEquals(Money.of("100.00"), result.items.first().unitValue)
        assertEquals(2, result.items.first().quantity)
        assertEquals(Money.of("50.00"), result.freightValue)
        assertEquals(Money.of("550.00"), result.itemsTotal)
        assertEquals(Money.of("66.00"), result.tax)
        assertEquals(0, "0.12".toBigDecimal().compareTo(result.taxRate.value))
        assertEquals(Money.of("52.40"), result.freight)
        assertEquals(Money.of("668.40"), result.invoiceTotal)
        assertEquals(TaxationMode.POR_FORA, result.mode)
        assertEquals(Region.SUDESTE, result.deliveryRegion)
        assertEquals(0, "0.048".toBigDecimal().compareTo(result.freightPercentage.value))
    }

    @Test
    fun `calcula tributacao por dentro por item`() {
        val result = calculate.calculate(order(itensExemplo()), TaxationMode.POR_DENTRO)
        assertEquals(listOf(Money.of("21.43"), Money.of("37.50")), result.items.map { it.itemTax })
        assertEquals(Money.of("58.93"), result.tax)
        result.items.forEach { item ->
            val net = assertNotNull(item.netValue)
            assertEquals(item.itemTotal, net + item.itemTax)
        }
        assertEquals(Money.of("602.40"), result.invoiceTotal)
    }

    @Test
    fun `calculos de pedidos distintos sao independentes`() {
        val orderA = order(items = listOf(item("A", "600")))
        val orderB = order(items = listOf(item("B", "1200")))
        val invoiceA = calculate.calculate(orderA, TaxationMode.POR_FORA)
        calculate.calculate(orderB, TaxationMode.POR_FORA)
        assertEquals(invoiceA, calculate.calculate(orderA, TaxationMode.POR_FORA))
        assertEquals(listOf("A"), invoiceA.items.map { it.code })
    }

    private fun itensExemplo(): List<OrderItem> = listOf(item("SKU-001", "100.005", 2), item("SKU-002", "350"))
}
