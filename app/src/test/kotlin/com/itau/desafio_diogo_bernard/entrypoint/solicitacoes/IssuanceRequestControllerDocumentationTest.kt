package com.itau.desafio_diogo_bernard.entrypoint.solicitacoes

import com.itau.desafio_diogo_bernard.config.BusinessMetrics
import com.itau.desafio_diogo_bernard.config.IngressMetrics
import com.itau.desafio_diogo_bernard.config.JacksonConfig
import com.itau.desafio_diogo_bernard.config.ObservabilityCorrelation
import com.itau.desafio_diogo_bernard.config.properties.NotaFiscalProperties
import com.itau.desafio_diogo_bernard.entrypoint.camposSolicitacao
import com.itau.desafio_diogo_bernard.model.solicitacao.ClientId
import com.itau.desafio_diogo_bernard.model.solicitacao.IssuanceRequestId
import com.itau.desafio_diogo_bernard.usecase.consulta.RetrieveIssuanceRequest
import com.itau.desafio_diogo_bernard.usecase.emissao.RequestIssuance
import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.given
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.restdocs.test.autoconfigure.AutoConfigureRestDocs
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.restdocs.headers.HeaderDocumentation.headerWithName
import org.springframework.restdocs.headers.HeaderDocumentation.requestHeaders
import org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document
import org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.get
import org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessRequest
import org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessResponse
import org.springframework.restdocs.operation.preprocess.Preprocessors.prettyPrint
import org.springframework.restdocs.payload.PayloadDocumentation.responseFields
import org.springframework.restdocs.request.RequestDocumentation.parameterWithName
import org.springframework.restdocs.request.RequestDocumentation.pathParameters
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@WebMvcTest(IssuanceRequestController::class)
@AutoConfigureRestDocs(uriHost = "api.notafiscal.local", uriScheme = "https")
@Import(JacksonConfig::class)
@EnableConfigurationProperties(NotaFiscalProperties::class)
class IssuanceRequestControllerDocumentationTest(
    @param:Autowired private val mockMvc: MockMvc,
) {
    @MockitoBean private lateinit var retrieveRequest: RetrieveIssuanceRequest

    @MockitoBean private lateinit var requestIssuance: RequestIssuance

    @MockitoBean private lateinit var metrics: BusinessMetrics

    @MockitoBean private lateinit var ingressMetrics: IngressMetrics

    @MockitoBean private lateinit var correlation: ObservabilityCorrelation

    @Test
    fun `documenta consulta de solicitacao`() {
        given(
            retrieveRequest.execute(
                ClientId("cliente-ficticio"),
                IssuanceRequestId(SOLICITACAO_ID_TESTE),
            ),
        ).willReturn(issuanceRequestFixture().status())

        mockMvc
            .perform(
                get("/issuance-requests/{id}", SOLICITACAO_ID_TESTE)
                    .header("X-Client-Id", "cliente-ficticio"),
            ).andExpect(status().isOk)
            .andDo(
                document(
                    "issuance-requests/consultar",
                    preprocessRequest(prettyPrint()),
                    preprocessResponse(prettyPrint()),
                    pathParameters(parameterWithName("id").description("Identificador UUID da solicitação")),
                    requestHeaders(
                        headerWithName("X-Client-Id").description("Identificador do cliente fornecido pelo gateway"),
                    ),
                    responseFields(camposSolicitacao()),
                ),
            )
    }
}
