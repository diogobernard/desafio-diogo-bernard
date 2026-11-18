package com.itau.desafio_diogo_bernard.config

import com.itau.desafio_diogo_bernard.config.properties.NotaFiscalProperties
import com.itau.desafio_diogo_bernard.config.properties.NotaFiscalProperties.Consumidor
import com.itau.desafio_diogo_bernard.config.properties.NotaFiscalProperties.Consumidores
import com.itau.desafio_diogo_bernard.entrypoint.consumidores.DlqForwarderFake
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.mockito.Mockito
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory
import org.springframework.kafka.core.ConsumerFactory
import kotlin.test.Test
import kotlin.test.assertEquals

class KafkaConsumersConfigTest {
    private val config = KafkaConsumersConfig(handlers())

    @Suppress("UNCHECKED_CAST")
    private val consumerFactory = Mockito.mock(ConsumerFactory::class.java) as ConsumerFactory<String, String>

    @Test
    fun `limita o lote de cada container para nao exceder as notificacoes em voo`() {
        val properties = appProperties(inventory = 200, registry = 10, delivery = 5, financial = 25)

        val factories =
            listOf(
                config.inventoryKafkaListenerContainerFactory(consumerFactory, properties),
                config.registryKafkaListenerContainerFactory(consumerFactory, properties),
                config.deliveryKafkaListenerContainerFactory(consumerFactory, properties),
                config.financialKafkaListenerContainerFactory(consumerFactory, properties),
            )

        assertEquals(listOf("20", "1", "1", "2"), factories.map(::maxPollRecords))
    }

    private fun maxPollRecords(factory: ConcurrentKafkaListenerContainerFactory<String, String>): String? =
        factory
            .createContainer("notafiscal.notas.v1")
            .containerProperties.kafkaConsumerProperties
            .getProperty(ConsumerConfig.MAX_POLL_RECORDS_CONFIG)

    private fun appProperties(
        inventory: Int,
        registry: Int,
        delivery: Int,
        financial: Int,
    ): NotaFiscalProperties =
        NotaFiscalProperties(
            consumidores =
                Consumidores(
                    estoque = Consumidor(maxEmVoo = inventory),
                    registro = Consumidor(maxEmVoo = registry),
                    entrega = Consumidor(maxEmVoo = delivery),
                    financeiro = Consumidor(maxEmVoo = financial),
                ),
        )

    private fun handlers(): ConsumerErrorHandlerFactory = ConsumerErrorHandlerFactory(DlqForwarderFake())
}
