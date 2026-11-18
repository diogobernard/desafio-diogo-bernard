@file:Suppress("PackageNaming")

package com.itau.desafio_diogo_bernard.gateway.postgres.lote

import com.itau.desafio_diogo_bernard.repository.IssuanceRequestInsert
import org.junit.jupiter.api.Test
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.jdbc.CannotGetJdbcConnectionException
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class IssuanceRequestBatchWriterTest {
    private val calls = mutableListOf<List<IssuanceRequestInsert>>()

    @Test
    fun `grava o lote em um comando e informa criada ou existente por item`() {
        val orders = List(3) { umPedido() }
        val writer = writer { batch -> batch.map { it !== orders[1].insert } }

        writer.write(orders)

        assertEquals(listOf(orders.map { it.insert }), calls)
        assertEquals(listOf(true, false, true), orders.map { it.await(PRAZO) })
    }

    @Test
    fun `descarta itens cancelados e nao chama o banco quando o lote fica vazio`() {
        val cancelled = umPedido().also { runCatching { it.await(Duration.ZERO) } }
        val valid = umPedido()
        val writer = writer { batch -> batch.map { true } }

        writer.write(listOf(cancelled))
        writer.write(listOf(cancelled, valid))

        assertEquals(listOf(listOf(valid.insert)), calls)
        assertTrue(valid.await(PRAZO))
    }

    @Test
    fun `grava individualmente quando o lote falha por dado invalido e isola o item defeituoso`() {
        val orders = List(3) { umPedido() }
        val defective = orders[1].insert
        val failure = DataIntegrityViolationException("dado invalido")
        val writer = writer { batch -> if (defective in batch) throw failure else batch.map { true } }

        writer.write(orders)

        assertEquals(listOf(3, 1, 1, 1), calls.map { it.size })
        assertTrue(orders[0].await(PRAZO))
        assertTrue(orders[2].await(PRAZO))
        assertSame(failure, assertFailsWith<DataIntegrityViolationException> { orders[1].await(PRAZO) })
    }

    @Test
    fun `falha todos os itens do lote quando o banco esta indisponivel`() {
        val orders = List(2) { umPedido() }
        val failure = CannotGetJdbcConnectionException("indisponivel")
        val writer = writer { throw failure }

        writer.write(orders)

        assertEquals(1, calls.size)
        orders.forEach { assertSame(failure, assertFailsWith<CannotGetJdbcConnectionException> { it.await(PRAZO) }) }
    }

    @Test
    fun `nao repete a gravacao de um unico item que falha por dado invalido`() {
        val order = umPedido()
        val writer = writer { throw DataIntegrityViolationException("dado invalido") }

        writer.write(listOf(order))

        assertEquals(1, calls.size)
        assertFailsWith<DataIntegrityViolationException> { order.await(PRAZO) }
        assertFalse(order.claim())
    }

    private fun writer(action: (List<IssuanceRequestInsert>) -> List<Boolean>): IssuanceRequestBatchWriter =
        IssuanceRequestBatchWriter(
            object : IssuanceRequestBatchPersister {
                override fun persist(batch: List<IssuanceRequestInsert>): List<Boolean> {
                    calls.add(batch)
                    return action(batch)
                }
            },
        )

    private companion object {
        val PRAZO: Duration = Duration.ofSeconds(1)
    }
}
