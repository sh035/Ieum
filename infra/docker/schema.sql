-- Schema per service. Cross-service access is blocked by schema privileges.
CREATE SCHEMA account;
CREATE SCHEMA family;
CREATE SCHEMA planner;
CREATE SCHEMA grocery;
CREATE SCHEMA expense;
CREATE SCHEMA notification;

CREATE TABLE account.account
(
	account_id uuid PRIMARY KEY,
	email      varchar(255) NOT NULL UNIQUE,
	nickname   varchar(255) NOT NULL,
	password   varchar(255) NOT NULL,
	birth_date date         NOT NULL,
	created_at timestamp(6) NOT NULL,
	updated_at timestamp(6),
	is_deleted boolean      NOT NULL DEFAULT false
);

CREATE TABLE family.family
(
	family_id  uuid PRIMARY KEY,
	name       varchar(255) NOT NULL,
	created_at timestamp(6) NOT NULL,
	updated_at timestamp(6),
	is_deleted boolean      NOT NULL DEFAULT false
);

CREATE TABLE family.family_member
(
	family_member_id uuid PRIMARY KEY,
	family_id        uuid         NOT NULL REFERENCES family.family (family_id),
	account_id       uuid         NOT NULL,
	role             varchar(20)  NOT NULL,
	created_at       timestamp(6) NOT NULL,
	updated_at       timestamp(6),
	is_deleted       boolean      NOT NULL DEFAULT false
);

-- 내보낸 구성원(soft delete)이 다시 가입할 수 있도록 삭제되지 않은 행에만 건다.
CREATE UNIQUE INDEX uk_family_member_family_id_account_id
	ON family.family_member (family_id, account_id)
	WHERE is_deleted = false;

-- 내 가족 목록 조회용
CREATE INDEX idx_family_member_account_id ON family.family_member (account_id);

CREATE TABLE family.family_invitation
(
	family_invitation_id uuid PRIMARY KEY,
	family_id            uuid         NOT NULL REFERENCES family.family (family_id),
	code                 varchar(8)   NOT NULL UNIQUE,
	expires_at           timestamp(6) NOT NULL,
	created_at           timestamp(6) NOT NULL,
	updated_at           timestamp(6),
	is_deleted           boolean      NOT NULL DEFAULT false
);

-- 가족마다 유효한 초대 코드는 하나만 둔다.
CREATE UNIQUE INDEX uk_family_invitation_family_id
	ON family.family_invitation (family_id)
	WHERE is_deleted = false;

-- PRIVATE 할 일은 가족에 속하지 않는다.
CREATE TABLE planner.todo
(
	todo_id             uuid PRIMARY KEY,
	parent_todo_id      uuid REFERENCES planner.todo (todo_id),
	family_id           uuid,
	creator_account_id  uuid         NOT NULL,
	assignee_account_id uuid         NOT NULL,
	title               varchar(255) NOT NULL,
	memo                text,
	visibility          varchar(20)  NOT NULL,
	priority            varchar(20)  NOT NULL,
	due_date            date,
	due_time            time,
	is_completed        boolean      NOT NULL DEFAULT false,
	created_at          timestamp(6) NOT NULL,
	updated_at          timestamp(6),
	is_deleted          boolean      NOT NULL DEFAULT false,
	CONSTRAINT ck_todo_visibility_family_id CHECK (
		(visibility = 'FAMILY' AND family_id IS NOT NULL) OR (visibility = 'PRIVATE' AND family_id IS NULL)
	)
);

CREATE INDEX idx_todo_family_id ON planner.todo (family_id);
CREATE INDEX idx_todo_creator_account_id ON planner.todo (creator_account_id);
CREATE INDEX idx_todo_parent_todo_id ON planner.todo (parent_todo_id);

CREATE TABLE planner.todo_checklist_item
(
	todo_checklist_item_id uuid PRIMARY KEY,
	todo_id                uuid         NOT NULL REFERENCES planner.todo (todo_id),
	content                varchar(255) NOT NULL,
	is_completed           boolean      NOT NULL DEFAULT false,
	created_at             timestamp(6) NOT NULL,
	updated_at             timestamp(6),
	is_deleted             boolean      NOT NULL DEFAULT false
);

