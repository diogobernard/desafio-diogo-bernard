package com.itau.desafio_diogo_bernard.entrypoint

import com.itau.desafio_diogo_bernard.gateway.InvoiceReadGateway
import com.itau.desafio_diogo_bernard.model.nota.FiscalNumber
import com.itau.desafio_diogo_bernard.model.nota.Series
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import kotlin.test.assertEquals
import kotlin.test.assertNull

@Tag("integration")
class CounterReadCacheIntegrationTest : PostgresIntegrationBase() {
    @Autowired
    private lateinit var invoices: InvoiceReadGateway

    @Test
    fun `usa valor em cache e consulta quando numero solicitado excede o cache`() {
        gravarContador(Series(997), 5)

        assertEquals(FiscalNumber(5), invoices.lastIssuedNumber(Series(997), FiscalNumber(5)))
        assertEquals(FiscalNumber(5), invoices.lastIssuedNumber(Series(997), FiscalNumber(4)))
        assertEquals(FiscalNumber(5), invoices.lastIssuedNumber(Series(997), FiscalNumber(6)))
        assertEquals(FiscalNumber(5), invoices.lastIssuedNumber(Series(997)))
        gravarContador(Series(998), 0)
        assertNull(invoices.lastIssuedNumber(Series(998)))
        assertNull(invoices.lastIssuedNumber(Series(998)))
        assertNull(invoices.lastIssuedNumber(Series(995)))
    }

    @Test
    fun `recarrega cache expirado e aceita numero maximo`() {
        gravarContador(Series(999), 999_999_999)
        assertEquals(FiscalNumber(999_999_999), invoices.lastIssuedNumber(Series(999), FiscalNumber(1)))
        Thread.sleep(2_100)

        assertEquals(FiscalNumber(999_999_999), invoices.lastIssuedNumber(Series(999), FiscalNumber(1)))
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
