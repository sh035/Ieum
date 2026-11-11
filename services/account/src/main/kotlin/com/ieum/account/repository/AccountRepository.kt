package com.ieum.account.repository

import com.ieum.account.domain.entity.Account
import org.springframework.data.jpa.repository.JpaRepository
import java.time.LocalDateTime
import java.util.UUID

interface AccountRepository : JpaRepository<Account, UUID> {

	fun existsByEmailAndIsDeletedFalse(email: String): Boolean

	fun findByEmailAndIsDeletedFalse(email: String): Account?

	fun existsByEmailAndIsDeletedTrueAndUpdatedAtAfter(email: String, after: LocalDateTime): Boolean

	fun findAllByAccountIdInAndIsDeletedFalse(accountIds: Collection<UUID>): List<Account>
}
