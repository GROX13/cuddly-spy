package org.ikigaidigital.timedeposit.domain

import java.math.BigDecimal

/**
 * Read model, like a bank statement: a time deposit's stored, exact state (its [balance] to the cent)
 * with its withdrawals, oldest first. It is what both endpoints return. [org.ikigaidigital.TimeDeposit] is the frozen
 * contract (ADR 0001); its Double can't hold every stored cent, so it exists only as the calculator's
 * input and never on the way out (ADR 0002).
 */
data class TimeDepositStatement(
    val id: Int,
    val planType: String,
    val balance: BigDecimal,
    val days: Int,
    val withdrawals: List<Withdrawal>,
)
