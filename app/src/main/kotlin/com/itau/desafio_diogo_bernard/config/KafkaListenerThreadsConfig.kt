package com.itau.desafio_diogo_bernard.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.config.ContainerCustomizer
import org.springframework.kafka.listener.ConcurrentMessageListenerContainer

private typealias ConsumersCustomizer = ContainerCustomizer<Any, Any, ConcurrentMessageListenerContainer<Any, Any>>

// No JDK 21, o cliente Kafka registra logs dentro de blocos synchronized, o que prende a virtual
// thread ao carrier enquanto ela espera o lock do appender. Com os consumidores do Boot em virtual
// threads, todos os carriers ficaram presos e o worker travou na atribuição de partições (tarefa 16).
// Sem executor próprio, o container usa threads de plataforma; o restante segue em virtual threads.
@Configuration
class KafkaListenerThreadsConfig {
    @Bean
    fun kafkaConsumersOnPlatformThreads(): ConsumersCustomizer =
        ContainerCustomizer { container ->
            container.containerProperties.listenerTaskExecutor = null
        }
}
