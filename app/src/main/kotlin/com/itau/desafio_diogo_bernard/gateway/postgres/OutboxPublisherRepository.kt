@file:Suppress("PackageNaming")

package com.itau.desafio_diogo_bernard.gateway.postgres

import com.itau.desafio_diogo_bernard.config.properties.NotaFiscalProperties
import com.itau.desafio_diogo_bernard.repository.entity.OutboxEntity
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit.MILLISECONDS
import java.util.concurrent.TimeoutException
import com.itau.desafio_diogo_bernard.repository.OutboxRepository as Eventos

private const val LIMITE_MENSAGEM = 200
private val CARACTERES_DE_CONTROLE = Regex("\\p{Cntrl}")
private val logger = LoggerFactory.getLogger(OutboxPublisherRepository::class.java)

@Component
class OutboxPublisherRepository(
    private val events: Eventos,
    private val dispatch: OutboxEventSender,
    private val properties: NotaFiscalProperties,
    private val clock: Clock,
) : OutboxPublication {
    @Transactional
    override fun publishPending(
        topic: String,
        limit: Int,
    ): Int {
        val dispatches = events.selectPendingByTopic(topic, limit).map(::send)
        if (dispatches.isEmpty()) return 0
        await(topic, dispatches)
        dispatches.filter(Dispatch::hasFailed).forEach(::recordFailure)
        val confirmed = dispatches.filter(Dispatch::isConfirmed).map { it.event.id }
        if (confirmed.isNotEmpty()) events.markPublished(confirmed.toTypedArray(), clock.instant())
        return confirmed.size
    }

    private fun send(entity: OutboxEntity): Dispatch {
        val event =
            OutboxEvent(
                entity.id,
                entity.eventId,
                entity.topic,
                entity.key,
                entity.payload,
                entity.traceparent,
            )
        return Dispatch(event, dispatch.send(event))
    }

    private fun await(
        topic: String,
        dispatches: List<Dispatch>,
    ) {
        val all = CompletableFuture.allOf(*dispatches.map { it.future }.toTypedArray()).exceptionally { null }
        try {
            all.get(properties.outbox.esperaConfirmacao.toMillis(), MILLISECONDS)
        } catch (ignored: TimeoutException) {
            logger.warn(
                "evento=falha_integracao provedor=kafka operacao=publicar_outbox topico={} tipoErro=timeout " +
                    "semConfirmacao={} esperaMs={}",
                topic,
                dispatches.count { !it.future.isDone },
                properties.outbox.esperaConfirmacao.toMillis(),
            )
        }
    }

    private fun recordFailure(dispatch: Dispatch) {
        val error = generateSequence(dispatch.future.exceptionNow()) { it.cause }.last()
        logger.warn(
            "evento=falha_integracao provedor=kafka operacao=publicar_outbox topico={} eventoId={} " +
                "tipoErro={} mensagemOriginal=\"{}\"",
            dispatch.event.topic,
            dispatch.event.eventId,
            error.javaClass.simpleName,
            safeMessage(error),
        )
    }

    private fun safeMessage(error: Throwable): String =
        error.message
            .orEmpty()
            .replace(CARACTERES_DE_CONTROLE, " ")
            .take(LIMITE_MENSAGEM)

    private class Dispatch(
        val event: OutboxEvent,
        val future: CompletableFuture<*>,
    ) {
        fun hasFailed(): Boolean = future.isCompletedExceptionally

        fun isConfirmed(): Boolean = future.isDone && !future.isCompletedExceptionally
    }
}
