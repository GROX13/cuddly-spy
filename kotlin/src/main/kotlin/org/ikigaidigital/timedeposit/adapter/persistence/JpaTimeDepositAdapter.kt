package org.ikigaidigital.timedeposit.adapter.persistence

import org.ikigaidigital.timedeposit.application.TimeDepositRepository
import org.ikigaidigital.timedeposit.application.TimeDepositSnapshot
import org.ikigaidigital.timedeposit.domain.TimeDepositStatement
import org.ikigaidigital.timedeposit.domain.Withdrawal
import org.springframework.stereotype.Repository
import java.math.BigDecimal

/**
 * Maps Spring Data's entities to the application's values (ADR 0006). `@Repository` also translates
 * caller errors into Spring's `DataAccessException`. The application owns the transaction spanning
 * loading, calculation and saving; Spring Data supplies the database operations.
 */
@Repository
class JpaTimeDepositAdapter(private val repository: SpringDataTimeDepositRepository) : TimeDepositRepository {

    /**
     * One query: Hibernate keeps one root per deposit, and `@OrderBy` sorts the fetched withdrawals.
     * The stored NUMERIC(19,2) balance goes out as is; it never passes through a Double (ADR 0002).
     */
    override fun loadAllWithWithdrawals(): List<TimeDepositStatement> =
        repository.findAllWithWithdrawals()
            .map { entity ->
                TimeDepositStatement(
                    id = entity.id,
                    planType = entity.planType,
                    balance = entity.balance,
                    days = entity.days,
                    withdrawals = entity.withdrawals.map { it.toDomain() },
                )
            }

    /**
     * `select … for no key update` (Hibernate 7.4's PESSIMISTIC_WRITE on PostgreSQL): a concurrent
     * balance update waits until this transaction ends, so no monthly run reads a balance another run
     * is about to overwrite (ADR 0007).
     */
    override fun loadAllForUpdate(): List<TimeDepositSnapshot> =
        repository.findAllForUpdate()
            .map { it.toSnapshot() }

    /**
     * Assigns final decimal balances to managed entities; Hibernate's dirty checking writes the changed
     * ones at flush, so no `save` is needed. After [loadAllForUpdate], `findById` resolves them from the
     * same persistence context without another SELECT. Missing ids fail rather than being skipped.
     */
    override fun saveBalances(balances: Map<Int, BigDecimal>) {
        balances.forEach { (id, balance) ->
            val entity = requireNotNull(repository.findById(id).orElse(null)) {
                "No time deposit with id $id"
            }
            entity.balance = balance
        }
    }
}

private fun TimeDepositEntity.toSnapshot() = TimeDepositSnapshot(id, planType, balance, days)

private fun WithdrawalEntity.toDomain() = Withdrawal(id, amount, date)
