package com.itau.desafio_diogo_bernard.model.nota

@JvmInline
value class FiscalNumber(
    val value: Int,
) {
    operator fun compareTo(another: FiscalNumber): Int = value.compareTo(another.value)

    init {
        require(value in 1..999_999_999)
    }
}

@JvmInline
value class Series(
    val value: Int,
) {
    init {
        require(value in 1..999)
    }
}

data class InvoiceReference(
    val number: FiscalNumber,
    val series: Series,
)
