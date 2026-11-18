package com.itau.desafio_diogo_bernard.entrypoint.job

import com.itau.desafio_diogo_bernard.config.properties.NotaFiscalProperties
import com.itau.desafio_diogo_bernard.repository.OutboxRepository
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Clock

@Component
@ConditionalOnExpression("'\${app.runtime.role:combined}' != 'api'")
class OutboxCleanupJob(
    private val outbox: OutboxRepository,
    private val properties: NotaFiscalProperties,
    private val clock: Clock,
) {
    @Scheduled(fixedDelayString = "\${nota-fiscal.outbox.retencao:24h}")
    @Transactional
    fun cleanUp() {
        outbox.deletePublished(clock.instant().minus(properties.outbox.retencao))
    }
}
