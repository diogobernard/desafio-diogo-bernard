package com.itau.desafio_diogo_bernard.repository.entity

import com.itau.desafio_diogo_bernard.model.nota.CalculatedInvoice
import com.itau.desafio_diogo_bernard.model.pedido.InvoiceRecipient
import com.itau.desafio_diogo_bernard.model.pedido.PersonType
import com.itau.desafio_diogo_bernard.model.pedido.Region
import com.itau.desafio_diogo_bernard.model.pedido.TaxRegime
import jakarta.persistence.Column
import jakarta.persistence.Embeddable
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import java.math.BigDecimal

@Embeddable
class InvoiceRecipientColumns(
    @Enumerated(EnumType.STRING)
    @Column(name = "destinatario_tipo_pessoa", nullable = false, length = 8)
    var personType: PersonType = PersonType.FISICA,
    @Column(name = "destinatario_documento", nullable = false, length = 20)
    var maskedDocument: String = "",
    @Enumerated(EnumType.STRING)
    @Column(name = "destinatario_regime", length = 20)
    var taxRegime: TaxRegime? = null,
) {
    fun toDomain(): InvoiceRecipient = InvoiceRecipient(personType, maskedDocument, taxRegime)

    companion object {
        fun fromDomain(recipient: InvoiceRecipient): InvoiceRecipientColumns =
            InvoiceRecipientColumns(
                recipient.personType,
                recipient.maskedDocument,
                recipient.taxRegime,
            )
    }
}

@Embeddable
class InvoiceFreightColumns(
    @Column(name = "frete_informado", nullable = false, precision = 15, scale = 2)
    var providedValue: BigDecimal = BigDecimal.ZERO,
    @Enumerated(EnumType.STRING)
    @Column(name = "frete_regiao", nullable = false, length = 12)
    var region: Region = Region.SUDESTE,
    @Column(name = "frete_percentual", nullable = false, precision = 6, scale = 4)
    var percentage: BigDecimal = BigDecimal.ZERO,
) {
    companion object {
        fun fromDomain(invoice: CalculatedInvoice): InvoiceFreightColumns =
            InvoiceFreightColumns(invoice.freightValue.value, invoice.deliveryRegion, invoice.freightPercentage.value)
    }
}
