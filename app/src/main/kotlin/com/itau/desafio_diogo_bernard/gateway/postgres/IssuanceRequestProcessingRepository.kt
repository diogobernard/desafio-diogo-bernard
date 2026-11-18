package com.itau.desafio_diogo_bernard.gateway.postgres

import com.itau.desafio_diogo_bernard.model.nota.FiscalNumber
import com.itau.desafio_diogo_bernard.model.nota.Invoice
import com.itau.desafio_diogo_bernard.model.nota.InvoiceReference
import com.itau.desafio_diogo_bernard.model.nota.Series
import com.itau.desafio_diogo_bernard.model.solicitacao.FailureReason
import com.itau.desafio_diogo_bernard.model.solicitacao.IssuanceRequest
import com.itau.desafio_diogo_bernard.model.solicitacao.IssuanceRequestId
import com.itau.desafio_diogo_bernard.model.solicitacao.IssuanceRequestStatus
import com.itau.desafio_diogo_bernard.repository.IssuanceRequestRepository
import com.itau.desafio_diogo_bernard.repository.IssuanceStateRecord
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.json.JsonMapper
import java.time.Clock
import java.util.UUID

@Component
@Transactional
class IssuanceRequestProcessingRepository(
    private val requests: IssuanceRequestRepository,
    private val json: JsonMapper,
    private val clock: Clock,
) : IssuanceRequestProcessing {
    override fun start(id: IssuanceRequestId): IssuanceRequest? {
        val key = UUID.fromString(id.value)
        if (requests.start(key, clock.instant()) == 0) return null
        return requests.findById(key).orElse(null)?.toDomain(json)
    }

    override fun lockForIssuance(id: IssuanceRequestId): IssuanceState? {
        val stateRecord = requests.lockForIssuance(UUID.fromString(id.value)) ?: return null
        return IssuanceState(IssuanceRequestStatus.valueOf(stateRecord.status), reference(stateRecord))
    }

    private fun reference(stateRecord: IssuanceStateRecord): InvoiceReference? {
        val series = stateRecord.invoiceSeries
        val number = stateRecord.invoiceNumber
        if (series == null || number == null) return null
        return InvoiceReference(FiscalNumber(number), Series(series))
    }

    override fun markIssued(invoice: Invoice) {
        val updatedRows =
            requests.markIssued(
                UUID.fromString(invoice.requestId.value),
                invoice.reference.series.value,
                invoice.reference.number.value,
                invoice.issuedAt,
            )
        require(updatedRows == 1) { "Solicitação ausente ao marcar a emissão" }
    }

    override fun markFailed(
        id: IssuanceRequestId,
        reason: FailureReason,
    ) {
        requests.markFailed(UUID.fromString(id.value), reason.value.take(LIMITE_MOTIVO), clock.instant())
    }

    private companion object {
        const val LIMITE_MOTIVO = 200
    }
}
