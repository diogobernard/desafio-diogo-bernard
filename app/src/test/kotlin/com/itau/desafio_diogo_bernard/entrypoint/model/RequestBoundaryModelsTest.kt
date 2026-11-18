package com.itau.desafio_diogo_bernard.entrypoint.model

import com.itau.desafio_diogo_bernard.model.dinheiro.Money
import com.itau.desafio_diogo_bernard.model.pedido.Address
import com.itau.desafio_diogo_bernard.model.pedido.AddressPurpose
import com.itau.desafio_diogo_bernard.model.pedido.OrderItem
import com.itau.desafio_diogo_bernard.model.pedido.PersonType
import com.itau.desafio_diogo_bernard.model.pedido.Recipient
import com.itau.desafio_diogo_bernard.model.pedido.Region
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class RequestBoundaryModelsTest {
    @Test
    fun `mapeia destinatario e endereco sem valores opcionais`() {
        val recipient = RecipientRequest(PersonType.FISICA, null, null)
        val address = AddressRequest(AddressPurpose.COBRANCA, Region.NORTE)

        assertEquals(Recipient(PersonType.FISICA), recipient.toDomain())
        assertEquals(Address(AddressPurpose.COBRANCA, Region.NORTE), address.toDomain())
        assertEquals(null, AddressRequest(null, Region.NORTE).toInput().purpose)
    }

    @Test
    fun `rejeita campos obrigatorios ausentes nos mapeadores de borda`() {
        assertFailsWith<IllegalArgumentException> { RecipientRequest(null, null, null).toDomain() }
        assertFailsWith<IllegalArgumentException> { AddressRequest(null, null).toDomain() }
        assertFailsWith<IllegalArgumentException> {
            AddressRequest(
                null,
                Region.SUL,
            ).toDomain()
        }
        assertFailsWith<IllegalArgumentException> {
            AddressRequest(AddressPurpose.ENTREGA, null).toDomain()
        }
        assertFailsWith<IllegalArgumentException> {
            ItemRequest(null, "Item", BigDecimal.ONE, BigDecimal.ONE).toDomain()
        }
        assertFailsWith<IllegalArgumentException> {
            ItemRequest("SKU", null, BigDecimal.ONE, BigDecimal.ONE).toDomain()
        }
        assertFailsWith<IllegalArgumentException> { ItemRequest("SKU", "Item", null, BigDecimal.ONE).toDomain() }
        assertFailsWith<IllegalArgumentException> { ItemRequest("SKU", "Item", BigDecimal.ONE, null).toDomain() }
    }

    @Test
    fun `mapeia item para dominio e para entrada`() {
        val item = ItemRequest("SKU", "Produto", BigDecimal("10.00"), BigDecimal("2"))

        val domain = item.toDomain()

        assertEquals("SKU", item.toInput().code)
        assertEquals(OrderItem("SKU", "Produto", Money.of(BigDecimal("10.00")), 2), domain)
    }
}
