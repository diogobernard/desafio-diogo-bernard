package com.itau.desafio_diogo_bernard.repository

import com.itau.desafio_diogo_bernard.repository.entity.InvoiceEntity
import com.itau.desafio_diogo_bernard.repository.entity.InvoiceId
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface InvoiceRepository : JpaRepository<InvoiceEntity, InvoiceId> {
    fun findByRequestId(requestId: UUID): InvoiceEntity?
}
