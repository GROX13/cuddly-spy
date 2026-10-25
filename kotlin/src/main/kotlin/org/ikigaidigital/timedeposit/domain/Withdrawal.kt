package org.ikigaidigital.timedeposit.domain

import java.math.BigDecimal
import java.time.LocalDate

/**
 * A historical record of an [amount] taken out of a time deposit on a [date]. The balance already
 * reflects it, so it never takes part in interest (ADR 0003).
 */
data class Withdrawal(val id: Int, val amount: BigDecimal, val date: LocalDate)
