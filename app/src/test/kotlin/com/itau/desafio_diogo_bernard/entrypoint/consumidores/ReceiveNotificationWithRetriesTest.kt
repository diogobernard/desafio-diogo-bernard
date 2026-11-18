package com.itau.desafio_diogo_bernard.entrypoint.consumidores

import com.itau.desafio_diogo_bernard.config.properties.NotaFiscalProperties.Consumidor
import com.itau.desafio_diogo_bernard.entrypoint.consumidores.RegisterNotificationFake.Companion.failure
import com.itau.desafio_diogo_bernard.entrypoint.consumidores.RegisterNotificationFake.Companion.lanca
import com.itau.desafio_diogo_bernard.entrypoint.consumidores.RegisterNotificationFake.Companion.success
import com.itau.desafio_diogo_bernard.gateway.mock.ConsumerTaskSchedulerFake
import org.apache.kafka.clients.consumer.ConsumerRecord
import java.time.Duration
import java.util.concurrent.CompletableFuture
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReceiveNotificationWithRetriesTest {
    private val record = ConsumerRecord("notafiscal.notas.v1", 2, 9L, "chave", "{}")
    private val scheduler = ConsumerTaskSchedulerFake()
    private val dlq = DlqForwarderFake()

    @Test
    fun `conclui sem retry quando a primeira tentativa registra a notificacao`() {
        val result = receive(RegisterNotificationFake(success()))

        assertSucesso(result)
        assertTrue(scheduler.tasks.isEmpty())
        assertTrue(dlq.forwarded.isEmpty())
    }

    @Test
    fun `repete com backoff e conclui quando a nova tentativa registra`() {
        val result = receive(RegisterNotificationFake(failure(IllegalStateException()), success()))

        assertFalse(result.isDone)
        scheduler.tasks.single().run()

        assertEquals(Duration.ofMillis(200), scheduler.delay)
        assertSucesso(result)
        assertTrue(dlq.forwarded.isEmpty())
    }

    @Test
    fun `encaminha para a DLQ do consumidor depois de esgotar as tentativas`() {
        val registerNotification = RegisterNotificationFake(failure(IllegalStateException()))
        val result = receive(registerNotification)

        scheduler.tasks.single().run()

        assertEquals(2, registerNotification.attempts)
        assertEquals(listOf<Pair<String, ConsumerRecord<*, *>>>("financeiro" to record), dlq.forwarded)
        assertSucesso(result)
    }

    @Test
    fun `envia direto para a DLQ uma falha sincrona nao repetivel`() {
        val registerNotification = RegisterNotificationFake(lanca(IllegalArgumentException("evento inválido")))

        val result = receive(registerNotification)

        assertTrue(scheduler.tasks.isEmpty())
        assertEquals(1, dlq.forwarded.size)
        assertSucesso(result)
    }

    @Test
    fun `repete a publicacao na DLQ ate o Kafka aceitar`() {
        val dlqUnavailable = DlqForwarderFake(failuresBeforeAccepting = 1)
        val result = receive(RegisterNotificationFake(failure(IllegalArgumentException())), dlq = dlqUnavailable)

        assertFalse(result.isDone)
        scheduler.tasks.single().run()

        assertEquals(Duration.ofSeconds(1), scheduler.delay)
        assertEquals(1, dlqUnavailable.forwarded.size)
        assertSucesso(result)
    }

    @Test
    fun `falha a entrega quando o agendamento da nova tentativa e rejeitado`() {
        val schedulerShutDown = ConsumerTaskSchedulerFake(failOnce = true)

        val result = receive(RegisterNotificationFake(failure(IllegalStateException())), schedulerShutDown)

        assertTrue(result.isCompletedExceptionally)
    }

    private fun receive(
        registerNotification: RegisterNotification,
        taskScheduler: ConsumerTaskSchedulerFake = scheduler,
        dlq: DlqForwarder = this.dlq,
    ): CompletableFuture<Void> =
        ReceiveNotificationWithRetries(registerNotification, taskScheduler, dlq)
            .receive("financeiro", Consumidor(tentativas = 2), record)

    private fun assertSucesso(result: CompletableFuture<Void>) {
        assertTrue(result.isDone)
        assertFalse(result.isCompletedExceptionally)
    }
}
