package com.itau.desafio_diogo_bernard.gateway.mock

import java.time.Duration
import java.util.concurrent.RejectedExecutionException

internal class ConsumerTaskSchedulerFake(
    private var failOnce: Boolean = false,
) : ConsumerTaskScheduler {
    var delay: Duration? = null
    val tasks = mutableListOf<Runnable>()

    override fun schedule(
        delay: Duration,
        task: Runnable,
    ) {
        if (failOnce) {
            failOnce = false
            throw RejectedExecutionException("falha de agendamento")
        }
        this.delay = delay
        tasks.add(task)
    }
}
