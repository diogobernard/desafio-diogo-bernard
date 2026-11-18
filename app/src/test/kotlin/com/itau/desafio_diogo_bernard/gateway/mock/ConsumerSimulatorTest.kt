package com.itau.desafio_diogo_bernard.gateway.mock

import com.itau.desafio_diogo_bernard.config.properties.NotaFiscalProperties.Consumidor
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.function.DoubleSupplier
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ConsumerSimulatorTest {
    @Test
    fun `simulador de consumidor conclui apos latencia sem bloquear`() {
        val scheduler = ConsumerTaskSchedulerFake()
        var ran = false
        val simulator = ConsumerSimulator(scheduler, DoubleSupplier { 0.5 })
        val settings = Consumidor(Duration.ofMillis(100), Duration.ofMillis(200))

        val future = simulator.execute("estoque", settings) { ran = true }

        assertFalse(future.isDone)
        assertFalse(ran)
        assertEquals(Duration.ofMillis(150), scheduler.delay)
        scheduler.tasks.single().run()
        assertTrue(future.isDone)
        assertTrue(ran)
    }

    @Test
    fun `limita entregas simultaneas por consumidor`() {
        val scheduler = ConsumerTaskSchedulerFake()
        val simulator = ConsumerSimulator(scheduler, DoubleSupplier { 0.5 })
        val settings = Consumidor(Duration.ZERO, Duration.ZERO, maxEmVoo = 1)

        val first = simulator.execute("estoque", settings) {}
        val second = simulator.execute("estoque", settings) {}

        assertFalse(first.isDone)
        assertTrue(second.isCompletedExceptionally)
        scheduler.tasks.single().run()
    }

    @Test
    fun `libera capacidade antes de concluir a notificacao`() {
        val scheduler = ConsumerTaskSchedulerFake()
        val simulator = ConsumerSimulator(scheduler, DoubleSupplier { 0.5 })
        val settings = Consumidor(Duration.ZERO, Duration.ZERO, maxEmVoo = 1)
        val first = simulator.execute("estoque", settings) {}
        var next: CompletableFuture<Void>? = null
        first.thenRun { next = simulator.execute("estoque", settings) {} }

        scheduler.tasks.single().run()

        assertTrue(first.isDone)
        assertEquals(false, next?.isCompletedExceptionally)
    }

    @Test
    fun `conclui com falha e libera capacidade quando a acao falha`() {
        val scheduler = ConsumerTaskSchedulerFake()
        val simulator = ConsumerSimulator(scheduler, DoubleSupplier { 0.5 })
        val settings = Consumidor(Duration.ZERO, Duration.ZERO, maxEmVoo = 1)
        val failure = simulator.execute("estoque", settings) { error("falha de registro") }

        scheduler.tasks.single().run()
        val next = simulator.execute("estoque", settings) {}

        assertTrue(failure.isCompletedExceptionally)
        assertFalse(next.isDone)
    }

    @Test
    fun `libera capacidade quando agendamento falha`() {
        val scheduler = ConsumerTaskSchedulerFake(failOnce = true)
        val simulator = ConsumerSimulator(scheduler, DoubleSupplier { 0.5 })
        val settings = Consumidor(Duration.ZERO, Duration.ZERO, maxEmVoo = 1)

        val failure = simulator.execute("estoque", settings) {}
        val success = simulator.execute("estoque", settings) {}

        assertTrue(failure.isCompletedExceptionally)
        assertFalse(success.isDone)
        scheduler.tasks.single().run()
        assertTrue(success.isDone)
    }
}
