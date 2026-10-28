package com.ieum.planner.domain.entity

import com.ieum.planner.domain.enums.TodoPriority
import com.ieum.planner.domain.enums.Visibility
import com.ieum.web.entity.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.UuidGenerator
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID

@Entity
@Table(name = "todo")
class Todo(
	@Column(updatable = false)
	val familyId: UUID?,

	@Column(nullable = false, updatable = false)
	val creatorAccountId: UUID,

	@Column(nullable = false)
	var assigneeAccountId: UUID,

	@Column(nullable = false)
	var title: String,

	var memo: String?,

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, updatable = false)
	val visibility: Visibility,

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	var priority: TodoPriority,

	var dueDate: LocalDate?,

	var dueTime: LocalTime?,

	@Id
	@UuidGenerator(style = UuidGenerator.Style.VERSION_7)
	@Column(nullable = false, updatable = false)
	val todoId: UUID? = null,
) : BaseEntity() {

	@Column(nullable = false)
	var isCompleted: Boolean = false
}
