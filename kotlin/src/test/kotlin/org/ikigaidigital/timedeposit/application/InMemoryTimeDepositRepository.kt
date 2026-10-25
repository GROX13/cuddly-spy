package org.ikigaidigital.timedeposit.application

import org.ikigaidigital.TimeDeposit
import org.ikigaidigital.timedeposit.domain.TimeDepositWithWithdrawals

/**
 * A fake port for tests: it hands back exactly the read models it holds, in that order. The
 * balance-update methods belong to a later use case and aren't needed yet.
 */
class InMemoryTimeDepositRepository(
    var deposits: List<TimeDepositWithWithdrawals> = emptyList(),
) : TimeDepositRepository {

    override fun loadAllWithWithdrawals(): List<TimeDepositWithWithdrawals> = deposits

    override fun loadAllForUpdate(): List<TimeDeposit> = throw UnsupportedOperationException("not used yet")

    override fun saveBalances(deposits: List<TimeDeposit>): Unit = throw UnsupportedOperationException("not used yet")
}
