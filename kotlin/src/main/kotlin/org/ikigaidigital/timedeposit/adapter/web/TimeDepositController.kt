package org.ikigaidigital.timedeposit.adapter.web

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.ArraySchema
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import org.ikigaidigital.timedeposit.application.ListTimeDeposits
import org.springframework.http.MediaType.APPLICATION_JSON_VALUE
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * Web adapter: the HTTP endpoints that drive the use cases. The API has exactly two operations
 * (AGENTS.md); SwaggerIT pins the set. Must not depend on the persistence adapter (ArchitectureTest).
 */
@RestController
@RequestMapping("/time-deposits")
@Tag(name = "Time deposits", description = "XA Bank time deposits and their withdrawals")
class TimeDepositController(private val listTimeDeposits: ListTimeDeposits) {

    @GetMapping(produces = [APPLICATION_JSON_VALUE])
    @Operation(
        summary = "List all time deposits",
        description = "Every time deposit ordered by id, each with its withdrawals ordered by date, then id. " +
            "Money is in two decimals; withdrawals are history and are already reflected in the balance.",
    )
    @ApiResponse(
        responseCode = "200",
        description = "All time deposits; an empty array when there are none",
        content = [
            Content(
                mediaType = APPLICATION_JSON_VALUE,
                array = ArraySchema(schema = Schema(implementation = TimeDepositResponse::class)),
            ),
        ],
    )
    fun listAll(): List<TimeDepositResponse> = listTimeDeposits.execute().map { it.toResponse() }
}
