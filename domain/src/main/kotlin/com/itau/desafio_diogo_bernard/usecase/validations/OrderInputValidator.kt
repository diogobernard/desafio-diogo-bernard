package com.itau.desafio_diogo_bernard.usecase.validations

import com.itau.desafio_diogo_bernard.exception.InvalidOrderException
import com.itau.desafio_diogo_bernard.model.pedido.AddressPurpose
import com.itau.desafio_diogo_bernard.model.pedido.PersonType
import com.itau.desafio_diogo_bernard.model.pedido.TaxRegime
import java.math.BigDecimal

class OrderInputValidator {
    /** Valida o pedido recebido e lança [PedidoInvalidoException] com todas as violações encontradas. */
    fun requireValid(input: OrderInput) {
        val violations = validate(input)
        if (violations.isNotEmpty()) throw InvalidOrderException(violations)
    }

    fun validate(input: OrderInput): List<Violation> =
        buildList {
            validateHeader(input, this)
            validateItems(input.items, this)
            validateFreight(input.freightValue, this)
            validateAddresses(input.addresses, this)
        }

    private fun validateHeader(
        input: OrderInput,
        errors: MutableList<Violation>,
    ) {
        if (input.personType == null) errors.add(missing("destinatario.tipo_pessoa"))
        if (input.document.isNullOrBlank()) errors.add(missing("destinatario.documento"))
        if (input.personType == PersonType.JURIDICA && input.taxRegime == null) {
            errors.add(missing("destinatario.regime_tributacao"))
        }
        if (input.taxRegime == TaxRegime.OUTROS) {
            errors.add(Violation("destinatario.regime_tributacao", ViolationType.IMPEDITIVO, "Regime não suportado"))
        }
        if (input.personType != null && input.document != null &&
            !input.document.hasValidFormat(input.personType)
        ) {
            errors.add(malformed("destinatario.documento"))
        }
    }

    private fun validateItems(
        items: List<OrderItemInput>?,
        errors: MutableList<Violation>,
    ) {
        if (items == null) {
            errors.add(missing("itens"))
            return
        }
        if (items.isEmpty()) errors.add(malformed("itens"))
        if (items.size > LIMITE_ITENS) {
            errors.add(Violation("itens", ViolationType.IMPEDITIVO, "O pedido excede 500 itens"))
        }
        items.forEachIndexed { index, item -> validateItem(item, index, errors) }
    }

    private fun validateItem(
        item: OrderItemInput,
        index: Int,
        errors: MutableList<Violation>,
    ) {
        val field = "itens[$index]"
        if (item.code.isNullOrBlank()) errors.add(missing("$field.codigo"))
        if (item.description.isNullOrBlank()) errors.add(missing("$field.descricao"))
        validateValue(item.unitValue, "$field.valor_unitario", errors)
        validateQuantity(item.quantity, "$field.quantidade", errors)
    }

    private fun validateValue(
        value: BigDecimal?,
        field: String,
        errors: MutableList<Violation>,
    ) {
        if (value == null) {
            errors.add(missing(field))
        } else if (value < BigDecimal.ZERO) {
            errors.add(malformed(field))
        }
    }

    private fun validateQuantity(
        value: BigDecimal?,
        field: String,
        errors: MutableList<Violation>,
    ) {
        if (value == null) {
            errors.add(missing(field))
        } else if (value <= BigDecimal.ZERO || value.stripTrailingZeros().scale() > 0) {
            errors.add(malformed(field))
        }
    }

    private fun validateFreight(
        value: BigDecimal?,
        errors: MutableList<Violation>,
    ) {
        validateValue(value, "valor_frete", errors)
    }

    private fun validateAddresses(
        addresses: List<AddressInput>?,
        errors: MutableList<Violation>,
    ) {
        if (addresses == null) {
            errors.add(missing("enderecos"))
            return
        }
        val deliveryQuantity = addresses.count { it.purpose in FINALIDADES_ENTREGA }
        if (deliveryQuantity != 1) errors.add(malformed("enderecos"))
        addresses.forEachIndexed { index, address ->
            if (address.purpose == null) errors.add(missing("enderecos[$index].finalidade"))
            if (address.region == null) errors.add(missing("enderecos[$index].regiao"))
        }
    }

    private fun missing(field: String): Violation = Violation(field, ViolationType.AUSENTE, "Campo obrigatório")

    private fun malformed(field: String): Violation = Violation(field, ViolationType.MALFORMADO, "Valor inválido")

    private companion object {
        const val LIMITE_ITENS = 500
        val FINALIDADES_ENTREGA = setOf(AddressPurpose.ENTREGA, AddressPurpose.COBRANCA_ENTREGA)
    }
}