CREATE INDEX idx_todo_checklist_item_todo_id ON planner.todo_checklist_item (todo_id);

-- 종일 일정은 start_at / end_at 의 날짜만 쓴다.
CREATE TABLE planner.schedule
(
	schedule_id        uuid PRIMARY KEY,
	family_id          uuid,
	creator_account_id uuid         NOT NULL,
	title              varchar(255) NOT NULL,
	memo               text,
	visibility         varchar(20)  NOT NULL,
	is_all_day         boolean      NOT NULL,
	start_at           timestamp(6) NOT NULL,
	end_at             timestamp(6) NOT NULL,
	created_at         timestamp(6) NOT NULL,
	updated_at         timestamp(6),
	is_deleted         boolean      NOT NULL DEFAULT false,
	CONSTRAINT ck_schedule_visibility_family_id CHECK (
		(visibility = 'FAMILY' AND family_id IS NOT NULL) OR (visibility = 'PRIVATE' AND family_id IS NULL)
	),
	CONSTRAINT ck_schedule_period CHECK (start_at <= end_at)
);

CREATE INDEX idx_schedule_family_id ON planner.schedule (family_id);
CREATE INDEX idx_schedule_creator_account_id ON planner.schedule (creator_account_id);

-- 가족 일정의 알림은 참석자에게만 보낸다.
CREATE TABLE planner.schedule_attendee
(
	schedule_attendee_id uuid PRIMARY KEY,
	schedule_id          uuid         NOT NULL REFERENCES planner.schedule (schedule_id),
	account_id           uuid         NOT NULL,
	created_at           timestamp(6) NOT NULL,
	updated_at           timestamp(6),
	is_deleted           boolean      NOT NULL DEFAULT false
);

CREATE UNIQUE INDEX uk_schedule_attendee_schedule_id_account_id
	ON planner.schedule_attendee (schedule_id, account_id)
	WHERE is_deleted = false;

-- 보관 장소는 가족이 직접 만들어 쓴다.
CREATE TABLE grocery.storage
(
	storage_id uuid PRIMARY KEY,
	family_id  uuid         NOT NULL,
	name       varchar(50)  NOT NULL,
	created_at timestamp(6) NOT NULL,
	updated_at timestamp(6),
	is_deleted boolean      NOT NULL DEFAULT false
);

CREATE UNIQUE INDEX uk_storage_family_id_name
	ON grocery.storage (family_id, name)
	WHERE is_deleted = false;

-- PRIVATE 물건은 가족 장소 안에서 작성자만 본다. family_id 는 장소에서 복사한다.
CREATE TABLE grocery.grocery_item
(
	grocery_item_id    uuid PRIMARY KEY,
	storage_id         uuid         NOT NULL REFERENCES grocery.storage (storage_id),
	family_id          uuid         NOT NULL,
	creator_account_id uuid         NOT NULL,
	name               varchar(100) NOT NULL,
	category           varchar(30)  NOT NULL,
	visibility         varchar(20)  NOT NULL,
	quantity           integer      NOT NULL CHECK (quantity > 0),
	purchase_price     integer CHECK (purchase_price >= 0),
	purchase_date      date,
	expiry_date        date,
	created_at         timestamp(6) NOT NULL,
	updated_at         timestamp(6),
	is_deleted         boolean      NOT NULL DEFAULT false
);

CREATE INDEX idx_grocery_item_storage_id ON grocery.grocery_item (storage_id);
CREATE INDEX idx_grocery_item_family_id ON grocery.grocery_item (family_id);

