package com.itau.desafio_diogo_bernard.entrypoint.job

import com.itau.desafio_diogo_bernard.config.properties.NotaFiscalProperties
import com.itau.desafio_diogo_bernard.gateway.postgres.OutboxPublication
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.ContextClosedEvent
import org.springframework.context.event.EventListener
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

private val logger = LoggerFactory.getLogger(OutboxPublisherJob::class.java)

@Component
@ConditionalOnExpression("'\${app.runtime.role:combined}' != 'api'")
class OutboxPublisherJob(
    private val outbox: OutboxPublication,
    private val properties: NotaFiscalProperties,
) {
    @EventListener(ApplicationReadyEvent::class)
    fun start(): Unit = logger.info("Publicador do outbox iniciado")

    @EventListener(ContextClosedEvent::class)
    fun shutdown(): Unit = logger.info("Publicador do outbox encerrado")

    @Scheduled(fixedDelayString = "\${nota-fiscal.outbox.intervalo:50ms}")
    fun publishRequests(): Unit = publish(properties.kafka.topicoSolicitacoes)

    @Scheduled(fixedDelayString = "\${nota-fiscal.outbox.intervalo:50ms}")
    fun publishInvoices(): Unit = publish(properties.kafka.topicoNotas)

    private fun publish(topic: String) {
        try {
            outbox.publishPending(topic, properties.outbox.lote)
        } catch (failure: Exception) {
            logger.warn(
                "Falha ao publicar lote do outbox; topico={}; nova tentativa no próximo ciclo: {}",
                topic,
                failure.javaClass.simpleName,
            )
        }
    }
}
