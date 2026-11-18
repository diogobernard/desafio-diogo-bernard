package com.itau.desafio_diogo_bernard.entrypoint.notas

import com.itau.desafio_diogo_bernard.config.BusinessMetrics
import com.itau.desafio_diogo_bernard.config.IngressMetrics
import com.itau.desafio_diogo_bernard.config.JacksonConfig
import com.itau.desafio_diogo_bernard.config.properties.NotaFiscalProperties
import com.itau.desafio_diogo_bernard.entrypoint.camposProblema
import com.itau.desafio_diogo_bernard.usecase.consulta.RetrieveInvoice
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.restdocs.test.autoconfigure.AutoConfigureRestDocs
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document
import org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.get
import org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessRequest
import org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessResponse
import org.springframework.restdocs.operation.preprocess.Preprocessors.prettyPrint
import org.springframework.restdocs.operation.preprocess.Preprocessors.replacePattern
import org.springframework.restdocs.payload.PayloadDocumentation.responseFields
import org.springframework.restdocs.request.RequestDocumentation.parameterWithName
import org.springframework.restdocs.request.RequestDocumentation.pathParameters
import org.springframework.restdocs.request.RequestDocumentation.queryParameters
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.util.regex.Pattern

@WebMvcTest(InvoiceController::class)
@AutoConfigureRestDocs(uriHost = "api.notafiscal.local", uriScheme = "https")
@Import(JacksonConfig::class)
@EnableConfigurationProperties(NotaFiscalProperties::class)
class InvoiceErrorDocumentationTest(
    @param:Autowired private val mockMvc: MockMvc,
) {
    @MockitoBean private lateinit var retrieveInvoice: RetrieveInvoice

    @MockitoBean private lateinit var metrics: BusinessMetrics

    @MockitoBean private lateinit var ingressMetrics: IngressMetrics

    @Test
    fun `documenta cliente ausente ao consultar nota`() {
        mockMvc
            .perform(get("/invoices/{number}", "77").param("series", "1"))
            .andExpect(status().isUnauthorized)
            .andDo(
                document(
                    "invoices/consultar-cliente-ausente",
                    preprocessRequest(prettyPrint()),
                    preprocessResponse(prettyPrint(), replacePattern(PADRAO_TRACE, TRACE_FICTICIO)),
                    pathParameters(parameterWithName("number").description("Número sequencial da nota")),
                    queryParameters(parameterWithName("series").description("Série fiscal")),
                    responseFields(camposProblema()),
                ),
            )
    }

    private companion object {
        const val TRACE_FICTICIO = "\"trace_id\":\"00000000000000000000000000000000\""
        val PADRAO_TRACE = Pattern.compile("\\\"trace_id\\\":\\\"[a-f0-9]+\\\"")
    }
}
