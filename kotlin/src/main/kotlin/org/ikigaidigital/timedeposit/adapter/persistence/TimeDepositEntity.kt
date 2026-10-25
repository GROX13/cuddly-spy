package org.ikigaidigital.timedeposit.adapter.persistence

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.OneToMany
import jakarta.persistence.OrderBy
import jakarta.persistence.Table
import java.math.BigDecimal

/**
 * A row of `time_deposits`. A plain class, not a data class: generated equals/hashCode over mutable
 * fields and lazy collections misbehave with Hibernate proxies. Column names follow Boot's default
 * snake_case naming (`planType` -> `plan_type`). Only the balance is ever written, so the other columns are
 * left out of UPDATE statements.
 */
@Entity
@Table(name = "time_deposits")
class TimeDepositEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Int,
    @Column(updatable = false)
    val planType: String,
    @Column(updatable = false)
    val days: Int,
    @Column(precision = 19, scale = 2)
    var balance: BigDecimal,
) {
    @OneToMany(mappedBy = "timeDeposit", fetch = FetchType.LAZY)
    @OrderBy("date ASC, id ASC")
    val withdrawals: MutableList<WithdrawalEntity> = mutableListOf()
}
