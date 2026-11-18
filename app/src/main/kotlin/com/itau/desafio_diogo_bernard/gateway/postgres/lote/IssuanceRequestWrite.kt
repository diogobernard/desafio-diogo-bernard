@file:Suppress("PackageNaming")

package com.itau.desafio_diogo_bernard.gateway.postgres.lote

import com.itau.desafio_diogo_bernard.repository.IssuanceRequestInsert

interface IssuanceRequestWrite {
    fun persist(insert: IssuanceRequestInsert): Boolean

    fun pending(): Int
}
