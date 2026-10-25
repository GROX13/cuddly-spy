package org.ikigaidigital.timedeposit.domain

import org.ikigaidigital.TimeDeposit

/**
 * Read model: a time deposit together with its withdrawals, oldest first. [TimeDeposit] is the frozen
 * contract and can't carry them itself (ADR 0001).
 */
data class TimeDepositWithWithdrawals(val deposit: TimeDeposit, val withdrawals: List<Withdrawal>)
