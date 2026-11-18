package com.itau.desafio_diogo_bernard.config

import com.itau.desafio_diogo_bernard.config.properties.NotaFiscalProperties.Consumidor
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries
import org.springframework.util.backoff.BackOff

private const val INTERVALO_RETRY_MS = 200L
private const val INTERVALO_RETRY_MAXIMO_MS = 5_000L
private const val FATOR_BACKOFF = 2.0

fun consumerBackOff(settings: Consumidor): BackOff {
    val attempts = settings.tentativas.coerceAtLeast(1).toInt()
    return ExponentialBackOffWithMaxRetries(attempts - 1).apply {
        setInitialInterval(INTERVALO_RETRY_MS)
        setMultiplier(FATOR_BACKOFF)
        setMaxInterval(INTERVALO_RETRY_MAXIMO_MS)
    }
}
