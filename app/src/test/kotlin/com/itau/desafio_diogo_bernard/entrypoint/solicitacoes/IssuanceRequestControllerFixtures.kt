package com.itau.desafio_diogo_bernard.entrypoint.solicitacoes

import com.itau.desafio_diogo_bernard.config.BusinessMetrics
import com.itau.desafio_diogo_bernard.config.ObservabilityCorrelation
import com.itau.desafio_diogo_bernard.config.properties.NotaFiscalProperties
import com.itau.desafio_diogo_bernard.entrypoint.model.AddressRequest
import com.itau.desafio_diogo_bernard.entrypoint.model.IssueInvoiceRequest
import com.itau.desafio_diogo_bernard.entrypoint.model.ItemRequest
import com.itau.desafio_diogo_bernard.entrypoint.model.RecipientRequest
import com.itau.desafio_diogo_bernard.model.pedido.AddressPurpose
import com.itau.desafio_diogo_bernard.model.pedido.PersonType
import com.itau.desafio_diogo_bernard.model.pedido.Region
import com.itau.desafio_diogo_bernard.model.solicitacao.ClientId
import com.itau.desafio_diogo_bernard.model.solicitacao.Fingerprint
import com.itau.desafio_diogo_bernard.model.solicitacao.IdempotencyKey
import com.itau.desafio_diogo_bernard.model.solicitacao.IssuanceRequest
import com.itau.desafio_diogo_bernard.model.solicitacao.IssuanceRequestId
import com.itau.desafio_diogo_bernard.model.solicitacao.IssuanceRequestState
import com.itau.desafio_diogo_bernard.model.solicitacao.IssuanceRequestStatus
import com.itau.desafio_diogo_bernard.usecase.consulta.RetrieveIssuanceRequest
import com.itau.desafio_diogo_bernard.usecase.emissao.IssuanceCommand
import com.itau.desafio_diogo_bernard.usecase.emissao.RequestIssuance
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.micrometer.observation.ObservationRegistry
import java.math.BigDecimal
import java.time.Instant

internal const val SOLICITACAO_ID_TESTE = "01933b6e-7c1a-7001-9f10-2b6a4c8d0e11"
internal const val CHAVE_TESTE = "6f1c2b7e-3a4d-4e5f-8a9b-0c1d2e3f4a5b"

internal fun comandoEmissaoFixture(client: String): IssuanceCommand =
    IssuanceCommand(ClientId(client), IdempotencyKey(CHAVE_TESTE), issuanceRequestFixture().order)

internal fun issueInvoiceRequestFixture(): IssueInvoiceRequest =
    IssueInvoiceRequest(
        RecipientRequest(PersonType.FISICA, "52998224725", null),
        listOf(ItemRequest("SKU-1", "Produto", BigDecimal("12.30"), BigDecimal.ONE)),
        BigDecimal("5.00"),
        listOf(AddressRequest(AddressPurpose.ENTREGA, Region.SUDESTE)),
    )

internal fun issuanceRequestFixture(status: IssuanceRequestStatus = IssuanceRequestStatus.RECEBIDA): IssuanceRequest =
    IssuanceRequest(
        IssuanceRequestId(SOLICITACAO_ID_TESTE),
        ClientId("cliente-a"),
        IdempotencyKey(CHAVE_TESTE),
        Fingerprint("a".repeat(64)),
        issueInvoiceRequestFixture().toDomain(),
        status,
        Instant.EPOCH,
        Instant.EPOCH,
    )

internal class IssuanceRequestControllerFixture(
    issuanceRequest: IssuanceRequest = issuanceRequestFixture(),
    query: (ClientId, IssuanceRequestId) -> IssuanceRequestState = { _, _ -> issuanceRequest.status() },
) {
    val registry = SimpleMeterRegistry()
    val metrics = BusinessMetrics(registry)
    val controller =
        IssuanceRequestController(
            object : RequestIssuance {
                override fun execute(command: IssuanceCommand): IssuanceRequest = issuanceRequest
            },
            object : RetrieveIssuanceRequest {
                override fun execute(
                    clientId: ClientId,
                    id: IssuanceRequestId,
                ): IssuanceRequestState = query(clientId, id)
            },
            NotaFiscalProperties(),
            metrics,
            ObservabilityCorrelation(ObservationRegistry.create()),
        )
}
