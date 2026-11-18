package com.itau.desafio_diogo_bernard.usecase.validations

enum class ViolationType {
    AUSENTE,
    MALFORMADO,
    IMPEDITIVO,
}

data class Violation(
    val field: String,
    val type: ViolationType,
    val message: String,
)
