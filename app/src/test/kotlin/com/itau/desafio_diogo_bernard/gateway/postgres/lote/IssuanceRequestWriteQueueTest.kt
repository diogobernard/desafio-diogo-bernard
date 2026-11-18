@file:Suppress("PackageNaming")

package com.itau.desafio_diogo_bernard.gateway.postgres.lote

import com.itau.desafio_diogo_bernard.config.IngressMetrics
import com.itau.desafio_diogo_bernard.config.properties.NotaFiscalProperties
import com.itau.desafio_diogo_bernard.exception.IngressCapacityExhaustedException
import com.itau.desafio_diogo_bernard.exception.IngressRejectionReason.ENCERRANDO
import com.itau.desafio_diogo_bernard.exception.IngressRejectionReason.ESPERA_EXCEDIDA
import com.itau.desafio_diogo_bernard.exception.IngressRejectionReason.FILA_CHEIA
import com.itau.desafio_diogo_bernard.repository.IssuanceRequestInsert
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.awaitility.kotlin.await
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.context.SmartLifecycle
import java.time.Duration
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit.SECONDS
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class IssuanceRequestWriteQueueTest {
    private val registry = SimpleMeterRegistry()
    private val executor = Executors.newCachedThreadPool()
    private val writer = ControlledWriter()
    private val queues = mutableListOf<IssuanceRequestWriteQueue>()

    @AfterEach
    fun shutdown() {
        writer.abrir()
        queues.forEach { it.stop() }
        executor.shutdownNow()
    }

    @Test
    fun `rejeita sem esperar quando a fila de gravacao esta cheia`() {
        val queue = start(capacity = 1)
        val first = gravarEmSegundoPlano(queue)
        assertTrue(writer.entered.await(TIMEOUT_TESTE, SECONDS))
        val second = gravarEmSegundoPlano(queue)
        await.atMost(TIMEOUT_TESTE, SECONDS).until { queue.pending() == 1 }

        val error = assertFailsWith<IngressCapacityExhaustedException> { queue.persist(umaInsercao()) }

        assertEquals(FILA_CHEIA, error.reason)
        writer.abrir()
        assertTrue(first.get(TIMEOUT_TESTE, SECONDS))
        assertTrue(second.get(TIMEOUT_TESTE, SECONDS))
    }

    @Test
    fun `agrupa os itens enfileirados em lotes de no maximo o tamanho configurado`() {
        val queue = start(maxBatchSize = 2)
        val tasks = mutableListOf(gravarEmSegundoPlano(queue))
        assertTrue(writer.entered.await(TIMEOUT_TESTE, SECONDS))
        repeat(3) { tasks.add(gravarEmSegundoPlano(queue)) }
        await.atMost(TIMEOUT_TESTE, SECONDS).until { queue.pending() == 3 }

        writer.abrir()

        tasks.forEach { assertTrue(it.get(TIMEOUT_TESTE, SECONDS)) }
        val sizes = registry.find("notafiscal.ingresso.lote.tamanho").summary()
        assertEquals(listOf(1, 2, 1), writer.batches.map { it.size })
        assertEquals(3L, sizes?.count())
        assertEquals(2.0, sizes?.max())
    }

    @Test
    fun `rejeita por espera excedida e nao grava o item cancelado`() {
        val queue = start(wait = Duration.ofMillis(50))
        val first = gravarEmSegundoPlano(queue)
        assertTrue(writer.entered.await(TIMEOUT_TESTE, SECONDS))

        val error = assertFailsWith<IngressCapacityExhaustedException> { queue.persist(umaInsercao()) }

        assertEquals(ESPERA_EXCEDIDA, error.reason)
        writer.abrir()
        assertTrue(first.get(TIMEOUT_TESTE, SECONDS))
        await.atMost(TIMEOUT_TESTE, SECONDS).until { queue.pending() == 0 }
        assertEquals(1, writer.batches.sumOf { it.size })
    }

    @Test
    fun `descarrega a fila no encerramento e rejeita novas requisicoes`() {
        val queue = start()
        val first = gravarEmSegundoPlano(queue)
        assertTrue(writer.entered.await(TIMEOUT_TESTE, SECONDS))
        val second = gravarEmSegundoPlano(queue)
        await.atMost(TIMEOUT_TESTE, SECONDS).until { queue.pending() == 1 }
        val closing = executor.submit { queue.stop() }
        await.atMost(TIMEOUT_TESTE, SECONDS).until { !queue.isRunning }

        val error = assertFailsWith<IngressCapacityExhaustedException> { queue.persist(umaInsercao()) }

        assertEquals(ENCERRANDO, error.reason)
        writer.abrir()
        closing.get(TIMEOUT_TESTE, SECONDS)
        assertTrue(first.get(TIMEOUT_TESTE, SECONDS))
        assertTrue(second.get(TIMEOUT_TESTE, SECONDS))
        assertEquals(2, writer.batches.sumOf { it.size })
    }

    @Test
    fun `rejeita como encerrando quando a fila ainda nao foi iniciada`() {
        val queue = IssuanceRequestWriteQueue(NotaFiscalProperties(), writer, IngressMetrics(registry))

        val error = assertFailsWith<IngressCapacityExhaustedException> { queue.persist(umaInsercao()) }

        assertEquals(ENCERRANDO, error.reason)
        assertFalse(queue.isRunning)
        queue.stop()
    }

    @Test
    fun `escritor ocioso aguarda novos itens ate o encerramento`() {
        val queue = start()
        await.atMost(TIMEOUT_TESTE, SECONDS).until { escritoresOciosos() }

        queue.stop()

        assertFalse(escritoresVivos())
    }

    @Test
    fun `falha o lote e mantem o escritor ativo quando ocorre excecao inesperada`() {
        val failure = IllegalStateException("falha inesperada")
        val queue = start(used = FailingWriter(failure))

        val first = assertFailsWith<IllegalStateException> { queue.persist(umaInsercao()) }
        val second = assertFailsWith<IllegalStateException> { queue.persist(umaInsercao()) }

        assertSame(failure, first)
        assertSame(failure, second)
    }

    @Test
    fun `executa a parada depois do encerramento gracioso do servidor web`() {
        val queue = start()

        assertTrue(queue.phase < SmartLifecycle.DEFAULT_PHASE - FASE_SERVIDOR_WEB)
        assertTrue(queue.isRunning)
    }

    private fun start(
        capacity: Int = CAPACIDADE_TESTE,
        wait: Duration = Duration.ofSeconds(TIMEOUT_TESTE),
        maxBatchSize: Int = LOTE_TESTE,
        used: BatchWriter = writer,
    ): IssuanceRequestWriteQueue {
        val ingress = NotaFiscalProperties.Ingresso(capacity, wait, maxBatchSize, escritores = 1)
        return IssuanceRequestWriteQueue(NotaFiscalProperties(ingresso = ingress), used, IngressMetrics(registry))
            .also {
                queues.add(it)
                it.start()
            }
    }

    private fun gravarEmSegundoPlano(queue: IssuanceRequestWriteQueue): Future<Boolean> =
        executor.submit<Boolean> { queue.persist(umaInsercao()) }

    private fun escritoresOciosos(): Boolean = threadsDeEscritor().any { it.state == Thread.State.TIMED_WAITING }

    private fun escritoresVivos(): Boolean = threadsDeEscritor().any { it.isAlive }

    private fun threadsDeEscritor(): List<Thread> =
        Thread
            .getAllStackTraces()
            .keys
            .filter { it.name.startsWith(PREFIXO_ESCRITOR) }

    private class FailingWriter(
        private val failure: IllegalStateException,
    ) : BatchWriter {
        override fun write(orders: List<PendingWrite>) {
            orders.forEach { it.claim() }
            throw failure
        }
    }

    private class ControlledWriter : BatchWriter {
        val entered = CountDownLatch(1)
        val batches = CopyOnWriteArrayList<List<IssuanceRequestInsert>>()
        private val release = CountDownLatch(1)

        fun abrir(): Unit = release.countDown()

        override fun write(orders: List<PendingWrite>) {
            val claimed = orders.filter(PendingWrite::claim)
            entered.countDown()
            release.await(TIMEOUT_TESTE, SECONDS)
            batches.add(claimed.map { it.insert })
            claimed.forEach { it.complete(Result.success(true)) }
        }
    }

    private companion object {
        const val TIMEOUT_TESTE = 10L
        const val CAPACIDADE_TESTE = 10
        const val LOTE_TESTE = 100
        const val FASE_SERVIDOR_WEB = 1024
        const val PREFIXO_ESCRITOR = "escritor-solicitacoes-"
    }
}
