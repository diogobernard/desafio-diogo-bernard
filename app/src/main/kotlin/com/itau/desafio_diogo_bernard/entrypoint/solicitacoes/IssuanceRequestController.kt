package com.itau.desafio_diogo_bernard.entrypoint.solicitacoes

import com.itau.desafio_diogo_bernard.config.BusinessMetrics
import com.itau.desafio_diogo_bernard.config.ObservabilityCorrelation
import com.itau.desafio_diogo_bernard.config.properties.NotaFiscalProperties
import com.itau.desafio_diogo_bernard.entrypoint.model.IssuanceRequestResponse
import com.itau.desafio_diogo_bernard.entrypoint.model.IssueInvoiceRequest
import com.itau.desafio_diogo_bernard.exception.IssuanceRequestNotFoundException
import com.itau.desafio_diogo_bernard.model.solicitacao.ClientId
import com.itau.desafio_diogo_bernard.model.solicitacao.IdempotencyKey
import com.itau.desafio_diogo_bernard.model.solicitacao.IssuanceRequestId
import com.itau.desafio_diogo_bernard.model.solicitacao.IssuanceRequestStatus
import com.itau.desafio_diogo_bernard.usecase.consulta.RetrieveIssuanceRequest
import com.itau.desafio_diogo_bernard.usecase.emissao.IssuanceCommand
import com.itau.desafio_diogo_bernard.usecase.emissao.RequestIssuance
import com.itau.desafio_diogo_bernard.usecase.validations.OrderInputValidator
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import java.net.URI
import java.util.UUID

@RestController
@ConditionalOnExpression("'\${app.runtime.role:combined}' != 'worker'")
@RequestMapping("/issuance-requests")
class IssuanceRequestController(
    private val requestIssuance: RequestIssuance,
    private val retrieveRequest: RetrieveIssuanceRequest,
    private val properties: NotaFiscalProperties,
    private val metrics: BusinessMetrics,
    private val correlation: ObservabilityCorrelation,
) {
    @PostMapping
    fun create(
        @RequestHeader(name = HEADER_CLIENTE, required = false) client: String?,
        @RequestHeader(name = HEADER_CHAVE, required = false) key: String?,
        @RequestBody request: IssueInvoiceRequest,
    ): ResponseEntity<IssuanceRequestResponse> {
        val clientId = client?.trim().orEmpty()
        if (clientId.isEmpty()) throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Cliente não identificado")
        if (clientId.length > TAMANHO_CLIENTE) throw ResponseStatusException(HttpStatus.UNAUTHORIZED)
        val keyId = parseKey(key)
        orderValidator.requireValid(request.toInput())
        val issuanceRequest = requestIssuance.execute(
            IssuanceCommand(
                clientId = ClientId(clientId),
                key = keyId,
                order = request.toDomain(),
            ),
        )
        correlation.annotate(issuanceRequest.id.value) {
            metrics.requestReceived(issuanceRequest.clientId.value)
            logger.info("Solicitação recebida; solicitacaoId={}", issuanceRequest.id.value)
        }
        val response = IssuanceRequestResponse.fromDomain(issuanceRequest.status())
        return ResponseEntity
            .accepted()
            .location(URI.create(response.links.self))
            .header(HttpHeaders.RETRY_AFTER, retryAfterSeconds())
            .body(response)
    }

    @GetMapping("/{id}")
    fun retrieve(
        @RequestHeader(name = HEADER_CLIENTE, required = false) client: String?,
        @PathVariable id: String,
    ): ResponseEntity<IssuanceRequestResponse> {
        val clientId = validateClient(client)
        val validId = parseUuid(id) ?: throw ResponseStatusException(HttpStatus.NOT_FOUND)
        val parsedRequestId = IssuanceRequestId(validId.toString())
        val issuanceRequest =
            try {
                retrieveRequest.execute(clientId, parsedRequestId)
            } catch (missing: IssuanceRequestNotFoundException) {
                metrics.record404(validId.toString())
                throw missing
            }
        metrics.recordSuccessfulRead(validId.toString())
        val response = IssuanceRequestResponse.fromDomain(issuanceRequest)
        val headers = HttpHeaders()
        if (issuanceRequest.status in STATUS_PENDENTES) {
            headers.set(HttpHeaders.RETRY_AFTER, retryAfterSeconds())
        }
        response.links.invoice?.let { headers.add(HttpHeaders.LINK, "<$it>; rel=\"invoice\"") }
        return ResponseEntity(response, headers, HttpStatus.OK)
    }

    private fun parseKey(key: String?): IdempotencyKey {
        val providedKey = key ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Idempotency-Key inválida")
        val keyUuid = parseUuid(providedKey)
        if (keyUuid == null) throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Idempotency-Key inválida")
        return IdempotencyKey(keyUuid.toString())
    }

    private fun parseUuid(value: String): UUID? =
        runCatching { UUID.fromString(value) }
            .getOrNull()
            ?.takeIf { it.toString().equals(value, ignoreCase = true) }

    private fun validateClient(client: String?): ClientId {
        val value = client?.trim().orEmpty()
        if (value.isEmpty()) throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Cliente não identificado")
        if (value.length > TAMANHO_CLIENTE) throw ResponseStatusException(HttpStatus.UNAUTHORIZED)
        return ClientId(value)
    }

    private fun retryAfterSeconds(): String =
        properties.consulta.retryAfter.seconds
            .coerceAtLeast(1)
            .toString()

    private companion object {
        const val HEADER_CLIENTE = "X-Client-Id"
        const val HEADER_CHAVE = "Idempotency-Key"
        const val TAMANHO_CLIENTE = 64
        val orderValidator = OrderInputValidator()
        val logger = LoggerFactory.getLogger(IssuanceRequestController::class.java)
        val STATUS_PENDENTES = setOf(IssuanceRequestStatus.RECEBIDA, IssuanceRequestStatus.PROCESSANDO)
    }
}
