package com.itau.desafio_diogo_bernard.repository

/** Colunas lidas sob lock na emissão: o suficiente para saber se a nota já foi emitida. */
interface IssuanceStateRecord {
    val status: String
    val invoiceSeries: Int?
    val invoiceNumber: Int?
}
