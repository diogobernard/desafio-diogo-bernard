package com.itau.desafio_diogo_bernard.config

import com.itau.desafio_diogo_bernard.config.properties.NotaFiscalProperties
import com.itau.desafio_diogo_bernard.gateway.InvoiceReadGateway
import com.itau.desafio_diogo_bernard.gateway.IssuanceRequestGateway
import com.itau.desafio_diogo_bernard.gateway.IssuanceRequestIdGenerator
import com.itau.desafio_diogo_bernard.gateway.IssuanceRequestReadGateway
import com.itau.desafio_diogo_bernard.gateway.ProcessingGateway
import com.itau.desafio_diogo_bernard.model.nota.TaxationMode
import com.itau.desafio_diogo_bernard.usecase.calculo.CalculateInvoice
import com.itau.desafio_diogo_bernard.usecase.consulta.RetrieveInvoice
import com.itau.desafio_diogo_bernard.usecase.consulta.RetrieveInvoiceUseCase
import com.itau.desafio_diogo_bernard.usecase.consulta.RetrieveIssuanceRequest
import com.itau.desafio_diogo_bernard.usecase.consulta.RetrieveIssuanceRequestUseCase
import com.itau.desafio_diogo_bernard.usecase.emissao.RequestIssuance
import com.itau.desafio_diogo_bernard.usecase.emissao.RequestIssuanceUseCase
import com.itau.desafio_diogo_bernard.usecase.processamento.ProcessIssuanceRequest
import com.itau.desafio_diogo_bernard.usecase.processamento.ProcessIssuanceRequestUseCase
import com.itau.desafio_diogo_bernard.usecase.processamento.RecordIssuanceRequestFailure
import com.itau.desafio_diogo_bernard.usecase.processamento.RecordIssuanceRequestFailureUseCase
import com.itau.desafio_diogo_bernard.usecase.validations.ConsistentReadPolicy
import com.itau.desafio_diogo_bernard.usecase.validations.OrderFingerprint
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Clock

@Configuration
class UseCaseConfig {
    @Bean
    fun clock(): Clock = Clock.systemUTC()

    @Bean
    fun requestIssuance(
        gateway: IssuanceRequestGateway,
        generator: IssuanceRequestIdGenerator,
        orderFingerprint: OrderFingerprint,
        clock: Clock,
    ): RequestIssuance = RequestIssuanceUseCase(gateway, generator, orderFingerprint, clock)

    @Bean
    fun retrieveRequest(
        gateway: IssuanceRequestReadGateway,
        properties: NotaFiscalProperties,
        clock: Clock,
    ): RetrieveIssuanceRequest =
        RetrieveIssuanceRequestUseCase(
            gateway,
            ConsistentReadPolicy(properties.consulta.janelaLeituraConsistente, clock),
        )

    @Bean
    fun retrieveInvoice(gateway: InvoiceReadGateway): RetrieveInvoice = RetrieveInvoiceUseCase(gateway)

    @Bean
    fun orderFingerprint(): OrderFingerprint = OrderFingerprint()

    @Bean
    fun taxationMode(properties: NotaFiscalProperties): TaxationMode = TaxationMode.valueOf(properties.tributacao.modo)

    @Bean
    fun calculateInvoice(): CalculateInvoice = CalculateInvoice()

    @Bean
    fun processRequest(
        gateway: ProcessingGateway,
        calculator: CalculateInvoice,
        mode: TaxationMode,
    ): ProcessIssuanceRequest = ProcessIssuanceRequestUseCase(gateway, calculator, mode)

    @Bean
    fun recordRequestFailure(gateway: ProcessingGateway): RecordIssuanceRequestFailure = RecordIssuanceRequestFailureUseCase(gateway)
}
