package com.ieum.grocery.repository

import com.ieum.grocery.domain.entity.GroceryItem
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface GroceryItemRepository : JpaRepository<GroceryItem, UUID> {

	fun existsByStorageIdAndIsDeletedFalse(storageId: UUID): Boolean
}
