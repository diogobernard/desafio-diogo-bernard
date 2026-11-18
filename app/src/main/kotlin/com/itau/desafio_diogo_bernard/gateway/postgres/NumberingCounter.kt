package com.itau.desafio_diogo_bernard.gateway.postgres

interface NumberingCounter {
    fun reserve(series: Int): Int
}
