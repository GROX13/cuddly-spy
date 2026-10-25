package org.ikigaidigital.timedeposit.application

import java.math.BigDecimal

/** Exact stored inputs for a balance update, captured while the time deposit is locked. */
data class TimeDepositSnapshot(
    val id: Int,
    val planType: String,
    val balance: BigDecimal,
    val days: Int,
)
