@file:Suppress("PackageNaming")

package com.itau.desafio_diogo_bernard.entrypoint

import com.itau.desafio_diogo_bernard.config.ConsumerSchedulerConfig
import com.itau.desafio_diogo_bernard.config.ConsumerTopicsConfig
import com.itau.desafio_diogo_bernard.config.KafkaConsumersConfig
import com.itau.desafio_diogo_bernard.entrypoint.consumidores.DeliveryListener
import com.itau.desafio_diogo_bernard.entrypoint.consumidores.FinancialListener
import com.itau.desafio_diogo_bernard.entrypoint.consumidores.InventoryListener
import com.itau.desafio_diogo_bernard.entrypoint.consumidores.RegistryListener
import com.itau.desafio_diogo_bernard.entrypoint.job.OutboxCleanupJob
import com.itau.desafio_diogo_bernard.entrypoint.job.OutboxPublisherJob
import com.itau.desafio_diogo_bernard.entrypoint.notas.InvoiceController
import com.itau.desafio_diogo_bernard.entrypoint.processamento.IssuanceRequestReceivedListener
import com.itau.desafio_diogo_bernard.entrypoint.solicitacoes.IssuanceRequestController
import com.zaxxer.hikari.HikariDataSource
import org.junit.jupiter.api.Tag
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.web.server.context.WebServerApplicationContext
import org.springframework.context.ApplicationContext
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@Tag("integration")
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    properties = [
        "app.runtime.role=worker",
        "spring.main.web-application-type=none",
        "app.datasource.write.hikari.maximum-pool-size=5",
        "app.datasource.write.hikari.minimum-idle=1",
        "app.datasource.read.hikari.maximum-pool-size=10",
        "app.datasource.read.hikari.minimum-idle=1",
        "nota-fiscal.kafka.topico-solicitacoes=teste.worker.solicitacoes.v1",
        "nota-fiscal.kafka.topico-solicitacoes-dlq=teste.worker.solicitacoes.v1.dlq",
        "nota-fiscal.kafka.topico-notas=teste.worker.notas.v1",
    ],
)
class WorkerRuntimeIntegrationTest {
    @Autowired private lateinit var context: ApplicationContext

    @Autowired
    @Qualifier("writeDataSource")
    private lateinit var writePool: HikariDataSource

    @Autowired
    @Qualifier("readDataSource")
    private lateinit var readPool: HikariDataSource

    @Test
    fun `TI-18 ativa somente listeners e jobs no papel worker`() {
        assertFalse(context is WebServerApplicationContext)
        assertFalse(context.getBeanNamesForType(IssuanceRequestController::class.java).isNotEmpty())
        assertFalse(context.getBeanNamesForType(InvoiceController::class.java).isNotEmpty())
        assertTrue(context.getBeanNamesForType(IssuanceRequestReceivedListener::class.java).isNotEmpty())
        assertTrue(context.getBeanNamesForType(InventoryListener::class.java).isNotEmpty())
        assertTrue(context.getBeanNamesForType(RegistryListener::class.java).isNotEmpty())
        assertTrue(context.getBeanNamesForType(DeliveryListener::class.java).isNotEmpty())
        assertTrue(context.getBeanNamesForType(FinancialListener::class.java).isNotEmpty())
        assertTrue(context.getBeanNamesForType(OutboxPublisherJob::class.java).isNotEmpty())
        assertTrue(context.getBeanNamesForType(OutboxCleanupJob::class.java).isNotEmpty())
        assertTrue(context.getBeanNamesForType(KafkaConsumersConfig::class.java).isNotEmpty())
        assertTrue(context.getBeanNamesForType(ConsumerSchedulerConfig::class.java).isNotEmpty())
        assertTrue(context.getBeanNamesForType(ConsumerTopicsConfig::class.java).isNotEmpty())
    }

    @Test
    fun `TI-19 limita pools worker dentro do orcamento`() {
        assertEquals(5, writePool.maximumPoolSize)
        assertEquals(10, readPool.maximumPoolSize)
        assertTrue(writePool.maximumPoolSize + API_WRITE_POOL <= WRITE_BUDGET)
        assertTrue(readPool.maximumPoolSize + API_READ_POOL <= READ_BUDGET)
    }

    companion object {
        private const val API_WRITE_POOL = 25
        private const val API_READ_POOL = 25
        private const val WRITE_BUDGET = 30
        private const val READ_BUDGET = 35

        @JvmStatic
        @DynamicPropertySource
        fun infra(registry: DynamicPropertyRegistry): Unit = PostgresIntegrationBase.database(registry)
    }
}
