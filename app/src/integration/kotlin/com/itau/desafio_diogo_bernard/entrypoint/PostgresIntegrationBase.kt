package com.itau.desafio_diogo_bernard.entrypoint

import org.awaitility.kotlin.await
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.BeforeEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.context.annotation.Import
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.client.RestTestClient
import org.testcontainers.kafka.KafkaContainer
import org.testcontainers.postgresql.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import java.util.UUID
import java.util.concurrent.TimeUnit.SECONDS
import javax.sql.DataSource
import kotlin.test.assertEquals

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(InMemoryTraceExporter::class)
abstract class PostgresIntegrationBase {
    @LocalServerPort
    private var port: Int = 0

    protected lateinit var http: RestTestClient

    @Autowired
    @Qualifier("dataSource")
    private lateinit var dataSource: DataSource

    protected lateinit var jdbc: JdbcClient

    @BeforeEach
    fun limparBanco() {
        jdbc = JdbcClient.create(dataSource)
        http = RestTestClient.bindToServer().baseUrl("http://localhost:$port").build()
        aguardarSolicitacoesConcluidas()
        jdbc.sql("DELETE FROM nota_fiscal_item").update()
        jdbc.sql("DELETE FROM nota_fiscal").update()
        jdbc.sql("DELETE FROM notificacao_recebida").update()
        jdbc.sql("DELETE FROM outbox").update()
        jdbc.sql("DELETE FROM solicitacao").update()
        jdbc.sql("UPDATE contador_numeracao SET ultimo_numero=0").update()
    }

    protected fun aguardarSolicitacoesConcluidas() {
        await.atMost(60, SECONDS).untilAsserted {
            val pending =
                jdbc
                    .sql("SELECT count(*) FROM solicitacao WHERE status IN ('RECEBIDA','PROCESSANDO')")
                    .query(Int::class.java)
                    .single()
            assertEquals(0, pending, "Solicitações de testes anteriores ainda estão em processamento")
        }
    }

    protected fun headers(
        key: String? = null,
        client: String = CLIENTE,
    ): HttpHeaders =
        HttpHeaders().apply {
            contentType = MediaType.APPLICATION_JSON
            set("X-Client-Id", client)
            key?.let { set("Idempotency-Key", it) }
        }

    protected fun contar(table: String): Int =
        jdbc
            .sql("SELECT count(*) FROM $table")
            .query(Long::class.java)
            .single()
            .toInt()

    protected fun idResposta(body: String): String =
        Regex("\\\"id\\\":\\\"([^\\\"]+)\\\"")
            .find(body)
            ?.groupValues
            ?.get(1) ?: error("Resposta sem id de solicitação")

    protected fun criarSolicitacaoParaConsumidores(): String {
        val response =
            http
                .post()
                .uri("/issuance-requests")
                .headers { it.addAll(headers(UUID.randomUUID().toString())) }
                .body(PAYLOAD)
                .exchange()
                .returnResult(String::class.java)
        return idResposta(response.responseBody.orEmpty())
    }

    companion object {
        private val MAX_CONEXOES = arrayOf("postgres", "-c", "max_connections=400")
        const val CLIENTE = "cliente-a"
        const val KEY = "6f1c2b7e-3a4d-4e5f-8a9b-0c1d2e3f4a5b"
        const val PAYLOAD = """
            {"recipient":{"person_type":"FISICA","document":"52998224725"},
            "items":[{"code":"SKU-1","description":"Produto","unit_price":12.50,"quantity":1}],
            "freight_value":2.00,"addresses":[{"purpose":"ENTREGA","region":"SUDESTE"}]}
        """

        /** O mesmo pedido de [PAYLOAD] no formato gravado na coluna `pedido`, independente do contrato HTTP. */
        const val PEDIDO_GRAVADO = """
            {"destinatario":{"tipo_pessoa":"FISICA","documento":"52998224725","regime_tributacao":null},
            "itens":[{"codigo":"SKU-1","descricao":"Produto","valor_unitario":12.50,"quantidade":1}],
            "valor_frete":2.00,"enderecos":[{"finalidade":"ENTREGA","regiao":"SUDESTE"}]}
        """

        @JvmStatic
        val database = PostgreSQLContainer("postgres:17").withCommand(*MAX_CONEXOES)

        @JvmStatic
        val replica = PostgreSQLContainer("postgres:17").withCommand(*MAX_CONEXOES)

        @JvmStatic
        val kafka = KafkaContainer(DockerImageName.parse("apache/kafka:4.0.1"))

        @DynamicPropertySource
        @JvmStatic
        fun database(registry: DynamicPropertyRegistry) {
            registry.add("app.datasource.write.url") { urlComSchema(database) }
            registry.add("app.datasource.write.username", database::getUsername)
            registry.add("app.datasource.write.password", database::getPassword)
            registry.add("app.datasource.read.url") { urlComSchema(replica) }
            registry.add("app.datasource.read.username", replica::getUsername)
            registry.add("app.datasource.read.password", replica::getPassword)
            registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers)
            registry.add("spring.jpa.properties.hibernate.session_factory.statement_inspector") {
                SqlStatementRecorder::class.java.name
            }
            registry.add("spring.jpa.properties.hibernate.generate_statistics") { true }
            registry.add("logging.level.org.apache.kafka") { "WARN" }
            registry.add("logging.level.org.springframework.kafka") { "WARN" }
            database.start()
            replica.start()
            kafka.start()
            Flyway
                .configure()
                .dataSource(replica.jdbcUrl, replica.username, replica.password)
                .schemas("notafiscal")
                .defaultSchema("notafiscal")
                .locations("classpath:db/migration")
                .load()
                .migrate()
        }

        private fun urlComSchema(container: PostgreSQLContainer): String {
            val url = container.getJdbcUrl()
            val separator = if ('?' in url) '&' else '?'
            return "${url}${separator}currentSchema=notafiscal"
        }
    }
}
