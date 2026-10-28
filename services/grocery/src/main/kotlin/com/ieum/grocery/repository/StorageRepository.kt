package com.ieum.grocery.repository

import com.ieum.grocery.domain.entity.Storage
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface StorageRepository : JpaRepository<Storage, UUID> {

	fun findByStorageIdAndIsDeletedFalse(storageId: UUID): Storage?

	fun findAllByFamilyIdAndIsDeletedFalseOrderByCreatedAt(familyId: UUID): List<Storage>
}
