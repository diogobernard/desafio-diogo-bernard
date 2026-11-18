package com.itau.desafio_diogo_bernard.entrypoint

import com.itau.desafio_diogo_bernard.config.properties.NotaFiscalProperties
import io.micrometer.core.instrument.MeterRegistry
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@Tag("integration")
class DatabaseMetricsIntegrationTest : PostgresIntegrationBase() {
    @Autowired
    private lateinit var registry: MeterRegistry

    @Autowired
    private lateinit var properties: NotaFiscalProperties

    @Test
    fun `mede outbox pendente por topico e atraso da replica`() {
        val topics = listOf(properties.kafka.topicoSolicitacoes, properties.kafka.topicoNotas)
        val pending =
            topics.map {
                registry
                    .get("notafiscal.outbox.pendentes")
                    .tag("topico", it)
                    .gauge()
                    .value()
            }
        val delay = registry.get("notafiscal.replica.lag").gauge().value()

        assertEquals(listOf(0.0, 0.0), pending)
        assertTrue(delay >= 0.0)
    }
}
