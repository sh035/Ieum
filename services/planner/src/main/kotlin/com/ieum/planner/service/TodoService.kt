package com.ieum.planner.service

import com.ieum.planner.controller.dto.request.CreateTodoRequest
import com.ieum.planner.controller.dto.request.UpdateTodoRequest
import com.ieum.planner.controller.dto.response.TodoChecklistItemResponse
import com.ieum.planner.controller.dto.response.TodoMatrixResponse
import com.ieum.planner.controller.dto.response.TodoResponse
import com.ieum.planner.domain.entity.Todo
import com.ieum.planner.domain.entity.TodoChecklistItem
import com.ieum.planner.domain.enums.TodoPriority
import com.ieum.planner.domain.enums.TodoVisibility
import com.ieum.planner.exception.PlannerErrorCode
import com.ieum.planner.repository.TodoChecklistItemRepository
import com.ieum.planner.repository.TodoRepository
import com.ieum.web.exception.IeumException
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID

@Service
class TodoService(
	private val todoRepository: TodoRepository,
	private val todoChecklistItemRepository: TodoChecklistItemRepository,
	private val familyClient: FamilyClient,
) {

	private val log = LoggerFactory.getLogger(javaClass)

	fun createTodo(accountId: String, request: CreateTodoRequest): TodoResponse {
		val me = UUID.fromString(accountId)
		checkDueTime(request.dueDate, request.dueTime)

		val familyId = when (request.visibility) {
			TodoVisibility.FAMILY -> request.familyId ?: throw IeumException(PlannerErrorCode.INVALID_TODO_FAMILY)
			TodoVisibility.PRIVATE -> request.familyId?.let { throw IeumException(PlannerErrorCode.INVALID_TODO_FAMILY) }
		}
		familyId?.let { checkMember(it, me) }

		val todo = todoRepository.save(
			Todo(
				familyId = familyId,
				creatorAccountId = me,
				assigneeAccountId = resolveAssignee(familyId, me, request.assigneeAccountId),
				title = request.title,
				memo = request.memo,
				visibility = request.visibility,
				priority = request.priority,
				dueDate = request.dueDate,
				dueTime = request.dueTime,
			),
		)

		log.debug("Todo created. todoId={}, accountId={}", todo.todoId, accountId)

		return todo.toResponse(checklistItems = emptyList())
	}

	// 가족을 지정하면 그 가족의 할 일도 함께 반환한다. 내 개인 할 일은 항상 포함한다.
	@Transactional(readOnly = true)
	fun getTodos(accountId: String, familyId: UUID?): List<TodoResponse> {
		val me = UUID.fromString(accountId)
		val familyTodos = familyId?.let {
			checkMember(it, me)
			todoRepository.findAllByFamilyIdAndIsDeletedFalseOrderByCreatedAt(it)
		}.orEmpty()
		val privateTodos =
			todoRepository.findAllByCreatorAccountIdAndVisibilityAndIsDeletedFalseOrderByCreatedAt(me, TodoVisibility.PRIVATE)

		return toResponses((familyTodos + privateTodos).sortedBy { it.createdAt })
	}

	// 내가 담당자인 미완료 할 일을 중요도(우선순위)와 긴급도(마감일)로 나눈다.
	@Transactional(readOnly = true)
	fun getMatrix(accountId: String, familyId: UUID?): TodoMatrixResponse {
		val me = UUID.fromString(accountId)
		val familyTodos = familyId?.let {
			checkMember(it, me)
			todoRepository.findAllByFamilyIdAndAssigneeAccountIdAndIsCompletedFalseAndIsDeletedFalse(it, me)
		}.orEmpty()
		val privateTodos =
			todoRepository.findAllByAssigneeAccountIdAndVisibilityAndIsCompletedFalseAndIsDeletedFalse(me, TodoVisibility.PRIVATE)

		val urgentUntil = LocalDate.now().plusDays(1)
		val todos = toResponses(
			(familyTodos + privateTodos).sortedWith(
				compareBy<Todo, LocalDate?>(nullsLast()) { it.dueDate }.thenBy { it.createdAt },
			),
		)
		val (important, notImportant) = todos.partition { it.priority == TodoPriority.HIGH || it.priority == TodoPriority.MEDIUM }
		fun isUrgent(todo: TodoResponse) = todo.dueDate?.let { !it.isAfter(urgentUntil) } ?: false

		return TodoMatrixResponse(
			importantUrgent = important.filter(::isUrgent),
			importantNotUrgent = important.filterNot(::isUrgent),
			notImportantUrgent = notImportant.filter(::isUrgent),
			notImportantNotUrgent = notImportant.filterNot(::isUrgent),
		)
	}

	@Transactional(readOnly = true)
	fun getTodo(accountId: String, todoId: UUID): TodoResponse =
		toResponses(listOf(findAccessibleTodo(UUID.fromString(accountId), todoId))).single()

	fun updateTodo(accountId: String, todoId: UUID, request: UpdateTodoRequest): TodoResponse {
		val me = UUID.fromString(accountId)
		val todo = findAccessibleTodo(me, todoId)
		checkDueTime(request.dueDate, request.dueTime)

		// 담당자를 바꿀 때만 확인한다. 기존 담당자가 가족을 나가도 다른 필드는 고칠 수 있어야 한다.
		if (request.assigneeAccountId != todo.assigneeAccountId) {
			todo.assigneeAccountId = resolveAssignee(todo.familyId, me, request.assigneeAccountId)
		}
		todo.title = request.title
		todo.memo = request.memo
		todo.priority = request.priority
		todo.dueDate = request.dueDate
		todo.dueTime = request.dueTime

		return toResponses(listOf(todoRepository.save(todo))).single()
	}

	fun deleteTodo(accountId: String, todoId: UUID) {
		val todo = findAccessibleTodo(UUID.fromString(accountId), todoId)
		todo.delete()
		todoRepository.save(todo)
	}

	fun updateCompletion(accountId: String, todoId: UUID, isCompleted: Boolean) {
		val todo = findAccessibleTodo(UUID.fromString(accountId), todoId)
		todo.isCompleted = isCompleted
		todoRepository.save(todo)
	}

	// 볼 수 없는 할 일은 존재 자체를 숨긴다. 체크리스트도 같은 권한을 따른다.
	fun findAccessibleTodo(me: UUID, todoId: UUID): Todo {
		val todo = todoRepository.findByTodoIdAndIsDeletedFalse(todoId)
			?: throw IeumException(PlannerErrorCode.TODO_NOT_FOUND)

		val accessible = when (todo.visibility) {
			TodoVisibility.FAMILY -> familyClient.isMember(requireNotNull(todo.familyId), me)
			TodoVisibility.PRIVATE -> todo.creatorAccountId == me
		}
		if (!accessible) {
			throw IeumException(PlannerErrorCode.TODO_NOT_FOUND)
		}

		return todo
	}

	// 개인 할 일의 담당자는 항상 본인이고, 가족 할 일은 담당자를 반드시 지정한다.
	private fun resolveAssignee(familyId: UUID?, me: UUID, assigneeAccountId: UUID?): UUID {
		if (familyId == null) {
			if (assigneeAccountId != null && assigneeAccountId != me) {
				throw IeumException(PlannerErrorCode.INVALID_ASSIGNEE)
			}
			return me
		}

		if (assigneeAccountId == null) {
			throw IeumException(PlannerErrorCode.ASSIGNEE_REQUIRED)
		}
		if (assigneeAccountId != me && !familyClient.isMember(familyId, assigneeAccountId)) {
			throw IeumException(PlannerErrorCode.INVALID_ASSIGNEE)
		}
		return assigneeAccountId
	}

	private fun checkMember(familyId: UUID, accountId: UUID) {
		if (!familyClient.isMember(familyId, accountId)) {
			throw IeumException(PlannerErrorCode.FAMILY_NOT_FOUND)
		}
	}

	private fun checkDueTime(dueDate: LocalDate?, dueTime: LocalTime?) {
		if (dueTime != null && dueDate == null) {
			throw IeumException(PlannerErrorCode.DUE_TIME_WITHOUT_DATE)
		}
	}

	// 체크리스트는 할 일 수와 상관없이 한 번에 조회한다.
	private fun toResponses(todos: List<Todo>): List<TodoResponse> {
		if (todos.isEmpty()) return emptyList()

		val checklistItems = todoChecklistItemRepository
			.findAllByTodoIdInAndIsDeletedFalseOrderByCreatedAt(todos.map { requireNotNull(it.todoId) })
			.groupBy { it.todoId }

		return todos.map { it.toResponse(checklistItems[it.todoId].orEmpty()) }
	}

	private fun Todo.toResponse(checklistItems: List<TodoChecklistItem>) = TodoResponse(
		todoId = requireNotNull(todoId),
		familyId = familyId,
		creatorAccountId = creatorAccountId,
		assigneeAccountId = assigneeAccountId,
		title = title,
		memo = memo,
		visibility = visibility,
		priority = priority,
		dueDate = dueDate,
		dueTime = dueTime,
		isCompleted = isCompleted,
		checklistItems = checklistItems.map {
			TodoChecklistItemResponse(
				todoChecklistItemId = requireNotNull(it.todoChecklistItemId),
				content = it.content,
				isCompleted = it.isCompleted,
			)
		},
	)
}
