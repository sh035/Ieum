package com.ieum.planner.domain.entity

import com.ieum.web.entity.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.UuidGenerator
import java.util.UUID

@Entity
@Table(name = "todo_checklist_item")
class TodoChecklistItem(
	@Column(nullable = false, updatable = false)
	val todoId: UUID,

	@Column(nullable = false)
	var content: String,

	@Id
	@UuidGenerator(style = UuidGenerator.Style.VERSION_7)
	@Column(nullable = false, updatable = false)
	val todoChecklistItemId: UUID? = null,
) : BaseEntity() {

	@Column(nullable = false)
	var isCompleted: Boolean = false
}
