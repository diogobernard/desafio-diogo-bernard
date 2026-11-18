@file:Suppress("PackageNaming")

package com.itau.desafio_diogo_bernard.gateway.postgres

import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator
import io.opentelemetry.context.propagation.ContextPropagators
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.KafkaException
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import org.mockito.ArgumentCaptor
import org.mockito.Mockito
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.support.SendResult
import java.util.UUID
import java.util.concurrent.CompletableFuture
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import org.springframework.kafka.KafkaException as SpringKafkaException

private typealias KafkaRecord = ProducerRecord<String, String>

class OutboxEventSenderTest {
    private val kafka = Mockito.mock(KafkaTemplate::class.java) as KafkaTemplate<String, String>
    private val propagators = ContextPropagators.create(W3CTraceContextPropagator.getInstance())
    private val dispatch = OutboxEventSender(kafka, OpenTelemetry.propagating(propagators))

    @Test
    fun `envia com cabecalhos e contexto restaurado do traceparent`() {
        val traceId = "1234567890abcdef1234567890abcdef"
        val event = event(traceparent = "00-$traceId-1234567890abcdef-01")
        val future = CompletableFuture.completedFuture(Mockito.mock(SendResult::class.java))
        var traceIdOnSend: String? = null
        Mockito.`when`(kafka.send(Mockito.any<ProducerRecord<String, String>>() ?: registroVazio())).thenAnswer {
            traceIdOnSend = Span.current().spanContext.traceId
            future
        }

        val result = dispatch.send(event)

        assertSame(future, result)
        assertEquals(traceId, traceIdOnSend)
        val producerRecord = registroEnviado()
        assertEquals(event.topic, producerRecord.topic())
        assertEquals(event.key, producerRecord.key())
        assertEquals(event.payload, producerRecord.value())
        assertEquals(event.traceparent, String(producerRecord.headers().lastHeader("traceparent").value()))
        assertEquals(event.key, String(producerRecord.headers().lastHeader("solicitacao-id").value()))
    }

    @Test
    fun `envia sem traceparent quando o evento nao tem contexto`() {
        Mockito
            .`when`(kafka.send(Mockito.any<ProducerRecord<String, String>>() ?: registroVazio()))
            .thenReturn(CompletableFuture())

        dispatch.send(event(traceparent = null))

        assertNull(registroEnviado().headers().lastHeader("traceparent"))
    }

    @ParameterizedTest
    @MethodSource("falhasSincronas")
    fun `converte falha sincrona do produtor em futuro com falha`(failure: Exception) {
        Mockito.`when`(kafka.send(Mockito.any<ProducerRecord<String, String>>() ?: registroVazio())).thenThrow(failure)

        val result = dispatch.send(event(traceparent = null))

        assertTrue(result.isCompletedExceptionally)
        assertSame(failure, result.exceptionNow())
    }

    companion object {
        @JvmStatic
        fun falhasSincronas(): List<Exception> =
            listOf(
                IllegalStateException("produtor fechado"),
                KafkaException("produtor fechado durante o envio"),
                SpringKafkaException("Send failed"),
            )
    }

    @Suppress("UNCHECKED_CAST")
    private fun registroEnviado(): KafkaRecord {
        val captor = ArgumentCaptor.forClass(ProducerRecord::class.java) as ArgumentCaptor<KafkaRecord>
        Mockito.verify(kafka).send(captor.capture() ?: registroVazio())
        return captor.value
    }

    private fun registroVazio(): KafkaRecord = KafkaRecord("topico", "chave", "valor")

    private fun event(traceparent: String?): OutboxEvent {
        val identifier = UUID.randomUUID()
        return OutboxEvent(1, identifier, "topico.teste", "chave-1", """{"x":1}""", traceparent)
    }
}
