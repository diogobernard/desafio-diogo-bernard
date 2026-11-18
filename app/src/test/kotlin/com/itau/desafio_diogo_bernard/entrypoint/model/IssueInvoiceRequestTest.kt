package com.itau.desafio_diogo_bernard.entrypoint.model

import com.itau.desafio_diogo_bernard.model.pedido.AddressPurpose
import com.itau.desafio_diogo_bernard.model.pedido.PersonType
import com.itau.desafio_diogo_bernard.model.pedido.Region
import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class IssueInvoiceRequestTest {
    @Test
    fun `converte request para dominio preservando BigDecimal`() {
        val request =
            IssueInvoiceRequest(
                RecipientRequest(PersonType.FISICA, "52998224725", null),
                listOf(ItemRequest("SKU-1", "Produto", BigDecimal("100.005"), BigDecimal.ONE)),
                BigDecimal("2.345"),
                listOf(AddressRequest(AddressPurpose.ENTREGA, Region.SUDESTE)),
            )

        val order = request.toDomain()

        assertEquals(
            BigDecimal("100.00"),
            order.items
                .single()
                .unitValue.value,
        )
        assertEquals(BigDecimal("100.005"), order.items.single().providedUnitValue)
        assertEquals(BigDecimal("2.345"), order.providedFreightValue)
    }

    @Test
    fun `entrega a validacao os campos recebidos e as ausencias`() {
        val request =
            IssueInvoiceRequest(
                RecipientRequest(PersonType.JURIDICA, "11222333000181", null),
                listOf(ItemRequest("SKU-1", "Produto", BigDecimal.TEN, BigDecimal.ONE)),
                null,
                null,
            )

        val input = request.toInput()

        assertEquals(PersonType.JURIDICA, input.personType)
        assertEquals("11222333000181", input.document)
        assertEquals("SKU-1", input.items?.single()?.code)
        assertNull(input.freightValue)
        assertNull(input.addresses)
    }

    @Test
    fun `entrega ausencias quando o corpo vem vazio`() {
        val input = IssueInvoiceRequest(null, null, null, null).toInput()

        assertNull(input.personType)
        assertNull(input.document)
        assertNull(input.items)
    }
}
