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
import org.springframework.context.ApplicationContext
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@Tag("integration")
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = [
        "app.runtime.role=api",
        "app.datasource.write.hikari.maximum-pool-size=25",
        "app.datasource.write.hikari.minimum-idle=1",
        "app.datasource.read.hikari.maximum-pool-size=25",
        "app.datasource.read.hikari.minimum-idle=1",
    ],
)
class ApiRuntimeIntegrationTest {
    @Autowired private lateinit var context: ApplicationContext

    @Autowired
    @Qualifier("writeDataSource")
    private lateinit var writePool: HikariDataSource

    @Autowired
    @Qualifier("readDataSource")
    private lateinit var readPool: HikariDataSource

    @Test
    fun `TI-18 ativa somente componentes HTTP no papel api`() {
        assertTrue(context.getBeanNamesForType(IssuanceRequestController::class.java).isNotEmpty())
        assertTrue(context.getBeanNamesForType(InvoiceController::class.java).isNotEmpty())
        assertFalse(context.getBeanNamesForType(IssuanceRequestReceivedListener::class.java).isNotEmpty())
        assertFalse(context.getBeanNamesForType(InventoryListener::class.java).isNotEmpty())
        assertFalse(context.getBeanNamesForType(RegistryListener::class.java).isNotEmpty())
        assertFalse(context.getBeanNamesForType(DeliveryListener::class.java).isNotEmpty())
        assertFalse(context.getBeanNamesForType(FinancialListener::class.java).isNotEmpty())
        assertFalse(context.getBeanNamesForType(OutboxPublisherJob::class.java).isNotEmpty())
        assertFalse(context.getBeanNamesForType(OutboxCleanupJob::class.java).isNotEmpty())
        assertFalse(context.getBeanNamesForType(KafkaConsumersConfig::class.java).isNotEmpty())
        assertFalse(context.getBeanNamesForType(ConsumerSchedulerConfig::class.java).isNotEmpty())
        assertFalse(context.getBeanNamesForType(ConsumerTopicsConfig::class.java).isNotEmpty())
    }

    @Test
    fun `TI-19 limita pools api aos valores configurados`() {
        assertEquals(25, writePool.maximumPoolSize)
        assertEquals(25, readPool.maximumPoolSize)
        assertTrue(writePool.maximumPoolSize + WORKER_WRITE_POOL <= WRITE_BUDGET)
        assertTrue(readPool.maximumPoolSize + WORKER_READ_POOL <= READ_BUDGET)
    }

    companion object {
        private const val WORKER_WRITE_POOL = 5
        private const val WORKER_READ_POOL = 10
        private const val WRITE_BUDGET = 30
        private const val READ_BUDGET = 35

        @JvmStatic
        @DynamicPropertySource
        fun infra(registry: DynamicPropertyRegistry): Unit = PostgresIntegrationBase.database(registry)
    }
}
