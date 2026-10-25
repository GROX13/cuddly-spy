package org.ikigaidigital.timedeposit.adapter.persistence

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.math.BigDecimal
import java.time.LocalDate

/** A row of `withdrawals`: history only, never written by this application (ADR 0003). */
@Entity
@Table(name = "withdrawals")
class WithdrawalEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Int,
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "time_deposit_id")
    // Owning side of TimeDepositEntity.withdrawals; private because nothing navigates back.
    private val timeDeposit: TimeDepositEntity,
    @Column(precision = 19, scale = 2)
    val amount: BigDecimal,
    val date: LocalDate,
)
