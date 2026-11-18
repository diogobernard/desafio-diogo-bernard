package com.itau.desafio_diogo_bernard.entrypoint

import org.hibernate.resource.jdbc.spi.StatementInspector
import java.util.concurrent.CopyOnWriteArrayList

class SqlStatementRecorder : StatementInspector {
    override fun inspect(sql: String): String {
        statements.add(RecordedStatement(Thread.currentThread().id, sql))
        return sql
    }

    companion object {
        val statements = CopyOnWriteArrayList<RecordedStatement>()

        fun currentThreadStatements(): List<String> = statements.filter { it.threadId == Thread.currentThread().id }.map { it.sql }
    }

    data class RecordedStatement(
        val threadId: Long,
        val sql: String,
    )
}
