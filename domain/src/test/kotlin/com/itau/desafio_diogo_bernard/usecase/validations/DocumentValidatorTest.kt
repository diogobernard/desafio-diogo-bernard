package com.itau.desafio_diogo_bernard.usecase.validations

import com.itau.desafio_diogo_bernard.model.pedido.PersonType
import org.junit.jupiter.api.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DocumentValidatorTest {
    @Test
    fun `valida digitos verificadores de CPF e CNPJ`() {
        assertTrue("12345678909".hasValidFormat(PersonType.FISICA))
        assertTrue("12345678000195".hasValidFormat(PersonType.JURIDICA))
        assertFalse("11111111111".hasValidFormat(PersonType.FISICA))
        assertFalse("12345678900".hasValidFormat(PersonType.FISICA))
        assertFalse("12345678919".hasValidFormat(PersonType.FISICA))
        assertFalse("123".hasValidFormat(PersonType.FISICA))
        assertFalse("12345678000100".hasValidFormat(PersonType.JURIDICA))
        assertFalse("12345678000194".hasValidFormat(PersonType.JURIDICA))
        assertFalse("123".hasValidFormat(PersonType.JURIDICA))
        assertFalse("1234567890a".hasValidFormat(PersonType.FISICA))
        assertFalse("00000000000".hasValidFormat(PersonType.FISICA))
        assertFalse("00000000000000".hasValidFormat(PersonType.JURIDICA))
        assertTrue("00000000191".hasValidFormat(PersonType.FISICA))
        assertTrue("00000000000191".hasValidFormat(PersonType.JURIDICA))
    }
}
