package com.itau.desafio_diogo_bernard.entrypoint.processamento

import com.itau.desafio_diogo_bernard.config.ObservabilityCorrelation
import com.itau.desafio_diogo_bernard.entrypoint.model.IssuanceRequestReceivedEvent
import com.itau.desafio_diogo_bernard.model.solicitacao.IssuanceRequestId
import com.itau.desafio_diogo_bernard.usecase.processamento.ProcessIssuanceRequest
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component
import tools.jackson.databind.json.JsonMapper

@Component
@ConditionalOnExpression("'\${app.runtime.role:combined}' != 'api'")
class IssuanceRequestReceivedListener(
    private val json: JsonMapper,
    private val process: ProcessIssuanceRequest,
    private val correlation: ObservabilityCorrelation,
) {
    @KafkaListener(topics = ["\${nota-fiscal.kafka.topico-solicitacoes}"])
    fun receive(payload: String) {
        val event = json.readValue(payload, IssuanceRequestReceivedEvent::class.java)
        correlation.execute("worker", event.requestId) {
            process.execute(IssuanceRequestId(event.requestId))
        }
    }
}
