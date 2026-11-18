package com.itau.desafio_diogo_bernard.entrypoint.consumidores

import java.util.concurrent.CompletionException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ConsumerFailureTest {
    @Test
    fun `remove o envelope de CompletionException quando ha causa`() {
        val cause = IllegalStateException("falha")
        val withoutCause = CompletionException(null as Throwable?)

        assertSame(cause, CompletionException(cause).unwrapped())
        assertSame(withoutCause, withoutCause.unwrapped())
        assertSame(cause, cause.unwrapped())
    }

    @Test
    fun `considera nao repetivel um IllegalArgumentException em qualquer nivel da causa`() {
        assertTrue(IllegalStateException(IllegalArgumentException("evento inválido")).isNonRetryable())
        assertFalse(IllegalStateException("banco indisponível").isNonRetryable())
    }

    @Test
    fun `nomeia a falha sem expor a mensagem`() {
        assertEquals("IllegalStateException", IllegalStateException("dado sensível").failureName())
        assertEquals("erro desconhecido", object : Exception() {}.failureName())
        assertEquals("erro desconhecido", (null as Throwable?).failureName())
    }
}
