package com.itau.desafio_diogo_bernard.model.nota

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class InvoiceIdentifiersTest {
    @Test
    fun `rejeita numeros fiscais e series fora dos limites`() {
        assertEquals(3, FiscalNumber(3).value)
        assertEquals(1, Series(1).value)
        assertFailsWith<IllegalArgumentException> { FiscalNumber(0) }
        assertFailsWith<IllegalArgumentException> { FiscalNumber(1_000_000_000) }
        assertFailsWith<IllegalArgumentException> { Series(0) }
        assertFailsWith<IllegalArgumentException> { Series(1_000) }
    }
}
