package com.itau.desafio_diogo_bernard.model.pedido

class Document(
    val value: String,
) {
    fun masked(): String =
        when (value.length) {
            11 -> "***.${value.substring(3, 6)}.${value.substring(6, 9)}-**"
            14 -> "**.${value.substring(2, 5)}.${value.substring(5, 8)}/${value.substring(8, 12)}-**"
            else -> "[DOCUMENTO_INVALIDO]"
        }

    override fun toString(): String = "Documento(${masked()})"

    override fun equals(other: Any?): Boolean = other is Document && value == other.value

    override fun hashCode(): Int = value.hashCode()
}
