package org.ikigaidigital.timedeposit.application

import org.ikigaidigital.timedeposit.domain.TimeDepositCalculator
import org.ikigaidigital.timedeposit.domain.TimeDepositStatement
import org.springframework.transaction.annotation.Transactional

/**
 * The application's use cases on time deposits, one method per endpoint (ADR 0016). A plain class,
 * wired in `ApplicationConfiguration` (ADR 0011).
 *
 * `@Transactional` sits on the class: the Kotlin `spring` compiler plugin opens only classes that
 * carry it (a method-level annotation leaves the class final), and Spring needs an open class to build
 * the transactional proxy (ADR 0014). Spring's proxy rules apply: a call from inside this class
 * bypasses the proxy, so the methods don't call each other.
 */
@Transactional
class TimeDepositService(
    private val repository: TimeDepositRepository,
    private val calculator: TimeDepositCalculator,
) {

    /**
     * Every time deposit with its withdrawals (deposits by id; withdrawals by date, then id). Read-only,
     * in a transaction of its own: the repository has none, and the fetch-join and mapping run inside it.
     */
    @Transactional(readOnly = true)
    fun listAll(): List<TimeDepositStatement> = repository.loadAllWithWithdrawals()

    /**
     * Adds one monthly interest to every time deposit and returns all of them, in the same shape as
     * [listAll]. Every call adds another month; there is no idempotency guard (ADR 0008). Lock,
     * calculate, save and re-read run in one transaction (ADR 0014): the rows stay locked until commit,
     * so a concurrent update waits instead of overwriting this one, and the list returned is exactly the
     * state this call produced.
     */
    fun updateAllBalances(): List<TimeDepositStatement> {
        val deposits = repository.loadAllForUpdate()
        calculator.updateBalance(deposits) // the frozen legacy calculator mutates the balances in place
        repository.saveBalances(deposits)
        return repository.loadAllWithWithdrawals()
    }
}
