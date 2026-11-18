package com.itau.desafio_diogo_bernard.entrypoint.consumidores

import com.itau.desafio_diogo_bernard.config.properties.NotaFiscalProperties.Consumidor
import java.util.concurrent.CompletableFuture

/** Uma tentativa de entregar a notificação ao consumidor simulado, sem retry. */
interface RegisterNotification {
    fun receive(
        consumer: String,
        settings: Consumidor,
        payload: String,
    ): CompletableFuture<Void>
}
