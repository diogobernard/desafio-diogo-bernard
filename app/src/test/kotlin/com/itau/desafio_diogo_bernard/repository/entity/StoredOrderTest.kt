package com.itau.desafio_diogo_bernard.repository.entity

import com.itau.desafio_diogo_bernard.config.JacksonConfig
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
import org.junit.jupiter.api.Test
import tools.jackson.databind.json.JsonMapper
import java.math.BigDecimal
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class StoredOrderTest {
    private val json: JsonMapper =
        JsonMapper.builder().also { JacksonConfig().jsonMapperBuilderCustomizer().customize(it) }.build()

    @Test
    fun `grava e le o pedido sem perder valores informados`() {
        val order = umPedido()

        val stored = json.writeValueAsString(StoredOrder.fromDomain(order))
        val readBack = json.readValue(stored, StoredOrder::class.java).toDomain()

        assertEquals(order, readBack)
        assertEquals(BigDecimal("100.005"), readBack.items.single().providedUnitValue)
        assertEquals(BigDecimal("2.345"), readBack.providedFreightValue)
    }

    @Test
    fun `grava com nomes proprios independentes do contrato HTTP`() {
        val stored = json.writeValueAsString(StoredOrder.fromDomain(umPedido()))

        listOf("destinatario", "tipo_pessoa", "itens", "valor_unitario", "valor_frete", "enderecos", "regiao")
            .forEach { key -> assertTrue(stored.contains("\"$key\""), "chave ausente: $key") }
        listOf("recipient", "items", "freight_value", "addresses")
            .forEach { key -> assertTrue(!stored.contains("\"$key\""), "chave do contrato HTTP: $key") }
    }

    @Test
    fun `le pedido gravado antes da renomeacao do contrato HTTP`() {
        val legacy =
            """
            {"itens":[{"codigo":"SKU-001","descricao":"Teclado","quantidade":2,"valor_unitario":250.00}],
            "enderecos":[{"uf":null,"cep":null,"cidade":null,"regiao":"SUDESTE","finalidade":"ENTREGA","logradouro":null}],
            "valor_frete":30.00,
            "destinatario":{"documento":"52998224725","tipo_pessoa":"FISICA","regime_tributacao":null}}
            """

        val order = json.readValue(legacy, StoredOrder::class.java).toDomain()

        assertEquals(Recipient(PersonType.FISICA, null, Document("52998224725")), order.recipient)
        assertEquals(OrderItem("SKU-001", "Teclado", Money.of("250.00"), 2), order.items.single())
        assertEquals(BigDecimal("30.00"), order.providedFreightValue)
    }

    @Test
    fun `preserva o regime de tributacao da pessoa juridica`() {
        val recipient = Recipient(PersonType.JURIDICA, TaxRegime.LUCRO_PRESUMIDO, Document("11222333000181"))

        assertEquals(recipient, StoredRecipient.fromDomain(recipient).toDomain())
    }

    @Test
    fun `rejeita gravar destinatario sem documento`() {
        assertFailsWith<IllegalArgumentException> { StoredRecipient.fromDomain(Recipient(PersonType.FISICA)) }
    }

    private fun umPedido(): Order =
        Order(
            recipient = Recipient(PersonType.FISICA, null, Document("52998224725")),
            items = listOf(OrderItem("SKU-1", "Produto", Money.of("100.005"), 1, BigDecimal("100.005"))),
            addresses = listOf(Address(AddressPurpose.ENTREGA, Region.SUDESTE)),
            freightValue = Money.of("2.345"),
            providedFreightValue = BigDecimal("2.345"),
        )
}
