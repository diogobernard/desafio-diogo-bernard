@file:Suppress("PackageNaming")

package com.itau.desafio_diogo_bernard.entrypoint.handlers

import com.itau.desafio_diogo_bernard.config.IngressMetrics
import com.itau.desafio_diogo_bernard.config.properties.NotaFiscalProperties
import com.itau.desafio_diogo_bernard.exception.IdempotencyKeyReusedException
import com.itau.desafio_diogo_bernard.exception.IngressCapacityExhaustedException
import com.itau.desafio_diogo_bernard.exception.IngressRejectionReason
import com.itau.desafio_diogo_bernard.exception.InvalidOrderException
import com.itau.desafio_diogo_bernard.exception.InvoiceNotFoundException
import com.itau.desafio_diogo_bernard.usecase.validations.Violation
import com.itau.desafio_diogo_bernard.usecase.validations.ViolationType
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.mock.http.MockHttpInputMessage
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException

@WebMvcTest(ExceptionsController::class)
@EnableConfigurationProperties(NotaFiscalProperties::class)
class ProblemDetailsOrderTest(
    @param:Autowired private val mockMvc: MockMvc,
) {
    @MockitoBean private lateinit var ingressMetrics: IngressMetrics

    @ParameterizedTest(name = "{0} responde {1}")
    @CsvSource(
        "/excecoes/pedido-invalido, 400",
        "/excecoes/chave-reutilizada, 422",
        "/excecoes/nota-nao-encontrada, 404",
        "/excecoes/rota-inexistente, 404",
        "/excecoes/capacidade, 503",
        "/excecoes/json-invalido, 400",
        "/excecoes/status, 401",
        "/excecoes/cabecalho, 401",
        "/excecoes/interno, 500",
    )
    fun `cada excecao e traduzida pelo seu handler e nao pelo de erro interno`(
        path: String,
        expectedStatus: Int,
    ) {
        mockMvc
            .perform(get(path))
            .andExpect(status().`is`(expectedStatus))
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
    }
}

@RestController
@RequestMapping("/excecoes")
class ExceptionsController {
    @GetMapping("/pedido-invalido")
    fun invalidOrder(): String =
        throw InvalidOrderException(
            listOf(Violation("campo", ViolationType.AUSENTE, "ausente")),
        )

    @GetMapping("/chave-reutilizada")
    fun reusedKey(): String = throw IdempotencyKeyReusedException()

    @GetMapping("/nota-nao-encontrada")
    fun notaNaoEncontrada(): String = throw InvoiceNotFoundException()

    @GetMapping("/capacidade")
    fun capacity(): String = throw IngressCapacityExhaustedException(IngressRejectionReason.FILA_CHEIA)

    @GetMapping("/json-invalido")
    fun invalidJson(): String =
        throw HttpMessageNotReadableException(
            "json",
            MockHttpInputMessage(ByteArray(0)),
        )

    @GetMapping("/status")
    fun status(): String = throw ResponseStatusException(HttpStatus.UNAUTHORIZED)

    @GetMapping("/cabecalho")
    fun cabecalho(
        @RequestHeader("X-Client-Id") client: String,
    ): String = client

    @GetMapping("/interno")
    fun interno(): String = throw IllegalStateException("falha inesperada")
}
