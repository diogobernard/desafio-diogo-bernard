package com.itau.desafio_diogo_bernard.e2e

import org.awaitility.kotlin.await
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.client.EntityExchangeResult
import org.springframework.test.web.servlet.client.RestTestClient
import org.testcontainers.kafka.KafkaContainer
import org.testcontainers.postgresql.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import java.util.UUID
import java.util.concurrent.TimeUnit.SECONDS
import javax.sql.DataSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
abstract class InvoiceIssuanceE2EBase {
    @LocalServerPort
    private var port: Int = 0

    @Autowired
    private lateinit var dataSource: DataSource

    protected lateinit var jdbc: JdbcClient

    protected lateinit var http: RestTestClient

    @BeforeEach
    fun preparar() {
        jdbc = JdbcClient.create(dataSource)
        http = RestTestClient.bindToServer().baseUrl("http://localhost:$port").build()
        await.atMost(60, SECONDS).untilAsserted {
            val pending =
                jdbc
                    .sql("SELECT count(*) FROM solicitacao WHERE status IN ('RECEBIDA','PROCESSANDO')")
                    .query(Int::class.java)
                    .single()
            assertEquals(0, pending)
        }
        jdbc.sql("DELETE FROM nota_fiscal_item").update()
        jdbc.sql("DELETE FROM nota_fiscal").update()
        jdbc.sql("DELETE FROM notificacao_recebida").update()
        jdbc.sql("DELETE FROM outbox").update()
        jdbc.sql("DELETE FROM solicitacao").update()
        jdbc.sql("UPDATE contador_numeracao SET ultimo_numero=0").update()
    }

    protected fun postar(
        payload: String = PEDIDO,
        key: String = UUID.randomUUID().toString(),
    ): EntityExchangeResult<String> =
        http
            .post()
            .uri("/issuance-requests")
            .headers { it.addAll(headers(key)) }
            .body(payload)
            .exchange()
            .returnResult(String::class.java)

    protected fun retrieve(id: String): EntityExchangeResult<String> =
        http
            .get()
            .uri("/issuance-requests/$id")
            .headers { it.addAll(headers()) }
            .exchange()
            .returnResult(String::class.java)

    protected fun capturar(
        regex: String,
        value: String,
        field: String,
    ): String = requireNotNull(Regex(regex).find(value)) { "Resposta sem $field" }.groupValues[1]

    protected fun emitirAteFinal(payload: String = PEDIDO): String {
        val accepted = postar(payload)
        assertEquals(HttpStatus.ACCEPTED, accepted.status, "Resposta: ${accepted.responseBody.orEmpty()}")
        val id = capturar("\"id\":\"([^\"]+)\"", accepted.responseBody.orEmpty(), "id de solicitação")
        await.atMost(30, SECONDS).untilAsserted { assertTrue(retrieve(id).responseBody.orEmpty().contains("EMITIDA")) }
        return retrieve(id).responseBody.orEmpty()
    }

    protected fun headers(key: String? = null): HttpHeaders =
        HttpHeaders().apply {
            contentType = MediaType.APPLICATION_JSON
            set("X-Client-Id", CLIENTE)
            key?.let { set("Idempotency-Key", it) }
        }

    companion object {
        const val CLIENTE = "cliente-e2e"
        const val PEDIDO = """
            {"recipient":{"person_type":"FISICA","document":"52998224725"},
             "items":[{"code":"SKU-001","description":"Teclado","unit_price":100.005,"quantity":2},
                      {"code":"SKU-002","description":"Monitor","unit_price":350.00,"quantity":1}],
             "freight_value":50.00,
             "addresses":[{"purpose":"ENTREGA","region":"SUDESTE"}]}
        """

        @JvmStatic val writeOp = PostgreSQLContainer("postgres:17")

        @JvmStatic val reading = PostgreSQLContainer("postgres:17")

        @JvmStatic val kafka = KafkaContainer(DockerImageName.parse("apache/kafka:4.0.1"))

        @DynamicPropertySource
        @JvmStatic
        fun infraestrutura(registry: DynamicPropertyRegistry) {
            writeOp.start()
            reading.start()
            kafka.start()
            registry.add("app.datasource.write.url") { urlComSchema(writeOp) }
            registry.add("app.datasource.write.username", writeOp::getUsername)
            registry.add("app.datasource.write.password", writeOp::getPassword)
            registry.add("app.datasource.read.url") { urlComSchema(reading) }
            registry.add("app.datasource.read.username", reading::getUsername)
            registry.add("app.datasource.read.password", reading::getPassword)
            registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers)
            Flyway
                .configure()
                .dataSource(reading.jdbcUrl, reading.username, reading.password)
                .schemas("notafiscal")
                .defaultSchema("notafiscal")
                .locations("classpath:db/migration")
                .load()
                .migrate()
        }

        private fun urlComSchema(container: PostgreSQLContainer): String {
            val url = container.jdbcUrl
            val separator = if ('?' in url) '&' else '?'
            return "$url${separator}currentSchema=notafiscal"
        }
    }
}

