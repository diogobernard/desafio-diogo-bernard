package com.itau.desafio_diogo_bernard.usecase.validations

import com.itau.desafio_diogo_bernard.model.pedido.Order
import com.itau.desafio_diogo_bernard.model.solicitacao.Fingerprint
import java.security.MessageDigest
import java.util.HexFormat

class OrderFingerprint {
    fun calculate(order: Order): Fingerprint {
        val bytes = MessageDigest.getInstance("SHA-256").digest(canonicalize(order).toByteArray(Charsets.UTF_8))
        return Fingerprint(HexFormat.of().formatHex(bytes))
    }

    private fun canonicalize(order: Order): String =
        buildString {
            append("{\"destinatario\":{")
            append("\"tipo\":\"${order.recipient.personType}\",")
            append(
                "\"documento\":\"${escape(
                    order.recipient.document
                        ?.value
                        .orEmpty(),
                )}\",",
            )
            append("\"regime\":\"${order.recipient.taxRegime ?: ""}\"},")
            append("\"itens\":[")
            append(
                order.items.joinToString(",") { item ->
                    "{\"codigo\":\"${escape(item.code)}\",\"descricao\":\"${escape(item.description)}\"," +
                        "\"valor_unitario\":\"${item.providedUnitValue.stripTrailingZeros().toPlainString()}\"," +
                        "\"quantidade\":${item.quantity}}"
                },
            )
            append("],\"valor_frete\":\"${order.providedFreightValue.stripTrailingZeros().toPlainString()}\",")
            append("\"enderecos\":[")
            append(
                order.addresses.joinToString(",") { address ->
                    "{\"finalidade\":\"${address.purpose}\",\"regiao\":\"${address.region}\"}"
                },
            )
            append("]}")
        }

    private fun escape(value: String): String =
        buildString(value.length) {
            value.forEach { character -> appendEscaped(character) }
        }

    private fun StringBuilder.appendEscaped(character: Char) {
        when (character) {
            '\\' -> append("\\\\")
            '"' -> append("\\\"")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            else -> append(character)
        }
    }
}
