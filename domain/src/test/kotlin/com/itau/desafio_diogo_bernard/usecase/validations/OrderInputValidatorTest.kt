package com.itau.desafio_diogo_bernard.usecase.validations

import com.itau.desafio_diogo_bernard.exception.InvalidOrderException
import com.itau.desafio_diogo_bernard.model.pedido.AddressPurpose
import com.itau.desafio_diogo_bernard.model.pedido.PersonType
import com.itau.desafio_diogo_bernard.model.pedido.Region
import com.itau.desafio_diogo_bernard.model.pedido.TaxRegime
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class OrderInputValidatorTest {
    @Test
    fun `classifica dados ausentes e malformados como violacoes de entrada`() {
        val input =
            OrderInput(
                PersonType.JURIDICA,
                "abc",
                null,
                listOf(OrderItemInput("SKU", "Item", BigDecimal("-1"), BigDecimal("1.5"))),
                BigDecimal("-2"),
                listOf(
                    AddressInput(AddressPurpose.ENTREGA, Region.SUL),
                    AddressInput(AddressPurpose.COBRANCA_ENTREGA, null),
                ),
            )

        val errors = OrderInputValidator().validate(input)

        assertTrue(errors.any { it.field == "destinatario.regime_tributacao" && it.type == ViolationType.AUSENTE })
        assertTrue(errors.any { it.field == "destinatario.documento" && it.type == ViolationType.MALFORMADO })
        assertTrue(errors.any { it.field == "itens[0].quantidade" && it.type == ViolationType.MALFORMADO })
        assertTrue(errors.any { it.field == "itens[0].valor_unitario" && it.type == ViolationType.MALFORMADO })
        assertTrue(errors.any { it.field == "enderecos" && it.type == ViolationType.MALFORMADO })
        assertTrue(errors.any { it.field == "enderecos[1].regiao" && it.type == ViolationType.AUSENTE })
        assertTrue(errors.any { it.field == "valor_frete" && it.type == ViolationType.MALFORMADO })
    }

    @Test
    fun `classifica regime OUTROS e mais de 500 itens como impeditivos`() {
        val base =
            OrderInput(
                PersonType.JURIDICA,
                "12345678000195",
                TaxRegime.OUTROS,
                listOf(OrderItemInput("A", "Item", BigDecimal.ONE, BigDecimal.ONE)),
                BigDecimal.ZERO,
                listOf(AddressInput(AddressPurpose.ENTREGA, Region.SUL)),
            )

        val item = base.items?.first() ?: error("Fixture incompleta")
        val limit = OrderInputValidator().validate(base.copy(items = List(500) { item }))
        val exceeded = OrderInputValidator().validate(base.copy(items = List(501) { item }))

        assertTrue(limit.none { it.field == "itens" })
        assertEquals(1, exceeded.count { it.type == ViolationType.IMPEDITIVO && it.field == "itens" })
        assertTrue(exceeded.any { it.type == ViolationType.IMPEDITIVO && it.field == "destinatario.regime_tributacao" })
    }

    @Test
    fun `reune campos ausentes e pedido sem itens ou endereco`() {
        val input = OrderInput(null, null, null, emptyList(), null, emptyList())

        val errors = OrderInputValidator().validate(input)

        assertTrue(errors.any { it.field == "destinatario.tipo_pessoa" && it.type == ViolationType.AUSENTE })
        assertTrue(errors.any { it.field == "destinatario.documento" && it.type == ViolationType.AUSENTE })
        assertTrue(errors.any { it.field == "itens" && it.type == ViolationType.MALFORMADO })
        assertTrue(errors.any { it.field == "valor_frete" && it.type == ViolationType.AUSENTE })
        assertTrue(errors.any { it.field == "enderecos" && it.type == ViolationType.MALFORMADO })
    }

    @Test
    fun `aceita entrada valida de pessoa fisica com endereco de cobranca adicional`() {
        val input =
            entradaValida().copy(
                addresses =
                    listOf(
                        AddressInput(AddressPurpose.ENTREGA, Region.SUL),
                        AddressInput(AddressPurpose.COBRANCA, Region.NORTE),
                    ),
            )

        assertTrue(OrderInputValidator().validate(input).isEmpty())
    }

    @Test
    fun `classifica valores e quantidades nulos e rejeita lista de itens ausente`() {
        val input =
            entradaValida().copy(
                items = listOf(OrderItemInput(null, " ", null, null)),
                freightValue = null,
                addresses = null,
            )

        val errors = OrderInputValidator().validate(input)

        assertEquals(
            setOf(
                "itens[0].codigo",
                "itens[0].descricao",
                "itens[0].valor_unitario",
                "itens[0].quantidade",
                "valor_frete",
                "enderecos",
            ),
            errors.map { it.field }.toSet(),
        )
        assertTrue(OrderInputValidator().validate(input.copy(items = null)).any { it.field == "itens" })
    }

    @Test
    fun `rejeita quantidade zero e mais de uma finalidade de entrega`() {
        val input =
            entradaValida().copy(
                items = listOf(OrderItemInput("A", "Item", BigDecimal.ZERO, BigDecimal.ZERO)),
                addresses =
                    listOf(
                        AddressInput(AddressPurpose.ENTREGA, Region.SUL),
                        AddressInput(AddressPurpose.COBRANCA_ENTREGA, Region.NORTE),
                    ),
            )

        val errors = OrderInputValidator().validate(input)

        assertTrue(errors.any { it.field == "itens[0].quantidade" })
        assertTrue(errors.any { it.field == "enderecos" })
    }

    @Test
    fun `classifica endereco sem finalidade e documento PJ ausente`() {
        val input =
            entradaValida().copy(
                personType = PersonType.JURIDICA,
                document = null,
                addresses = listOf(AddressInput(null, Region.SUL)),
            )

        val fields = OrderInputValidator().validate(input).map { it.field }.toSet()

        assertTrue(
            fields.containsAll(
                setOf(
                    "destinatario.documento",
                    "destinatario.regime_tributacao",
                    "enderecos[0].finalidade",
                    "enderecos",
                ),
            ),
        )
    }

    @Test
    fun `classifica descricao nula`() {
        val input =
            entradaValida().copy(
                items = listOf(OrderItemInput("SKU", null, BigDecimal.ONE, BigDecimal.ONE)),
            )

        assertTrue(OrderInputValidator().validate(input).any { it.field == "itens[0].descricao" })
    }

    @Test
    fun `exige pedido valido lancando todas as violacoes encontradas`() {
        val invalid = entradaValida().copy(document = null, freightValue = null)

        val error = assertFailsWith<InvalidOrderException> { OrderInputValidator().requireValid(invalid) }

        assertEquals(setOf("destinatario.documento", "valor_frete"), error.violations.map { it.field }.toSet())
        OrderInputValidator().requireValid(entradaValida())
    }

    private fun entradaValida(): OrderInput =
        OrderInput(
            PersonType.FISICA,
            "12345678909",
            null,
            listOf(OrderItemInput("SKU", "Item", BigDecimal("10.00"), BigDecimal.ONE)),
            BigDecimal("2.00"),
            listOf(AddressInput(AddressPurpose.ENTREGA, Region.SUL)),
        )
}
