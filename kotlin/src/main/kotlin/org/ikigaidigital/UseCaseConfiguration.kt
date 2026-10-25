package org.ikigaidigital

import org.ikigaidigital.timedeposit.application.ListTimeDeposits
import org.ikigaidigital.timedeposit.application.TimeDepositRepository
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
}
