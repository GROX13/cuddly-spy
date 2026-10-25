package org.ikigaidigital.timedeposit.application

import org.ikigaidigital.timedeposit.domain.TimeDepositCalculator
import org.ikigaidigital.timedeposit.domain.TimeDepositWithWithdrawals

/**
 * Use case: adds one monthly interest to every time deposit and returns all of them, in the same
 * shape as [ListTimeDeposits]. Every call adds another month; there is no idempotency guard
 * (ADR 0008). Lock, calculate, save and re-read run in one transaction (ADR 0012): the rows stay
 * locked until commit, so a concurrent update waits instead of overwriting this one, and the list
 * returned is exactly the state this call produced.
 */
class UpdateAllBalances(
    private val repository: TimeDepositRepository,
    private val calculator: TimeDepositCalculator,
    private val transactions: Transactions,
) {
    fun execute(): List<TimeDepositWithWithdrawals> = transactions.inTransaction {
        val deposits = repository.loadAllForUpdate()
        calculator.updateBalance(deposits) // the frozen legacy calculator mutates the balances in place
        repository.saveBalances(deposits)
        repository.loadAllWithWithdrawals()
    }
}
