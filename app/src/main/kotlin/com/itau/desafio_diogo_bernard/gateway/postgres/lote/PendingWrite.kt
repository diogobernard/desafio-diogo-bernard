@file:Suppress("PackageNaming")

package com.itau.desafio_diogo_bernard.gateway.postgres.lote

import com.itau.desafio_diogo_bernard.exception.IngressCapacityExhaustedException
import com.itau.desafio_diogo_bernard.exception.IngressRejectionReason.ESPERA_EXCEDIDA
import com.itau.desafio_diogo_bernard.repository.IssuanceRequestInsert
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit.NANOSECONDS
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicBoolean

class PendingWrite(
    val insert: IssuanceRequestInsert,
) {
    private val outcome = CompletableFuture<Result<Boolean>>()
    private val decided = AtomicBoolean()

    fun claim(): Boolean = decided.compareAndSet(false, true)

    fun complete(result: Result<Boolean>): Boolean = outcome.complete(result)

    fun await(wait: Duration): Boolean = (resultWithin(wait) ?: cancelOrAwait()).getOrThrow()

    private fun resultWithin(wait: Duration): Result<Boolean>? =
        try {
            outcome.get(wait.toNanos(), NANOSECONDS)
        } catch (_: TimeoutException) {
            null
        }

    private fun cancelOrAwait(): Result<Boolean> {
        if (claim()) throw IngressCapacityExhaustedException(ESPERA_EXCEDIDA)
        return outcome.get()
    }
}
