@file:Suppress("PackageNaming")

package com.itau.desafio_diogo_bernard.gateway.postgres

import com.itau.desafio_diogo_bernard.config.properties.NotaFiscalProperties
import com.itau.desafio_diogo_bernard.repository.OutboxRepository
import com.itau.desafio_diogo_bernard.repository.entity.OutboxEntity
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mockito
import org.springframework.boot.test.system.CapturedOutput
import org.springframework.boot.test.system.OutputCaptureExtension
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import java.util.concurrent.CompletableFuture
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private val MENSAGEM_VAZIA = Regex("mensagemOriginal=\\\\?\"\\\\?\"")

@ExtendWith(OutputCaptureExtension::class)
class OutboxPublisherRepositoryTest {
    private val now = Instant.parse("2026-11-16T12:00:00Z")
    private val events = Mockito.mock(OutboxRepository::class.java)
    private val dispatch = Mockito.mock(OutboxEventSender::class.java)
    private val publisher = publisher(Duration.ofSeconds(5))

    @Test
    fun `retorna zero e nao marca nada quando nao ha pendentes`() {
        Mockito.`when`(events.selectPendingByTopic(TOPICO_TESTE, 10)).thenReturn(emptyList())

        assertEquals(0, publisher.publishPending(TOPICO_TESTE, 10))

        Mockito.verify(events, Mockito.never()).markPublished(arrayOf(), now)
    }

    @Test
    fun `marca todos os confirmados com uma unica atualizacao`() {
        val entities = listOf(entidadeOutbox(1, now), entidadeOutbox(2, now))
        Mockito.`when`(events.selectPendingByTopic(TOPICO_TESTE, 10)).thenReturn(entities)
        entities.forEach { send(it, CompletableFuture.completedFuture("ok")) }

        assertEquals(2, publisher.publishPending(TOPICO_TESTE, 10))

        Mockito.verify(events, Mockito.times(1)).markPublished(arrayOf(1L, 2L), now)
    }

    @Test
    fun `mantem pendentes o recusado e o sem confirmacao e registra aviso sanitizado`(output: CapturedOutput) {
        val entities = listOf(entidadeOutbox(1, now), entidadeOutbox(2, now), entidadeOutbox(3, now))
        Mockito.`when`(events.selectPendingByTopic(TOPICO_TESTE, 10)).thenReturn(entities)
        send(entities[0], CompletableFuture.completedFuture("ok"))
        send(
            entities[1],
            CompletableFuture.failedFuture<String>(rejectedFuture(IllegalStateException("linha1\nlinha2"))),
        )
        send(entities[2], CompletableFuture<String>())

        val published = publisher(Duration.ofMillis(50)).publishPending(TOPICO_TESTE, 10)

        assertEquals(1, published)
        Mockito.verify(events).markPublished(arrayOf(1L), now)
        assertTrue(output.all.contains("eventoId=${entities[1].eventId}"))
        assertTrue(output.all.contains("tipoErro=IllegalStateException"))
        assertTrue(output.all.contains("linha1 linha2"))
        assertTrue(output.all.contains("tipoErro=timeout semConfirmacao=1 esperaMs=50"))
        assertFalse(output.all.contains(PAYLOAD_TESTE))
    }

    @Test
    fun `nao marca nada quando todos os envios falham`(output: CapturedOutput) {
        val entities = listOf(entidadeOutbox(1, now))
        Mockito.`when`(events.selectPendingByTopic(TOPICO_TESTE, 10)).thenReturn(entities)
        send(entities[0], CompletableFuture.failedFuture<String>(IllegalStateException()))

        assertEquals(0, publisher.publishPending(TOPICO_TESTE, 10))

        Mockito.verify(events, Mockito.never()).markPublished(arrayOf(1L), now)
        assertTrue(MENSAGEM_VAZIA.containsMatchIn(output.all))
    }

    private fun rejectedFuture(cause: Throwable): RuntimeException = RuntimeException("Failed to send", cause)

    private fun publisher(wait: Duration): OutboxPublisherRepository =
        OutboxPublisherRepository(
            events,
            dispatch,
            NotaFiscalProperties(outbox = NotaFiscalProperties.Outbox(esperaConfirmacao = wait)),
            Clock.fixed(now, ZoneOffset.UTC),
        )

    private fun send(
        entity: OutboxEntity,
        future: CompletableFuture<*>,
    ) {
        val event = entity.paraEvento()
        Mockito.`when`(dispatch.send(event)).thenReturn(future)
    }
}
