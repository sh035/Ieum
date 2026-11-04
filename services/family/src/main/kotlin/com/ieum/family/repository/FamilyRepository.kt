package com.ieum.family.repository

import com.ieum.family.domain.entity.Family
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import java.util.UUID

interface FamilyRepository : JpaRepository<Family, UUID> {

	fun findAllByFamilyIdInAndIsDeletedFalse(familyIds: Collection<UUID>): List<Family>

	fun findByFamilyIdAndIsDeletedFalse(familyId: UUID): Family?

	// 나가기 / 내보내기 / 소유권 넘기기 / 초대 생성 / 취소 / 수락 / 거절을 가족 단위로 하나씩 처리하려고 가족 행을 잠근다.
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	fun findWithLockByFamilyIdAndIsDeletedFalse(familyId: UUID): Family?
}
