package org.ikigaidigital.timedeposit.domain

import java.math.BigDecimal
import java.math.RoundingMode

class TimeDepositCalculator(plans: Set<PlanType> = planTypes) {
    private val plansByCode = plans.associateBy { it.code } // map built once from the active plans

    fun updateBalance(xs: List<TimeDeposit>) {
        xs.forEach { deposit ->
            // `0.0 +` mirrors the legacy accumulator: it turns a -0.0 interest into 0.0
            val interest = 0.0 + (plansByCode[deposit.planType]?.monthlyInterest(deposit.balance, deposit.days) ?: 0.0)
            deposit.balance += BigDecimal(interest).setScale(2, RoundingMode.HALF_UP).toDouble()
        }
    }
}
