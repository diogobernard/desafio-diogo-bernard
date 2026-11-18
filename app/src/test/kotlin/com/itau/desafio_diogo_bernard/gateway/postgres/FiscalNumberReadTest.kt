package com.itau.desafio_diogo_bernard.gateway.postgres

import com.itau.desafio_diogo_bernard.config.BusinessMetrics
import com.itau.desafio_diogo_bernard.config.properties.NotaFiscalProperties
import com.itau.desafio_diogo_bernard.model.nota.FiscalNumber
import com.itau.desafio_diogo_bernard.repository.IssuanceRequestRepository
import com.itau.desafio_diogo_bernard.repository.NumberingCounterRepository
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import kotlin.test.assertEquals
import kotlin.test.assertNull

class FiscalNumberReadTest {
    @Test
    fun `aceita somente numeros fiscais dentro da faixa permitida`() {
        val gateway =
            ReadPostgresGateway(
                Mockito.mock(IssuanceRequestRepository::class.java),
                Mockito.mock(PersistedInvoice::class.java),
                Mockito.mock(NumberingCounterRepository::class.java),
                NotaFiscalProperties(),
                BusinessMetrics(SimpleMeterRegistry()),
            )

        with(gateway) {
            assertEquals(FiscalNumber(1), 1.toFiscalNumberOrNull())
            assertNull(0.toFiscalNumberOrNull())
            assertNull(1_000_000_000.toFiscalNumberOrNull())
        }
    }
}
