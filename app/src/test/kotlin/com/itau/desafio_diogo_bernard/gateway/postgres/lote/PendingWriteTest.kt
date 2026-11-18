@file:Suppress("PackageNaming")

package com.itau.desafio_diogo_bernard.gateway.postgres.lote

import com.itau.desafio_diogo_bernard.exception.IngressCapacityExhaustedException
import com.itau.desafio_diogo_bernard.exception.IngressRejectionReason
import org.awaitility.kotlin.await
import org.junit.jupiter.api.Test
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit.SECONDS
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class PendingWriteTest {
    @Test
    fun `cancela o item e rejeita quando a espera maxima excede antes de um escritor assumi-lo`() {
        val order = umPedido()

        val error = assertFailsWith<IngressCapacityExhaustedException> { order.await(Duration.ZERO) }

        assertEquals(IngressRejectionReason.ESPERA_EXCEDIDA, error.reason)
        assertFalse(order.claim())
    }

    @Test
    fun `aguarda o resultado quando o escritor assumiu o item antes do prazo`() {
        val order = umPedido()
        assertTrue(order.claim())
        val result = CompletableFuture<Boolean>()
        val httpRequest = Thread.ofPlatform().start { result.complete(order.await(Duration.ZERO)) }
        await.atMost(TIMEOUT_TESTE, SECONDS).until { httpRequest.state == Thread.State.WAITING }

        order.complete(Result.success(true))

        assertTrue(result.get(TIMEOUT_TESTE, SECONDS))
    }

    @Test
    fun `devolve o resultado concluido dentro do prazo`() {
        val created = umPedido().apply { complete(Result.success(true)) }
        val existing = umPedido().apply { complete(Result.success(false)) }

        assertTrue(created.await(PRAZO))
        assertFalse(existing.await(PRAZO))
    }

    @Test
    fun `propaga a falha registrada pelo escritor e ignora conclusoes repetidas`() {
        val failure = IllegalStateException("falha simulada")
        val order = umPedido()
        order.complete(Result.failure(failure))
        order.complete(Result.success(true))

        val propagated = assertFailsWith<IllegalStateException> { order.await(PRAZO) }

        assertSame(failure, propagated)
    }

    private companion object {
        const val TIMEOUT_TESTE = 10L
        val PRAZO: Duration = Duration.ofSeconds(1)
    }
}
