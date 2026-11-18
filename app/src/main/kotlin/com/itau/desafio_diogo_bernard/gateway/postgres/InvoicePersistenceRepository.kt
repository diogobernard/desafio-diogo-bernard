package com.itau.desafio_diogo_bernard.gateway.postgres

import com.itau.desafio_diogo_bernard.model.nota.Invoice
import com.itau.desafio_diogo_bernard.model.nota.InvoiceReference
import com.itau.desafio_diogo_bernard.repository.InvoiceItemRepository
import com.itau.desafio_diogo_bernard.repository.entity.InvoiceEntity
import com.itau.desafio_diogo_bernard.repository.entity.InvoiceId
import com.itau.desafio_diogo_bernard.repository.entity.InvoiceItemEntity
import org.springframework.stereotype.Component
import com.itau.desafio_diogo_bernard.repository.InvoiceRepository as Notas

@Component
class InvoicePersistenceRepository(
    private val invoices: Notas,
    private val items: InvoiceItemRepository,
) : InvoicePersistence {
    override fun store(invoice: Invoice) {
        val reference = invoice.reference
        invoices.save(InvoiceEntity.fromDomain(invoice))
        items.saveAll(
            invoice.calculated.items.mapIndexed { index, item ->
                InvoiceItemEntity.fromDomain(reference.series.value, reference.number.value, index + 1, item)
            },
        )
    }

    override fun find(reference: InvoiceReference): Invoice? {
        val series = reference.series.value
        val number = reference.number.value
        val invoice = invoices.findById(InvoiceId(series, number)).orElse(null) ?: return null
        return invoice.toDomain(items.findItems(series, number).map(InvoiceItemEntity::toDomain))
    }
}
