package com.itau.desafio_diogo_bernard.entrypoint

import org.springframework.jdbc.core.simple.JdbcClient
import java.util.UUID

internal fun JdbcClient.inserirEventos(
    topic: String,
    quantity: Int,
    payload: () -> String,
): Unit =
    repeat(quantity) {
        sql(
            "INSERT INTO outbox (evento_id, topico, chave, payload, criado_em) " +
                "VALUES (:evento, :topico, :chave, CAST(:payload AS jsonb), now())",
        ).param("evento", UUID.randomUUID())
            .param("topico", topic)
            .param("chave", UUID.randomUUID().toString())
            .param("payload", payload())
            .update()
    }

internal fun JdbcClient.contarEventos(
    topic: String,
    published: Boolean,
): Int {
    val condition = if (published) "IS NOT NULL" else "IS NULL"
    return sql("SELECT count(*) FROM outbox WHERE topico = :topico AND publicado_em $condition")
        .param("topico", topic)
        .query(Long::class.java)
        .single()
        .toInt()
}
