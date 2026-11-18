package com.itau.desafio_diogo_bernard.repository

import com.itau.desafio_diogo_bernard.model.nota.FiscalNumber
import com.itau.desafio_diogo_bernard.model.nota.InvoiceReference
import com.itau.desafio_diogo_bernard.model.nota.Series
import com.itau.desafio_diogo_bernard.model.solicitacao.ClientId
import com.itau.desafio_diogo_bernard.model.solicitacao.FailureReason
import com.itau.desafio_diogo_bernard.model.solicitacao.IssuanceRequestId
import com.itau.desafio_diogo_bernard.model.solicitacao.IssuanceRequestState
import com.itau.desafio_diogo_bernard.model.solicitacao.IssuanceRequestStatus
import java.time.Instant
import java.util.UUID

/** Projeção da consulta de situação: lê só as colunas da resposta, sem o `pedido` jsonb. */
data class IssuanceRequestStateRecord(
    val id: UUID,
    val clientId: String,
    val status: IssuanceRequestStatus,
    val receivedAt: Instant,
    val updatedAt: Instant,
    val invoiceSeries: Int?,
    val invoiceNumber: Int?,
    val failureReason: String?,
) {
    fun toDomain(): IssuanceRequestState =
        IssuanceRequestState(
            IssuanceRequestId(id.toString()),
            ClientId(clientId),
            status,
            receivedAt,
            updatedAt,
            invoiceReference(),
            failureReason?.let(::FailureReason),
        )

    private fun invoiceReference(): InvoiceReference? {
        val series = invoiceSeries
        val number = invoiceNumber
        if (series == null || number == null) return null
        return InvoiceReference(FiscalNumber(number), Series(series))
    }
}
