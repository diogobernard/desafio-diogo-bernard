package com.itau.desafio_diogo_bernard.repository

import com.itau.desafio_diogo_bernard.repository.entity.InvoiceItemEntity
import com.itau.desafio_diogo_bernard.repository.entity.InvoiceItemId
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface InvoiceItemRepository : JpaRepository<InvoiceItemEntity, InvoiceItemId> {
    @Query(
        """
        select i from InvoiceItemEntity i
        where i.itemKey.series = :serie and i.itemKey.number = :numero
        order by i.itemKey.sequence
        """,
    )
    fun findItems(
        @Param("serie") series: Int,
        @Param("numero") number: Int,
    ): List<InvoiceItemEntity>
}
