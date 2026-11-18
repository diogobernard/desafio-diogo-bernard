package com.itau.desafio_diogo_bernard.usecase.validations

import com.itau.desafio_diogo_bernard.model.dinheiro.Money
import com.itau.desafio_diogo_bernard.model.pedido.Address
import com.itau.desafio_diogo_bernard.model.pedido.AddressPurpose
import com.itau.desafio_diogo_bernard.model.pedido.Document
import com.itau.desafio_diogo_bernard.model.pedido.Order
import com.itau.desafio_diogo_bernard.model.pedido.OrderItem
import com.itau.desafio_diogo_bernard.model.pedido.PersonType
import com.itau.desafio_diogo_bernard.model.pedido.Recipient
import com.itau.desafio_diogo_bernard.model.pedido.Region
import com.itau.desafio_diogo_bernard.model.pedido.TaxRegime
import com.itau.desafio_diogo_bernard.usecase.pedidoValido
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class OrderFingerprintTest {
    @Test
    fun `escapa caracteres especiais em campos textuais da impressao`() {
        val original = pedidoValido()
        val orderWithCharacters =
            Order(
                recipient = original.recipient.copy(document = Document("12345678909")),
                items = listOf(OrderItem("A\\\"\n\r\t", "B\\\"\n\r\t", Money.of("50.00"), 2)),
                addresses = listOf(Address(AddressPurpose.ENTREGA, Region.SUL)),
                freightValue = Money.of("5.00"),
            )
        val withoutDocument =
            orderWithCharacters.copy(recipient = Recipient(PersonType.FISICA))

        assertNotEquals(
            OrderFingerprint().calculate(orderWithCharacters),
            OrderFingerprint().calculate(withoutDocument),
        )
        assertNotEquals(
            OrderFingerprint().calculate(orderWithCharacters),
            OrderFingerprint().calculate(
                orderWithCharacters.copy(
                    recipient = Recipient(PersonType.JURIDICA, TaxRegime.SIMPLES_NACIONAL),
                ),
            ),
        )
    }

    @Test
    fun `mantem o hexadecimal das impressoes ja gravadas`() {
        val order = pedidoValido()

        val fingerprintValue = OrderFingerprint().calculate(order)

        assertEquals(IMPRESSAO_PEDIDO_VALIDO, fingerprintValue.value)
    }

    @Test
    fun `impressao digital ignora formatacao e detecta mudanca de valor`() {
        val order = pedidoValido()
        val formatted =
            order.copy(
                freightValue = Money.of("5.000"),
                providedFreightValue = BigDecimal("5.0"),
                items = order.items.map { it.copy(providedUnitValue = BigDecimal("50.0")) },
            )
        val changed = order.copy(items = listOf(OrderItem("SKU", "Produto", Money.of("50.00"), 3)))
        val morePrecision =
            order.copy(
                items =
                    order.items.map {
                        it.copy(providedUnitValue = BigDecimal("50.001"))
                    },
            )

        assertEquals(OrderFingerprint().calculate(order), OrderFingerprint().calculate(formatted))
        assertNotEquals(OrderFingerprint().calculate(order), OrderFingerprint().calculate(changed))
        assertNotEquals(OrderFingerprint().calculate(order), OrderFingerprint().calculate(morePrecision))
    }

    private companion object {
        const val IMPRESSAO_PEDIDO_VALIDO = "1ea823e74fa75c85bd476576191f1204ab3f91227c5e79795047ea9a39046fcb"
    }
}
