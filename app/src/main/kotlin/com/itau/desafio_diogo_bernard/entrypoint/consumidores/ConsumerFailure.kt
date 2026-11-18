package com.itau.desafio_diogo_bernard.entrypoint.consumidores

import org.apache.kafka.clients.consumer.ConsumerRecord
import org.slf4j.LoggerFactory
import java.util.concurrent.CompletionException

private const val ERRO_DESCONHECIDO = "erro desconhecido"
private const val MENSAGEM_RETRY =
    "Falha no consumidor; nova tentativa: grupo={} topic={} part={} offset={} tentativa={} erro={}"
private const val MENSAGEM_DLQ =
    "Notificação enviada para DLQ: consumidor={} topic={} part={} offset={} erro={}"
private const val MENSAGEM_DLQ_FALHOU =
    "Falha ao publicar na DLQ; nova tentativa: consumidor={} topic={} part={} offset={} erro={}"
private val logger = LoggerFactory.getLogger("com.itau.desafio_diogo_bernard.entrypoint.consumidores.ConsumerFailure")

internal fun Throwable.unwrapped(): Throwable = if (this is CompletionException) cause ?: this else this

internal fun Throwable.isNonRetryable(): Boolean {
    val causes = generateSequence(this) { it.cause }
    return causes.any { it is IllegalArgumentException }
}

internal fun Throwable?.failureName(): String {
    if (this == null) return ERRO_DESCONHECIDO
    return javaClass.simpleName.ifEmpty { ERRO_DESCONHECIDO }
}

internal fun ConsumerRecord<*, *>.recordRetry(
    consumer: String,
    attempt: Int,
    failure: Throwable?,
): Unit = logger.warn(MENSAGEM_RETRY, consumer, topic(), partition(), offset(), attempt, failure.failureName())

internal fun ConsumerRecord<*, *>.recordDlqFailure(
    consumer: String,
    failure: Throwable,
): Unit = logger.warn(MENSAGEM_DLQ_FALHOU, consumer, topic(), partition(), offset(), failure.failureName())

internal fun ConsumerRecord<*, *>.recordSentToDlq(
    consumer: String,
    failure: Throwable,
): Unit = logger.error(MENSAGEM_DLQ, consumer, topic(), partition(), offset(), failure.failureName())
