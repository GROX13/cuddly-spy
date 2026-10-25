package org.ikigaidigital.timedeposit.application

import org.ikigaidigital.timedeposit.domain.TimeDepositWithWithdrawals

/**
 * Use case: lists every time deposit with its withdrawals (deposits by id; withdrawals by date, then
 * id). A plain class, wired in `UseCaseConfiguration` (ADR 0011). It needs no transaction of its own:
 * the repository fetch-joins the withdrawals and maps inside Spring Data's transaction.
 */
class ListTimeDeposits(private val repository: TimeDepositRepository) {
    fun execute(): List<TimeDepositWithWithdrawals> = repository.loadAllWithWithdrawals()
}
