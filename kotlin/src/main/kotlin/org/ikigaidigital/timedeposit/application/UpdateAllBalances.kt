package org.ikigaidigital.timedeposit.application

import org.ikigaidigital.timedeposit.domain.TimeDepositCalculator
import org.ikigaidigital.timedeposit.domain.TimeDepositWithWithdrawals
import org.springframework.transaction.annotation.Transactional

/**
 * Use case: adds one monthly interest to every time deposit and returns all of them, in the same
 * shape as [ListTimeDeposits]. Every call adds another month; there is no idempotency guard
 * (ADR 0008). Lock, calculate, save and re-read run in one transaction (ADR 0014): the rows stay
 * locked until commit, so a concurrent update waits instead of overwriting this one, and the list
 * returned is exactly the state this call produced.
 *
 * `@Transactional` sits on the class, not the method: the Kotlin `spring` compiler plugin only opens
 * classes annotated this way, and Spring needs an open class to build the transactional proxy.
 */
@Transactional
class UpdateAllBalances(
    private val repository: TimeDepositRepository,
    private val calculator: TimeDepositCalculator,
) {
    fun execute(): List<TimeDepositWithWithdrawals> {
        val deposits = repository.loadAllForUpdate()
        calculator.updateBalance(deposits) // the frozen legacy calculator mutates the balances in place
        repository.saveBalances(deposits)
        return repository.loadAllWithWithdrawals()
    }
}
