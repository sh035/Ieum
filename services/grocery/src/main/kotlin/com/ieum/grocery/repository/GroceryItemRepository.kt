package com.ieum.grocery.repository

import com.ieum.grocery.domain.entity.GroceryItem
import com.ieum.grocery.domain.enums.Visibility
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface GroceryItemRepository : JpaRepository<GroceryItem, UUID> {

	fun findByGroceryItemIdAndIsDeletedFalse(groceryItemId: UUID): GroceryItem?

	fun findAllByFamilyIdAndVisibilityAndIsDeletedFalse(familyId: UUID, visibility: Visibility): List<GroceryItem>

	fun findAllByFamilyIdAndVisibilityAndCreatorAccountIdAndIsDeletedFalse(
		familyId: UUID,
		visibility: Visibility,
		creatorAccountId: UUID,
	): List<GroceryItem>

	fun existsByStorageIdAndIsDeletedFalse(storageId: UUID): Boolean
}