@Tag("e2e")
class InvoiceIssuanceE2ETest : InvoiceIssuanceE2EBase() {
    @Test
    fun `emite nota PF do pedido ate a consulta da nota`() {
        val status = emitirAteFinal()
        assertTrue(status.contains("EMITIDA"))
        val number = capturar("\"number\":(\\d+)", status, "número fiscal")
        val invoice =
            http
                .get()
                .uri("/invoices/$number?series=1")
                .headers { it.addAll(headers()) }
                .exchange()
                .returnResult(String::class.java)
        assertEquals(HttpStatus.OK, invoice.status)
        assertTrue(invoice.responseBody.orEmpty().contains("668.40"))
        assertTrue(invoice.responseBody.orEmpty().contains("100.00"))
        assertTrue(invoice.responseBody.orEmpty().contains("***.982.247-**"))
    }

    @Test
    fun `emite nota PJ para cada regime suportado`() {
        val scenarios = listOf("SIMPLES_NACIONAL" to "0.13", "LUCRO_REAL" to "0.15", "LUCRO_PRESUMIDO" to "0.16")
        scenarios.forEach { (regime, taxRate) ->
            val order =
                PEDIDO
                    .replace("FISICA", "JURIDICA")
                    .replace("52998224725", "11222333000181")
                    .replace("350.00", "2300.00")
                    .replace("\"document\":\"11222333000181\"}", "\"document\":\"11222333000181\",\"tax_regime\":\"$regime\"}")
            val status = emitirAteFinal(order)
            val number = capturar("\"number\":(\\d+)", status, "número fiscal")
            val invoice =
                http
                    .get()
                    .uri("/invoices/$number?series=1")
                    .headers { it.addAll(headers()) }
                    .exchange()
                    .returnResult(String::class.java)
                    .responseBody
                    .orEmpty()
            val receivedTaxRate = Regex("\"tax_rate\":([0-9.]+)").find(invoice)?.groupValues?.get(1)
            assertEquals(
                0,
                receivedTaxRate?.toBigDecimal()?.compareTo(taxRate.toBigDecimal()),
                "Alíquota divergente para $regime: $invoice",
            )
        }
    }

    @Test
    fun `reenvio com a mesma chave devolve a mesma solicitacao`() {
        val key = UUID.randomUUID().toString()
        val firstItem = postar(key = key)
        val secondItem = postar(key = key)
        val id1 = capturar("\"id\":\"([^\"]+)\"", firstItem.responseBody.orEmpty(), "id de solicitação")
        val id2 = capturar("\"id\":\"([^\"]+)\"", secondItem.responseBody.orEmpty(), "id de solicitação")
        assertEquals(HttpStatus.ACCEPTED, firstItem.status)
        assertEquals(id1, id2)
        assertEquals(1, jdbc.sql("SELECT count(*) FROM solicitacao").query(Int::class.java).single())
    }

    @Test
    fun `consulta imediata apos o aceite nunca retorna 404`() {
        repeat(300) {
            val accepted = postar()
            val id = capturar("\"id\":\"([^\"]+)\"", accepted.responseBody.orEmpty(), "id de solicitação")
            assertEquals(HttpStatus.OK, retrieve(id).status)
        }
    }

    @Test
    fun `quatro sistemas sao notificados da nota emitida`() {
        val status = emitirAteFinal()
        val number = capturar("\"number\":(\\d+)", status, "número fiscal").toInt()
        await.atMost(30, SECONDS).untilAsserted {
            val receivedItems =
                jdbc
                    .sql("SELECT count(DISTINCT consumidor) FROM notificacao_recebida WHERE numero=:numero")
                    .param("numero", number)
                    .query(Int::class.java)
                    .single()
            assertEquals(4, receivedItems)
        }
    }

    @Test
    fun `aceita 500 itens e rejeita 501`() {
        val items = (1..500).joinToString(",") { "{\"code\":\"SKU-$it\",\"description\":\"Item\",\"unit_price\":1,\"quantity\":1}" }
        val order = Regex("\\[.*?\\]", RegexOption.DOT_MATCHES_ALL).replaceFirst(PEDIDO, "[$items]")
        val status = emitirAteFinal(order)
        assertTrue(status.contains("EMITIDA"))
        val largeOrder =
            order.replace(
                "$items]",
                "$items,{\"code\":\"SKU-501\",\"description\":\"Item\",\"unit_price\":1,\"quantity\":1}]",
            )
        val rejection = postar(largeOrder)
        assertEquals(HttpStatus.UNPROCESSABLE_CONTENT, rejection.status, "Resposta: ${rejection.responseBody.orEmpty()}")
    }
}

@Tag("e2e")
@TestPropertySource(
    properties = [
        "nota-fiscal.tributacao.modo=POR_DENTRO",
        "nota-fiscal.kafka.topico-solicitacoes=teste.por-dentro.solicitacoes.v1",
        "nota-fiscal.kafka.topico-solicitacoes-dlq=teste.por-dentro.solicitacoes.v1.dlq",
        "nota-fiscal.kafka.topico-notas=teste.por-dentro.notas.v1",
    ],
)
class InclusiveModeE2ETest : InvoiceIssuanceE2EBase() {
    @Test
    fun `emite nota no modo por dentro configurado`() {
        val status = emitirAteFinal()
        val number = capturar("\"number\":(\\d+)", status, "número fiscal")
        val invoice =
            http
                .get()
                .uri("/invoices/$number?series=1")
                .headers { it.addAll(headers()) }
                .exchange()
                .returnResult(String::class.java)
                .responseBody
                .orEmpty()
        assertTrue(invoice.contains("\"invoice_total\":602.40"))
        assertTrue(invoice.contains("POR_DENTRO"))
    }
}
