package com.itau.desafio_diogo_bernard.repository.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table

@Entity
@Table(name = "contador_numeracao")
class NumberingCounterEntity(
    @Id @Column(name = "serie") var series: Int = 1,
    @Column(name = "ultimo_numero", nullable = false) var lastNumber: Int = 0,
)
