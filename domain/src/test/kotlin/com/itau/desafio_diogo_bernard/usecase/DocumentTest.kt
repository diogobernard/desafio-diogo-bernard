package com.itau.desafio_diogo_bernard.usecase

import com.itau.desafio_diogo_bernard.model.pedido.Document
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class DocumentTest {
    @Test
    fun `mascara CPF e CNPJ`() {
        val cpf = Document("12345678909")
        val cnpj = Document("12345678000195")

        assertEquals("***.456.789-**", cpf.masked())
        assertEquals("**.345.678/0001-**", cnpj.masked())
        assertFalse(cpf.toString().contains(cpf.value))
        assertFalse(cnpj.toString().contains(cnpj.value))
        assertEquals("[DOCUMENTO_INVALIDO]", Document("123").masked())
        assertEquals("[DOCUMENTO_INVALIDO]", Document("123456789012").masked())
        assertEquals("[DOCUMENTO_INVALIDO]", Document("1234567890123").masked())
        assertTrue(cpf == Document("12345678909"))
        assertFalse(cpf == cnpj)
        val missing: Any? = null
        assertFalse(cpf.equals(missing))
        assertEquals(cpf.hashCode(), Document("12345678909").hashCode())
        assertNotEquals(cpf.hashCode(), cnpj.hashCode())
    }
}