-- 영수증 원문을 가족이 확정한 이름 / 카테고리 / 장소로 기억해 다음 인식의 기본값으로 쓴다.
CREATE TABLE grocery.receipt_item_mapping
(
	receipt_item_mapping_id uuid PRIMARY KEY,
	family_id               uuid         NOT NULL,
	raw_name                varchar(255) NOT NULL,
	name                    varchar(100) NOT NULL,
	category                varchar(30)  NOT NULL,
	storage_id              uuid         NOT NULL REFERENCES grocery.storage (storage_id),
	created_at              timestamp(6) NOT NULL,
	updated_at              timestamp(6),
	is_deleted              boolean      NOT NULL DEFAULT false
);

CREATE UNIQUE INDEX uk_receipt_item_mapping_family_id_raw_name
	ON grocery.receipt_item_mapping (family_id, raw_name)
	WHERE is_deleted = false;

-- PRIVATE 지출은 가족 가계부 안에서 작성자만 보고, 결제한 사람도 작성자 본인이다.
CREATE TABLE expense.expense
(
	expense_id         uuid PRIMARY KEY,
	family_id          uuid         NOT NULL,
	creator_account_id uuid         NOT NULL,
	payer_account_id   uuid         NOT NULL,
	title              varchar(100) NOT NULL,
	memo               text,
	amount             integer      NOT NULL CHECK (amount > 0),
	category           varchar(30)  NOT NULL,
	visibility         varchar(20)  NOT NULL,
	spent_date         date         NOT NULL,
	created_at         timestamp(6) NOT NULL,
	updated_at         timestamp(6),
	is_deleted         boolean      NOT NULL DEFAULT false
);

CREATE INDEX idx_expense_family_id_spent_date ON expense.expense (family_id, spent_date);

-- account_id 가 없으면 가족 예산, 있으면 그 사람의 개인 예산이다.
-- budget_month 가 없으면 매달 적용되는 기본 예산, 있으면 그 달(1일)에만 적용되는 예산이다.
CREATE TABLE expense.budget
(
	budget_id    uuid PRIMARY KEY,
	family_id    uuid         NOT NULL,
	account_id   uuid,
	budget_month date,
	amount       integer      NOT NULL CHECK (amount >= 0),
	created_at   timestamp(6) NOT NULL,
	updated_at   timestamp(6),
	is_deleted   boolean      NOT NULL DEFAULT false,
	CONSTRAINT ck_budget_month_first_day CHECK (budget_month IS NULL OR extract(DAY FROM budget_month) = 1)
);

CREATE UNIQUE INDEX uk_budget_family_id_account_id_budget_month
	ON expense.budget (family_id, account_id, budget_month) NULLS NOT DISTINCT
	WHERE is_deleted = false;

-- family_id 가 없으면 가족과 무관한 알림이다 (알림함 개인 탭).
CREATE TABLE notification.notification
(
	notification_id uuid PRIMARY KEY,
	account_id      uuid         NOT NULL,
	family_id       uuid,
	type            varchar(40)  NOT NULL,
	title           varchar(100) NOT NULL,
	body            varchar(200) NOT NULL,
	target_type     varchar(20),
	target_id       uuid,
	dedup_key       varchar(200) NOT NULL,
	is_read         boolean      NOT NULL DEFAULT false,
	created_at      timestamp(6) NOT NULL,
	updated_at      timestamp(6),
	is_deleted      boolean      NOT NULL DEFAULT false
);

-- 지운 알림도 다시 오면 안 되므로 is_deleted 와 상관없이 막는다.
CREATE UNIQUE INDEX uk_notification_account_id_dedup_key ON notification.notification (account_id, dedup_key);
CREATE INDEX idx_notification_account_id_created_at ON notification.notification (account_id, created_at DESC);

CREATE TABLE notification.device_token
(
	device_token_id uuid PRIMARY KEY,
	account_id      uuid         NOT NULL,
	token           varchar(500) NOT NULL,
	platform        varchar(20)  NOT NULL,
	created_at      timestamp(6) NOT NULL,
	updated_at      timestamp(6),
	is_deleted      boolean      NOT NULL DEFAULT false
);

CREATE UNIQUE INDEX uk_device_token_token ON notification.device_token (token) WHERE is_deleted = false;
CREATE INDEX idx_device_token_account_id ON notification.device_token (account_id);
