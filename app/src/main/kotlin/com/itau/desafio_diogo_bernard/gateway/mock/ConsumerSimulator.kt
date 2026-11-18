package com.itau.desafio_diogo_bernard.gateway.mock

import com.itau.desafio_diogo_bernard.config.properties.NotaFiscalProperties.Consumidor
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression
import org.springframework.stereotype.Component
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Semaphore
import java.util.function.DoubleSupplier
import kotlin.math.floor

interface SimulateConsumerDelivery {
    fun execute(
        consumer: String,
        settings: Consumidor,
        action: Runnable,
    ): CompletableFuture<Void>
}

@Component
@ConditionalOnExpression("'\${app.runtime.role:combined}' != 'api'")
class ConsumerSimulator(
    private val scheduler: ConsumerTaskScheduler,
    private val random: DoubleSupplier =
        DoubleSupplier { Math.random() },
) : SimulateConsumerDelivery {
    private val permits = ConcurrentHashMap<String, Semaphore>()

    override fun execute(
        consumer: String,
        settings: Consumidor,
        action: Runnable,
    ): CompletableFuture<Void> {
        val future = CompletableFuture<Void>()
        val semaphore = permits.computeIfAbsent(consumer) { Semaphore(settings.maxEmVoo) }
        if (!semaphore.tryAcquire()) return future.failed(IllegalStateException(ERRO_LIMITE))
        try {
            scheduler.schedule(delay(settings), task(future, semaphore, settings, action))
        } catch (failure: RuntimeException) {
            semaphore.release()
            future.completeExceptionally(failure)
        }
        return future
    }

    private fun delay(settings: Consumidor): Duration {
        val interval = settings.latenciaMaxima.toMillis() - settings.latenciaMinima.toMillis()
        val milliseconds = settings.latenciaMinima.toMillis() + floor(random.getAsDouble() * (interval + 1))
        return Duration.ofMillis(milliseconds.toLong())
    }

    private fun task(
        future: CompletableFuture<Void>,
        semaphore: Semaphore,
        settings: Consumidor,
        action: Runnable,
    ): Runnable =
        Runnable {
            // A permissão é liberada antes de concluir o futuro: a conclusão confirma o offset na
            // mesma thread, e o container pode buscar o próximo lote antes de um release posterior.
            val failure =
                try {
                    executeAction(settings, action)
                } finally {
                    semaphore.release()
                }
            if (failure == null) future.complete(null) else future.completeExceptionally(failure)
        }

    private fun executeAction(
        settings: Consumidor,
        action: Runnable,
    ): Exception? =
        try {
            if (random.getAsDouble() < settings.taxaFalha) error(ERRO_SIMULADO)
            action.run()
            null
        } catch (failure: Exception) {
            failure
        }

    private fun CompletableFuture<Void>.failed(failure: Exception): CompletableFuture<Void> {
        completeExceptionally(failure)
        return this
    }

    private companion object {
        const val ERRO_LIMITE = "Limite de notificações simultâneas atingido"
        const val ERRO_SIMULADO = "Falha simulada no consumidor"
    }
}
