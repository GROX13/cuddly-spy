package org.ikigaidigital

import org.ikigaidigital.timedeposit.application.ListTimeDeposits
import org.ikigaidigital.timedeposit.application.TimeDepositRepository
import org.ikigaidigital.timedeposit.application.Transactions
import org.ikigaidigital.timedeposit.application.UpdateAllBalances
import org.ikigaidigital.timedeposit.domain.TimeDepositCalculator
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Composition root for the use cases: they are plain Spring-free classes, created here from the
 * ports Spring provides (ADR 0011).
 */
@Configuration(proxyBeanMethods = false)
class UseCaseConfiguration {

    @Bean
    fun listTimeDeposits(repository: TimeDepositRepository) = ListTimeDeposits(repository)

    /** The frozen legacy calculator with its default plan types (ADR 0001, ADR 0009). */
    @Bean
    fun timeDepositCalculator() = TimeDepositCalculator()

    @Bean
    fun updateAllBalances(
        repository: TimeDepositRepository,
        calculator: TimeDepositCalculator,
        transactions: Transactions,
    ) = UpdateAllBalances(repository, calculator, transactions)
}
