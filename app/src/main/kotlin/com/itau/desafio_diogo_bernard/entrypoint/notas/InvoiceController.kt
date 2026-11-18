package com.itau.desafio_diogo_bernard.entrypoint.notas

import com.itau.desafio_diogo_bernard.config.BusinessMetrics
import com.itau.desafio_diogo_bernard.config.properties.NotaFiscalProperties
import com.itau.desafio_diogo_bernard.entrypoint.model.InvoiceResponse
import com.itau.desafio_diogo_bernard.exception.InvoiceNotFoundException
import com.itau.desafio_diogo_bernard.model.nota.FiscalNumber
import com.itau.desafio_diogo_bernard.model.nota.InvoiceReference
import com.itau.desafio_diogo_bernard.model.nota.Series
import com.itau.desafio_diogo_bernard.model.solicitacao.ClientId
import com.itau.desafio_diogo_bernard.usecase.consulta.RetrieveInvoice
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression
import org.springframework.http.CacheControl
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import java.time.Duration

@RestController
@ConditionalOnExpression("'\${app.runtime.role:combined}' != 'worker'")
@RequestMapping("/invoices")
class InvoiceController(
    private val retrieveInvoice: RetrieveInvoice,
    private val properties: NotaFiscalProperties,
    private val metrics: BusinessMetrics,
) {
    @GetMapping("/{number}")
    fun retrieve(
        @RequestHeader(name = "X-Client-Id", required = false) client: String?,
        @PathVariable("number") number: String,
        @RequestParam(name = "series", required = false) series: String?,
        @RequestHeader(name = HttpHeaders.IF_NONE_MATCH, required = false) requestedEtag: String?,
    ): ResponseEntity<InvoiceResponse> {
        val clientId = validateClient(client)
        val reference = parseReference(number, series)
        val readKey = "${reference.series.value}-${reference.number.value}"
        val invoice =
            try {
                retrieveInvoice.execute(clientId = ClientId(clientId), reference = reference)
            } catch (missing: InvoiceNotFoundException) {
                metrics.record404(readKey)
                throw missing
            }
        metrics.recordSuccessfulRead(readKey)
        val etag = "\"${reference.series.value}-${reference.number.value}\""
        val headers =
            HttpHeaders().apply {
                set(HttpHeaders.ETAG, etag)
                cacheControl =
                    CacheControl
                        .maxAge(CACHE_DURACAO)
                        .cachePrivate()
                        .immutable()
                        .headerValue
            }
        if (requestedEtag != null && requestedEtag.split(',').any { it.trim() == etag }) {
            return ResponseEntity.status(HttpStatus.NOT_MODIFIED).headers(headers).build()
        }
        return ResponseEntity(InvoiceResponse.fromDomain(invoice), headers, HttpStatus.OK)
    }

    private fun validateClient(client: String?): String {
        val clientId = client?.trim().orEmpty()
        if (clientId.isEmpty() || clientId.length > TAMANHO_CLIENTE) {
            throw ResponseStatusException(HttpStatus.UNAUTHORIZED)
        }
        return clientId
    }

    private fun parseReference(
        number: String,
        series: String?,
    ): InvoiceReference {
        val fiscalSeries = parseSeries(series)
        val fiscalNumber = parseNumber(number)
        return runCatching { InvoiceReference(FiscalNumber(fiscalNumber), Series(fiscalSeries)) }
            .getOrNull()
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND)
    }

    private fun parseSeries(series: String?): Int =
        series?.let { it.toIntOrNull() ?: throw ResponseStatusException(HttpStatus.NOT_FOUND) }
            ?: properties.numeracao.seriePadrao

    private fun parseNumber(number: String): Int = number.toIntOrNull() ?: notFound()

    private fun notFound(): Nothing = throw ResponseStatusException(HttpStatus.NOT_FOUND)

    private companion object {
        const val TAMANHO_CLIENTE = 64
        val CACHE_DURACAO: Duration = Duration.ofDays(1)
    }
}
