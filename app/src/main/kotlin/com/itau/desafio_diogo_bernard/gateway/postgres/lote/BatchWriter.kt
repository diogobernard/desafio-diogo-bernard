@file:Suppress("PackageNaming")

package com.itau.desafio_diogo_bernard.gateway.postgres.lote

interface BatchWriter {
    fun write(orders: List<PendingWrite>)
}
