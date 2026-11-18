package com.itau.desafio_diogo_bernard.model.pedido

import com.itau.desafio_diogo_bernard.model.dinheiro.Money
import com.itau.desafio_diogo_bernard.model.item
import com.itau.desafio_diogo_bernard.model.order
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class OrderModelsTest {
    @Test
    fun `exige destinatario PF sem regime`() {
        assertFailsWith<IllegalArgumentException> {
            Recipient(PersonType.FISICA, TaxRegime.OUTROS)
        }
        assertEquals(PersonType.JURIDICA, Recipient(PersonType.JURIDICA).personType)
    }

    @Test
    fun `exige item consistente e pedido com uma entrega`() {
        assertFailsWith<IllegalArgumentException> { item(code = " ") }
        assertFailsWith<IllegalArgumentException> { OrderItem("A", " ", Money.ZERO, 1) }
        assertFailsWith<IllegalArgumentException> { item(price = "-1") }
        assertFailsWith<IllegalArgumentException> { item(quantity = 0) }
        assertFailsWith<IllegalArgumentException> {
            OrderItem("A", "Item", Money.ZERO, 1, BigDecimal("-0.01"))
        }
        assertFailsWith<IllegalArgumentException> { order(items = emptyList()) }
        assertFailsWith<IllegalArgumentException> { order(freightValue = "-1") }
        assertFailsWith<IllegalArgumentException> {
            Order(
                Recipient(PersonType.FISICA),
                listOf(item()),
                listOf(Address(AddressPurpose.ENTREGA, Region.SUL)),
                Money.ZERO,
                BigDecimal("-0.01"),
            )
        }
        val withoutDelivery = listOf(Address(AddressPurpose.COBRANCA, Region.SUL))
        assertFailsWith<IllegalArgumentException> {
            Order(Recipient(PersonType.FISICA), listOf(item()), withoutDelivery, Money.ZERO)
        }
    }

    @Test
    fun `localiza regiao de entrega e aceita cobranca entrega`() {
        val address = Address(AddressPurpose.COBRANCA_ENTREGA, Region.NORTE)
        val order =
            Order(
                Recipient(PersonType.FISICA),
                listOf(item()),
                listOf(address),
                Money.ZERO,
            )
        assertEquals(Region.NORTE, order.deliveryRegion())
        assertFailsWith<IllegalArgumentException> {
            Order(
                Recipient(PersonType.FISICA),
                listOf(item()),
                listOf(address, address),
                Money.ZERO,
            )
        }
    }
}
