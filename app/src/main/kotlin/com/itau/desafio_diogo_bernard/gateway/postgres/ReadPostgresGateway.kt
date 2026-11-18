package com.itau.desafio_diogo_bernard.gateway.postgres

import com.itau.desafio_diogo_bernard.config.BusinessMetrics
import com.itau.desafio_diogo_bernard.config.properties.NotaFiscalProperties
import com.itau.desafio_diogo_bernard.gateway.InvoiceReadGateway
import com.itau.desafio_diogo_bernard.gateway.IssuanceRequestReadGateway
import com.itau.desafio_diogo_bernard.model.nota.FiscalNumber
import com.itau.desafio_diogo_bernard.model.nota.Invoice
import com.itau.desafio_diogo_bernard.model.nota.InvoiceReference
import com.itau.desafio_diogo_bernard.model.nota.Series
import com.itau.desafio_diogo_bernard.model.solicitacao.IssuanceRequestId
import com.itau.desafio_diogo_bernard.model.solicitacao.IssuanceRequestState
import com.itau.desafio_diogo_bernard.repository.IssuanceRequestRepository
import com.itau.desafio_diogo_bernard.repository.NumberingCounterRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

@Component
class ReadPostgresGateway(
    private val requests: IssuanceRequestRepository,
    private val invoices: PersistedInvoice,
    private val counters: NumberingCounterRepository,
    private val properties: NotaFiscalProperties,
    private val metrics: BusinessMetrics,
) : IssuanceRequestReadGateway,
    InvoiceReadGateway {
    @Transactional(readOnly = true)
    override fun find(id: IssuanceRequestId): IssuanceRequestState? = findStatus(id)

    @Transactional
    override fun findConsistent(id: IssuanceRequestId): IssuanceRequestState? {
        metrics.fallback("recente")
        return findStatus(id).also { if (it == null) metrics.fallback("negado") }
    }

    @Transactional(readOnly = true)
    override fun find(reference: InvoiceReference): Invoice? = findInvoice(reference)

    @Transactional
    override fun findConsistent(reference: InvoiceReference): Invoice? {
        metrics.fallback("ultimo_numero")
        return findInvoice(reference).also { if (it == null) metrics.fallback("negado") }
    }

    @Transactional
    override fun lastIssuedNumber(series: Series): FiscalNumber? {
        val last = cache[series]?.number ?: 1
        return lastIssuedNumber(series, FiscalNumber(last.coerceAtLeast(1)))
    }

    @Transactional
    override fun lastIssuedNumber(
        series: Series,
        requested: FiscalNumber,
    ): FiscalNumber? {
        val current = cache[series]
        if (current?.isValid(requested) == true) return current.number.toFiscalNumberOrNull()
        return lock.withLock { reload(series, requested) }
    }

    private fun findStatus(id: IssuanceRequestId): IssuanceRequestState? {
        val stateRecord = requests.findStatus(UUID.fromString(id.value))
        return stateRecord?.toDomain()
    }

    private fun findInvoice(reference: InvoiceReference): Invoice? = invoices.find(reference)

    private fun reload(
        series: Series,
        requested: FiscalNumber,
    ): FiscalNumber? {
        val current = cache[series]
        if (current?.isValid(requested) == true) return current.number.toFiscalNumberOrNull()
        val number = counters.lastNumber(series.value) ?: 0
        cache[series] = LastNumberCache(number, System.nanoTime() + properties.consulta.cacheUltimoNumero.toNanos())
        return number.toFiscalNumberOrNull()
    }

    internal fun Int.toFiscalNumberOrNull(): FiscalNumber? = if (this in 1..NUMERO_MAXIMO) FiscalNumber(this) else null

    private data class LastNumberCache(
        val number: Int,
        val expiresAtNanos: Long,
    ) {
        fun isValid(requested: FiscalNumber): Boolean = expiresAtNanos > System.nanoTime() && requested.value <= number
    }

    private companion object {
        const val NUMERO_MAXIMO = 999_999_999
        val cache = ConcurrentHashMap<Series, LastNumberCache>()
        val lock = ReentrantLock()
    }
}
