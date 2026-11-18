package com.itau.desafio_diogo_bernard.config

import org.mockito.Mockito
import org.springframework.core.task.SimpleAsyncTaskExecutor
import org.springframework.kafka.core.ConsumerFactory
import org.springframework.kafka.listener.ConcurrentMessageListenerContainer
import org.springframework.kafka.listener.ContainerProperties
import kotlin.test.Test
import kotlin.test.assertNull

class KafkaListenerThreadsConfigTest {
    @Test
    fun `remove o executor de virtual threads dos consumidores kafka`() {
        @Suppress("UNCHECKED_CAST")
        val consumerFactory = Mockito.mock(ConsumerFactory::class.java) as ConsumerFactory<Any, Any>
        val appProperties = ContainerProperties("notafiscal.solicitacoes.v1")
        appProperties.listenerTaskExecutor = SimpleAsyncTaskExecutor("kafka-").apply { setVirtualThreads(true) }
        val container = ConcurrentMessageListenerContainer(consumerFactory, appProperties)

        KafkaListenerThreadsConfig().kafkaConsumersOnPlatformThreads().configure(container)

        assertNull(container.containerProperties.listenerTaskExecutor)
    }
}
