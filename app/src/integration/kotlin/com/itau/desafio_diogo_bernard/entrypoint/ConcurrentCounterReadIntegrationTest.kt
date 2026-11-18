package com.itau.desafio_diogo_bernard.entrypoint

import com.itau.desafio_diogo_bernard.gateway.InvoiceReadGateway
import com.itau.desafio_diogo_bernard.model.nota.FiscalNumber
import com.itau.desafio_diogo_bernard.model.nota.Series
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit.SECONDS
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@Tag("integration")
class ConcurrentCounterReadIntegrationTest : PostgresIntegrationBase() {
    @Autowired
    private lateinit var invoices: InvoiceReadGateway

    @Autowired
    private lateinit var transactionManager: PlatformTransactionManager

    @Test
    fun `segunda leitura usa cache populado enquanto aguardava o lock`() {
        val series = Series(994)
        gravarContador(series, 5)
        val executor = Executors.newFixedThreadPool(3)
        val activeLock = CountDownLatch(1)
        val releaseLock = CountDownLatch(1)
        val futureLock =
            executor.submit {
                TransactionTemplate(transactionManager).executeWithoutResult {
                    jdbc.sql("LOCK TABLE contador_numeracao IN ACCESS EXCLUSIVE MODE").update()
                    activeLock.countDown()
                    releaseLock.await(10, SECONDS)
                }
            }

        try {
            assertTrue(activeLock.await(5, SECONDS))
            val first = executor.submit<FiscalNumber?> { invoices.lastIssuedNumber(series, FiscalNumber(1)) }
            Thread.sleep(150)
            val second = executor.submit<FiscalNumber?> { invoices.lastIssuedNumber(series, FiscalNumber(1)) }
            Thread.sleep(150)
            releaseLock.countDown()

            assertEquals(FiscalNumber(5), first.get(10, SECONDS))
            assertEquals(FiscalNumber(5), second.get(10, SECONDS))
        } finally {
            releaseLock.countDown()
            futureLock.get(10, SECONDS)
            executor.shutdownNow()
        }
    }

    private fun gravarContador(
        series: Series,
        number: Int,
    ) {
        jdbc
            .sql(
                "INSERT INTO contador_numeracao (serie, ultimo_numero) VALUES (:serie, :numero) " +
                    "ON CONFLICT (serie) DO UPDATE SET ultimo_numero=EXCLUDED.ultimo_numero",
            ).param("serie", series.value)
            .param("numero", number)
            .update()
    }
}
