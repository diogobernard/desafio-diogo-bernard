package com.itau.desafio_diogo_bernard.gateway.mock

import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression
import org.springframework.stereotype.Component
import java.time.Duration
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit.MILLISECONDS

@Component
@ConditionalOnExpression("'\${app.runtime.role:combined}' != 'api'")
class ScheduledConsumerTaskScheduler(
    private val executor: ScheduledExecutorService,
) : ConsumerTaskScheduler {
    override fun schedule(
        delay: Duration,
        task: Runnable,
    ) {
        executor.schedule(task, delay.toMillis(), MILLISECONDS)
    }
}
