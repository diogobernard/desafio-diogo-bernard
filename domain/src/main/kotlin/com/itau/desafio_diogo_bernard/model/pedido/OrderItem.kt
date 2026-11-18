package com.itau.desafio_diogo_bernard.model.pedido

import com.itau.desafio_diogo_bernard.model.dinheiro.Money
import java.math.BigDecimal

data class OrderItem(
    val code: String,
    val description: String,
    val unitValue: Money,
    val quantity: Int,
    val providedUnitValue: BigDecimal = unitValue.value,
) {
    init {
        require(code.isNotBlank()) { "O código do item é obrigatório" }
        require(description.isNotBlank()) { "A descrição do item é obrigatória" }
        require(unitValue >= Money.ZERO) { "O valor unitário não pode ser negativo" }
        require(providedUnitValue >= BigDecimal.ZERO) { "O valor unitário informado não pode ser negativo" }
        require(quantity > 0) { "A quantidade deve ser positiva" }
    }

    fun total(): Money = unitValue * quantity
}
