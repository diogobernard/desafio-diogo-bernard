package com.itau.desafio_diogo_bernard.entrypoint.consumidores

import com.itau.desafio_diogo_bernard.config.properties.NotaFiscalProperties.Consumidor
import java.util.concurrent.CompletableFuture

/** Devolve as respostas na ordem informada; a última se repete nas tentativas seguintes. */
internal class RegisterNotificationFake(
    private vararg val responses: () -> CompletableFuture<Void>,
) : RegisterNotification {
    var attempts = 0

    override fun receive(
        consumer: String,
        settings: Consumidor,
        payload: String,
    ): CompletableFuture<Void> = responses[minOf(attempts++, responses.lastIndex)]()

    companion object {
        fun success(): () -> CompletableFuture<Void> = { CompletableFuture.completedFuture(null) }

        fun failure(error: Exception): () -> CompletableFuture<Void> = { CompletableFuture.failedFuture(error) }

        fun lanca(error: Exception): () -> CompletableFuture<Void> = { throw error }
    }
}
