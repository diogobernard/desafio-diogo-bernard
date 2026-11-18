package com.itau.desafio_diogo_bernard.entrypoint.processamento

import com.itau.desafio_diogo_bernard.config.BusinessMetrics
import com.itau.desafio_diogo_bernard.config.properties.NotaFiscalProperties
import com.itau.desafio_diogo_bernard.entrypoint.model.IssuanceRequestReceivedEvent
import com.itau.desafio_diogo_bernard.exception.NumberingExhaustedException
import com.itau.desafio_diogo_bernard.model.solicitacao.FailureReason
import com.itau.desafio_diogo_bernard.model.solicitacao.IssuanceRequestId
import com.itau.desafio_diogo_bernard.usecase.processamento.RecordIssuanceRequestFailure
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.producer.ProducerRecord
import org.slf4j.LoggerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.listener.ConsumerRecordRecoverer
import org.springframework.stereotype.Component
import tools.jackson.databind.json.JsonMapper

@Component
class ProcessingFailureRecoverer(
    private val recordRequestFailure: RecordIssuanceRequestFailure,
    private val json: JsonMapper,
    private val kafka: KafkaTemplate<String, String>,
    private val properties: NotaFiscalProperties,
    private val metrics: BusinessMetrics,
) : ConsumerRecordRecoverer {
    override fun accept(
        record: ConsumerRecord<*, *>,
        exception: Exception,
    ) {
        val event = json.readValue(record.value().toString(), IssuanceRequestReceivedEvent::class.java)
        publishToDlq(record)
        metrics.dlq("processamento")
        recordFailure(event.requestId, exception)
        logger.error("Solicitação esgotou as tentativas do worker e foi enviada à DLQ; eventoId={}", event.eventId)
    }

    private fun recordFailure(
        id: String,
        error: Exception,
    ) {
        val requestId = IssuanceRequestId(id)
        val exhausted = error.causeChain().filterIsInstance<NumberingExhaustedException>().firstOrNull()
        if (exhausted != null) {
            recordRequestFailure.execute(requestId, exhausted)
            return
        }
        recordRequestFailure.execute(requestId, FailureReason(MOTIVO_PROCESSAMENTO))
    }

    private fun publishToDlq(record: ConsumerRecord<*, *>) {
        val destination =
            ProducerRecord(
                properties.kafka.topicoSolicitacoesDlq,
                record.partition(),
                record.key().toString(),
                record.value().toString(),
            )
        record.headers().forEach { destination.headers().add(it) }
        kafka.send(destination).get()
    }

    private fun Throwable.causeChain(): Sequence<Throwable> =
        sequence {
            var current: Throwable? = this@causeChain
            while (current != null) {
                val cause = current
                yield(cause)
                current = cause.cause
            }
        }

    private companion object {
        const val MOTIVO_PROCESSAMENTO =
            "Não foi possível processar a solicitação após as tentativas configuradas."
        val logger = LoggerFactory.getLogger(ProcessingFailureRecoverer::class.java)
    }
}
