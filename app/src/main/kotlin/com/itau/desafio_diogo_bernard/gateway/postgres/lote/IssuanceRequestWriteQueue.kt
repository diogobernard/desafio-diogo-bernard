@file:Suppress("PackageNaming")

package com.itau.desafio_diogo_bernard.gateway.postgres.lote

import com.itau.desafio_diogo_bernard.config.IngressMetrics
import com.itau.desafio_diogo_bernard.config.properties.NotaFiscalProperties
import com.itau.desafio_diogo_bernard.exception.IngressCapacityExhaustedException
import com.itau.desafio_diogo_bernard.exception.IngressRejectionReason.ENCERRANDO
import com.itau.desafio_diogo_bernard.exception.IngressRejectionReason.FILA_CHEIA
import com.itau.desafio_diogo_bernard.repository.IssuanceRequestInsert
import org.slf4j.LoggerFactory
import org.springframework.context.SmartLifecycle
import org.springframework.stereotype.Component
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit.MILLISECONDS
import java.util.concurrent.atomic.AtomicBoolean

private const val FASE = Int.MAX_VALUE / 2
private const val SONDAGEM_MS = 50L
private val logger = LoggerFactory.getLogger(IssuanceRequestWriteQueue::class.java)

@Component
class IssuanceRequestWriteQueue(
    properties: NotaFiscalProperties,
    private val writer: BatchWriter,
    private val metrics: IngressMetrics,
) : IssuanceRequestWrite,
    SmartLifecycle {
    private val ingress = properties.ingresso
    private val queue = LinkedBlockingQueue<PendingWrite>(ingress.capacidade)
    private val accepting = AtomicBoolean()

    @Volatile
    private var threads: List<Thread> = emptyList()

    override fun persist(insert: IssuanceRequestInsert): Boolean {
        val order = PendingWrite(insert)
        if (!accepting.get() || !queue.offer(order)) {
            throw IngressCapacityExhaustedException(if (accepting.get()) FILA_CHEIA else ENCERRANDO)
        }
        return order.await(ingress.esperaMaxima)
    }

    override fun pending(): Int = queue.size

    override fun start() {
        accepting.set(true)
        threads =
            List(ingress.escritores) {
                Thread
                    .ofPlatform()
                    .name("escritor-solicitacoes-$it")
                    .daemon()
                    .start { consume() }
            }
    }

    override fun stop() {
        accepting.set(false)
        threads.forEach { it.join(ingress.esperaMaxima) }
    }

    override fun isRunning(): Boolean = accepting.get()

    override fun getPhase(): Int = FASE

    internal fun consume() {
        while (accepting.get() || queue.isNotEmpty()) {
            val firstItem = queue.poll(SONDAGEM_MS, MILLISECONDS) ?: continue
            val batch = mutableListOf(firstItem).also { queue.drainTo(it, ingress.loteMaximo - 1) }
            write(batch)
        }
    }

    private fun write(batch: List<PendingWrite>) {
        metrics.recordBatchSize(batch.size)
        runCatching {
            metrics.timeBatchWrite {
                writer.write(batch)
            }
        }.onFailure { error ->
            logger.error("Falha inesperada ao gravar lote de solicitações; tipo={}", error.javaClass.simpleName)
            batch.forEach {
                it.complete(Result.failure(error))
            }
        }
    }
}
