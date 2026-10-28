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
