package com.itau.desafio_diogo_bernard.usecase.validations

import com.itau.desafio_diogo_bernard.model.pedido.PersonType

internal fun String.hasValidFormat(personType: PersonType): Boolean {
    if (any { !it.isDigit() }) return false
    return when (personType) {
        PersonType.FISICA -> isValidCpf()
        PersonType.JURIDICA -> isValidCnpj()
    }
}

private fun String.isValidCpf(): Boolean {
    if (length != TAMANHO_CPF || all { it == first() }) return false
    val digits = map(Char::digitToInt)
    val firstItem = checkDigit(digits.take(9), (10 downTo 2).toList())
    val secondItem = checkDigit(digits.take(10), (11 downTo 2).toList())
    return digits[9] == firstItem && digits[10] == secondItem
}

private fun String.isValidCnpj(): Boolean {
    if (length != TAMANHO_CNPJ || all { it == first() }) return false
    val digits = map(Char::digitToInt)
    val firstItem = checkDigit(digits.take(12), PESOS_CNPJ_PRIMEIRO)
    val secondItem = checkDigit(digits.take(13), PESOS_CNPJ_SEGUNDO)
    return digits[12] == firstItem && digits[13] == secondItem
}

private fun checkDigit(
    digits: List<Int>,
    weights: List<Int>,
): Int {
    val remainder = digits.zip(weights).sumOf { (digit, weight) -> digit * weight } % MODULO_CPF
    return if (remainder < LIMIAR_CPF) 0 else MODULO_CPF - remainder
}

private const val TAMANHO_CPF = 11
private const val TAMANHO_CNPJ = 14
private const val MODULO_CPF = 11
private const val LIMIAR_CPF = 2
private val PESOS_CNPJ_PRIMEIRO = listOf(5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2)
private val PESOS_CNPJ_SEGUNDO = listOf(6, 5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2)
