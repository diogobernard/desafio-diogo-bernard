@file:Suppress("PackageNaming")

package com.itau.desafio_diogo_bernard.gateway.postgres.lote

import com.itau.desafio_diogo_bernard.repository.IssuanceRequestInsert

interface IssuanceRequestBatchPersister {
    fun persist(batch: List<IssuanceRequestInsert>): List<Boolean>
}
