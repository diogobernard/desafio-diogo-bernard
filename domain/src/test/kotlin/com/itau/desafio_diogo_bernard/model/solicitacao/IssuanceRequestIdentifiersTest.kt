package com.itau.desafio_diogo_bernard.model.solicitacao

import org.junit.jupiter.api.Test
import kotlin.test.assertFailsWith

class IssuanceRequestIdentifiersTest {
    @Test
    fun `rejeita chave de idempotencia vazia e motivo sem mensagem`() {
        assertFailsWith<IllegalArgumentException> { IdempotencyKey(" ") }
        assertFailsWith<IllegalArgumentException> { FailureReason("") }
    }
}
