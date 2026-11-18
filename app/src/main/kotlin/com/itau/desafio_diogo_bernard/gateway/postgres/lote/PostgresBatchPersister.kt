@file:Suppress("PackageNaming")

package com.itau.desafio_diogo_bernard.gateway.postgres.lote

import com.itau.desafio_diogo_bernard.repository.IssuanceBatchParameters
import com.itau.desafio_diogo_bernard.repository.IssuanceRequestInsert
import com.itau.desafio_diogo_bernard.repository.IssuanceRequestRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class PostgresBatchPersister(
    private val requests: IssuanceRequestRepository,
) : IssuanceRequestBatchPersister {
    @Transactional
    override fun persist(batch: List<IssuanceRequestInsert>): List<Boolean> {
        val parameters = IssuanceBatchParameters(batch)
        val createdItems = requests.insertBatch(parameters).toSet()
        return batch.map { it.event.eventId in createdItems }
    }
}
