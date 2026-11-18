package com.itau.desafio_diogo_bernard.config

import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService

@Configuration
@ConditionalOnExpression("'\${app.runtime.role:combined}' != 'api'")
class ConsumerSchedulerConfig {
    @Bean(destroyMethod = "shutdown")
    fun consumerScheduledExecutor(): ScheduledExecutorService = Executors.newScheduledThreadPool(4)
}
