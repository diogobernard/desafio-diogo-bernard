package com.itau.desafio_diogo_bernard.entrypoint

import com.itau.desafio_diogo_bernard.entrypoint.processamento.ProcessingFailureRecoverer
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals

@Tag("integration")
class GenericFailureRecovererIntegrationTest : PostgresIntegrationBase() {
    @Autowired
    private lateinit var recoverer: ProcessingFailureRecoverer

    @Test
    fun `registra motivo generico quando erro nao decorre de numeracao`() {
        val id = UUID.randomUUID()
        val now = Timestamp.from(Instant.parse("2026-11-14T18:00:00Z"))
        jdbc
            .sql(
                """INSERT INTO solicitacao
            (id,cliente_id,idempotency_key,impressao_digital,pedido,status,recebida_em,atualizada_em)
            VALUES (:id,:cliente,:chave,:digital,CAST(:pedido AS jsonb),'PROCESSANDO',:agora,:agora)""",
            ).param("id", id)
            .param("cliente", CLIENTE)
            .param("chave", UUID.randomUUID())
            .param("digital", "a".repeat(64))
            .param("pedido", PEDIDO_GRAVADO)
            .param("agora", now)
            .update()
        val payload = """{"evento_id":"${UUID.randomUUID()}","versao":1,"ocorrido_em":"2026-11-14T18:00:00Z","solicitacao_id":"$id"}"""
        val record = ConsumerRecord("notafiscal.solicitacoes.v1", 0, 0L, id.toString(), payload)

        recoverer.accept(record, IllegalStateException("falha simulada"))

        assertEquals(
            "Não foi possível processar a solicitação após as tentativas configuradas.",
            jdbc
                .sql("SELECT motivo_falha FROM solicitacao WHERE id=:id")
                .param("id", id)
                .query(String::class.java)
                .single(),
        )
    }
}
