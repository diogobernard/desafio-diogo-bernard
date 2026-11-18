package com.itau.desafio_diogo_bernard.entrypoint

import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.common.serialization.StringDeserializer
import org.awaitility.kotlin.await
import org.junit.jupiter.api.Tag
import java.time.Duration
import java.util.UUID
import java.util.concurrent.TimeUnit.SECONDS
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@Tag("integration")
class ProcessingFailureIntegrationTest : PostgresIntegrationBase() {
    @Test
    fun `marca FALHA apos esgotar tentativas do worker`() {
        jdbc.sql("UPDATE contador_numeracao SET ultimo_numero=999999999 WHERE serie=1").update()
        val response =
            http
                .post()
                .uri("/issuance-requests")
                .headers { it.addAll(headers(UUID.randomUUID().toString())) }
                .body(PAYLOAD)
                .exchange()
                .returnResult(String::class.java)
        val id = idResposta(response.responseBody.orEmpty())

        await.atMost(20, SECONDS).untilAsserted {
            val status =
                jdbc
                    .sql("SELECT status FROM solicitacao WHERE id=:id")
                    .param("id", UUID.fromString(id))
                    .query(String::class.java)
                    .single()
            assertEquals("FALHA", status)
        }
        assertTrue(mensagemNaDlq(id))
        assertEquals(0, contar("nota_fiscal"))
    }

    private fun mensagemNaDlq(id: String): Boolean {
        val settings =
            mapOf(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG to kafka.bootstrapServers,
                ConsumerConfig.GROUP_ID_CONFIG to UUID.randomUUID().toString(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG to "earliest",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG to StringDeserializer::class.java,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG to StringDeserializer::class.java,
            )
        return KafkaConsumer<String, String>(settings).use { consumer ->
            consumer.subscribe(listOf("notafiscal.solicitacoes.v1.dlq"))
            await.atMost(20, SECONDS).until {
                consumer
                    .poll(Duration.ofMillis(500))
                    .records("notafiscal.solicitacoes.v1.dlq")
                    .any { it.value().contains(id) }
            }
            true
        }
    }
}
