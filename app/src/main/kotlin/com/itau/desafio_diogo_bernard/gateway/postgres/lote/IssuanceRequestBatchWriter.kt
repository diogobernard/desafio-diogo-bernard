@file:Suppress("PackageNaming")

package com.itau.desafio_diogo_bernard.gateway.postgres.lote

import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Component

@Component
class IssuanceRequestBatchWriter(
    private val persister: IssuanceRequestBatchPersister,
) : BatchWriter {
    override fun write(orders: List<PendingWrite>) {
        val claimed = orders.filter(PendingWrite::claim)
        if (claimed.isEmpty()) return
        val outcomes = persist(claimed)
        claimed.forEachIndexed { index, order -> order.complete(outcomes[index]) }
    }

    private fun persist(orders: List<PendingWrite>): List<Result<Boolean>> =
        runCatching {
            persister.persist(orders.map { it.insert })
        }.fold(
            onSuccess = { createdEntries -> createdEntries.map { Result.success(it) } },
            onFailure = { error -> fail(orders, error) },
        )

    private fun fail(
        orders: List<PendingWrite>,
        error: Throwable,
    ): List<Result<Boolean>> =
        if (error is DataIntegrityViolationException && orders.size > 1) {
            orders.flatMap { persist(listOf(it)) }
        } else {
            orders.map { Result.failure(error) }
        }
}
