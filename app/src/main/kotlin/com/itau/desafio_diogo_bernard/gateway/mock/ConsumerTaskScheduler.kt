package com.itau.desafio_diogo_bernard.gateway.mock

import java.time.Duration

interface ConsumerTaskScheduler {
    fun schedule(
        delay: Duration,
        task: Runnable,
    )
}
