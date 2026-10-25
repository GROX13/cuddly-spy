package org.ikigaidigital.timedeposit.domain

data class TimeDeposit(
    val id: Int,
    val planType: String,
    var balance: Double,
    val days: Int
)