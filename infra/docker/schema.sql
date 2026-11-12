-- Schema per service. Cross-service access is blocked by schema privileges.
CREATE SCHEMA account;
CREATE SCHEMA family;
CREATE SCHEMA planner;
CREATE SCHEMA grocery;
CREATE SCHEMA expense;
CREATE SCHEMA notification;

-- 회원 계정
-- - 이메일은 탈퇴하지 않은 계정끼리 겹치지 않는다. 같은 이메일 재가입은 탈퇴 3일 뒤부터 된다
-- - 다른 서비스는 account_id 로만 참조하고 닉네임은 gRPC 로 조회한다
CREATE TABLE account.account
(
	account_id uuid PRIMARY KEY,               -- 계정 ID
	email      varchar(255) NOT NULL,          -- 로그인 이메일, 가족 초대 시 계정 찾기에도 쓴다
	nickname   varchar(255) NOT NULL,          -- 화면과 알림 문구에 쓰는 이름
	password   varchar(255) NOT NULL,          -- 해시한 비밀번호
	birth_date date         NOT NULL,          -- 생년월일
	created_at timestamp(6) NOT NULL,          -- 가입 시각
	created_by uuid,                           -- 만든 계정, 요청한 사람이 없으면(시스템) 없다
	updated_at timestamp(6),                   -- 마지막 수정 시각. 탈퇴한 계정은 탈퇴 시각
	updated_by uuid,                           -- 마지막으로 고친 계정, 시스템이면 없다
	is_deleted boolean      NOT NULL DEFAULT false -- 탈퇴 여부
);

-- 탈퇴한 계정과 같은 이메일로 다시 가입할 수 있도록 탈퇴하지 않은 행에만 건다.
CREATE UNIQUE INDEX uk_account_email
	ON account.account (email)
	WHERE is_deleted = false;

-- 가족
-- - 만든 사람이 OWNER 가 된다
-- - 혼자 남은 OWNER 가 나가면 is_deleted 로 삭제한다
CREATE TABLE family.family
(
	family_id  uuid PRIMARY KEY,               -- 가족 ID
	name       varchar(255) NOT NULL,          -- 가족 이름, 알림 문구의 [가족이름] 에 쓴다
	created_at timestamp(6) NOT NULL,          -- 만든 시각
	created_by uuid,                           -- 만든 계정, 요청한 사람이 없으면(시스템) 없다
	updated_at timestamp(6),                   -- 마지막 수정 시각
	updated_by uuid,                           -- 마지막으로 고친 계정, 시스템이면 없다
	is_deleted boolean      NOT NULL DEFAULT false -- 삭제 여부
);

-- 가족 구성원
-- - 가족마다 OWNER 는 한 명이다
-- - 나가거나 내보내지면 is_deleted 로 끝내고, 다시 초대받으면 새 행으로 들어온다
CREATE TABLE family.family_member
(
	family_member_id uuid PRIMARY KEY,         -- 구성원 ID
	family_id        uuid         NOT NULL REFERENCES family.family (family_id), -- 가족
	account_id       uuid         NOT NULL,    -- 계정
	role             varchar(20)  NOT NULL,    -- OWNER / MANAGER(살림 함께 꾸리기) / MEMBER
	nickname         varchar(20)  NOT NULL,    -- 이 가족에서 부르는 이름. 들어올 때 계정 닉네임을 복사하고 본인이 바꾼다
	created_at       timestamp(6) NOT NULL,    -- 들어온 시각
	created_by       uuid,                     -- 만든 계정, 요청한 사람이 없으면(시스템) 없다
	updated_at       timestamp(6),             -- 마지막 수정 시각 (역할 변경 등)
	updated_by       uuid,                     -- 마지막으로 고친 계정, 시스템이면 없다
	is_deleted       boolean      NOT NULL DEFAULT false -- 나갔거나 내보내졌는지
);

-- 내보낸 구성원(soft delete)이 다시 가입할 수 있도록 삭제되지 않은 행에만 건다.
CREATE UNIQUE INDEX uk_family_member_family_id_account_id
	ON family.family_member (family_id, account_id)
	WHERE is_deleted = false;

-- 내 가족 목록 조회용
CREATE INDEX idx_family_member_account_id ON family.family_member (account_id);

-- 특정 계정에게 보낸 가족 초대
-- - OWNER 가 지정한 계정에게 보내고, 그 계정만 수락하거나 거절할 수 있다
-- - 만든 뒤 1시간 동안 유효하다
-- - 수락 / 거절 / 취소하거나 혼자 남은 OWNER 가 나가 가족이 삭제되면 is_deleted 로 끝낸다
-- - 만료된 초대는 같은 사람을 다시 초대할 때 정리한다
CREATE TABLE family.family_invitation
(
	family_invitation_id uuid PRIMARY KEY,     -- 초대 ID
	family_id            uuid         NOT NULL REFERENCES family.family (family_id), -- 초대한 가족
	inviter_account_id   uuid         NOT NULL, -- 초대한 OWNER
	invitee_account_id   uuid         NOT NULL, -- 초대받은 계정
	expires_at           timestamp(6) NOT NULL, -- 만료 시각 (만든 시각 + 1시간)
	created_at           timestamp(6) NOT NULL, -- 만든 시각
	created_by           uuid,                  -- 만든 계정, 요청한 사람이 없으면(시스템) 없다
	updated_at           timestamp(6),          -- 마지막 수정 시각
	updated_by           uuid,                  -- 마지막으로 고친 계정, 시스템이면 없다
	is_deleted           boolean      NOT NULL DEFAULT false -- 수락 / 거절 / 취소 / 가족 삭제 / 만료 정리로 끝났는지
);

-- 같은 가족이 같은 사람에게 보낸 살아 있는 초대는 하나만 둔다.
CREATE UNIQUE INDEX uk_family_invitation_family_id_invitee_account_id
	ON family.family_invitation (family_id, invitee_account_id)
	WHERE is_deleted = false;

-- 받은 초대 목록 조회용
CREATE INDEX idx_family_invitation_invitee_account_id ON family.family_invitation (invitee_account_id);

-- 다른 서비스로 보낼 가족 이벤트 (Transactional Outbox)
-- - 나가기 / 내보내기 / 가족 삭제와 같은 트랜잭션에 저장해 커밋된 변경만 보낸다
-- - 스케줄러가 sent_at 이 빈 행을 오래된 순으로 Kafka 에 보내고 sent_at 을 채운다
-- - 보내기에 실패하면 그 행에서 멈추고 다음 주기에 다시 보낸다
-- - 보낸 뒤 sent_at 을 채우기 전에 서버가 죽으면 다시 보내므로 받는 쪽은 같은 이벤트를 두 번 받아도 결과가 같아야 한다
-- - 보낸 지 7일이 지난 행은 매일 지운다
-- - 인프라 테이블이라 공통 컬럼 규칙을 따르지 않는다
CREATE TABLE family.outbox_event
(
	outbox_event_id uuid PRIMARY KEY,          -- 이벤트 ID, Kafka 메시지의 eventId 로 쓴다
	event_type      varchar(64)  NOT NULL,     -- FAMILY_MEMBER_LEFT / FAMILY_DELETED
	family_id       uuid         NOT NULL,     -- 대상 가족, Kafka 키로 써서 같은 가족은 순서대로 처리된다
	payload         text         NOT NULL,     -- 이벤트 내용 JSON (familyId, accountId)
	created_at      timestamp(6) NOT NULL,     -- 쌓은 시각, 보내는 순서의 기준
	sent_at         timestamp(6)               -- Kafka 로 보낸 시각, 비어 있으면 아직 안 보냈다
);

-- 보낼 이벤트 조회용
CREATE INDEX idx_outbox_event_created_at ON family.outbox_event (created_at, outbox_event_id) WHERE sent_at IS NULL;

-- ShedLock 이 관리하는 스케줄 작업 락
-- - Pod 가 여러 개여도 outbox 전송과 정리는 한 Pod 에서만 돈다 (한 주기가 최대 잠금 시간 1분 안에 끝나도록 나눠 보낸다)
-- - 라이브러리 표준 형식이라 공통 컬럼 규칙을 따르지 않는다
CREATE TABLE family.shedlock
(
	name       varchar(64)  NOT NULL PRIMARY KEY, -- 스케줄 작업 이름
	lock_until timestamp(6) NOT NULL,             -- 이 시각까지 다른 Pod 는 실행하지 않는다
	locked_at  timestamp(6) NOT NULL,             -- 락을 잡은 시각
	locked_by  varchar(255) NOT NULL              -- 락을 잡은 Pod
);

-- libs:scheduler 엔티티 검증용 빈 테이블
-- - family 는 체크포인트를 쓰지 않는다
-- - 인프라 테이블이라 공통 컬럼 규칙을 따르지 않는다
CREATE TABLE family.scheduler_checkpoint
(
	name            varchar(64)  NOT NULL PRIMARY KEY, -- 스케줄 작업 이름
	processed_until timestamp(6) NOT NULL              -- 여기까지 처리했다
);

-- 할 일
-- - PRIVATE 할 일은 가족에 속하지 않는다 (family_id 없음)
-- - parent_todo_id 가 있으면 하위 할 일이다
CREATE TABLE planner.todo
(
	todo_id             uuid PRIMARY KEY,      -- 할 일 ID
	parent_todo_id      uuid REFERENCES planner.todo (todo_id), -- 상위 할 일, 없으면 최상위
	family_id           uuid,                  -- 가족, PRIVATE 이면 없다
	creator_account_id  uuid         NOT NULL, -- 만든 사람
	assignee_account_id uuid         NOT NULL, -- 맡은 사람, 마감 알림과 브리핑 대상
	title               varchar(255) NOT NULL, -- 제목
	memo                text,                  -- 메모
	visibility          varchar(20)  NOT NULL, -- FAMILY / PRIVATE
	priority            varchar(20)  NOT NULL, -- HIGH / MEDIUM / LOW / NONE
	due_date            date,                  -- 마감 날짜, 없으면 기한 없음
	due_time            time,                  -- 마감 시각, 없으면 그날 하루
	is_completed        boolean      NOT NULL DEFAULT false, -- 완료 여부
	created_at          timestamp(6) NOT NULL, -- 만든 시각
	created_by          uuid,                  -- 만든 계정, 요청한 사람이 없으면(시스템) 없다
	updated_at          timestamp(6),          -- 마지막 수정 시각
	updated_by          uuid,                  -- 마지막으로 고친 계정, 시스템이면 없다
	is_deleted          boolean      NOT NULL DEFAULT false, -- 삭제 여부
	CONSTRAINT ck_todo_visibility_family_id CHECK (
		(visibility = 'FAMILY' AND family_id IS NOT NULL) OR (visibility = 'PRIVATE' AND family_id IS NULL)
	)
);

CREATE INDEX idx_todo_family_id ON planner.todo (family_id);
CREATE INDEX idx_todo_creator_account_id ON planner.todo (creator_account_id);
CREATE INDEX idx_todo_parent_todo_id ON planner.todo (parent_todo_id);

-- 할 일 안의 체크리스트 항목
CREATE TABLE planner.todo_checklist_item
(
	todo_checklist_item_id uuid PRIMARY KEY,   -- 항목 ID
	todo_id                uuid         NOT NULL REFERENCES planner.todo (todo_id), -- 속한 할 일
	content                varchar(255) NOT NULL, -- 항목 내용
	is_completed           boolean      NOT NULL DEFAULT false, -- 체크 여부
	created_at             timestamp(6) NOT NULL, -- 만든 시각
	created_by             uuid,                  -- 만든 계정, 요청한 사람이 없으면(시스템) 없다
	updated_at             timestamp(6),          -- 마지막 수정 시각
	updated_by             uuid,                  -- 마지막으로 고친 계정, 시스템이면 없다
	is_deleted             boolean      NOT NULL DEFAULT false -- 삭제 여부
);

CREATE INDEX idx_todo_checklist_item_todo_id ON planner.todo_checklist_item (todo_id);

-- 일정
-- - PRIVATE 일정은 가족에 속하지 않는다 (family_id 없음)
-- - 종일 일정은 start_at / end_at 의 날짜만 쓰고 끝 날짜를 포함한다
-- - 시각 일정은 끝 시각을 포함하지 않는다 (자정에 끝나면 전날 일정)
CREATE TABLE planner.schedule
(
	schedule_id        uuid PRIMARY KEY,       -- 일정 ID
	family_id          uuid,                   -- 가족, PRIVATE 이면 없다
	creator_account_id uuid         NOT NULL,  -- 만든 사람
	title              varchar(255) NOT NULL,  -- 제목
	memo               text,                   -- 메모
	visibility         varchar(20)  NOT NULL,  -- FAMILY / PRIVATE
	is_all_day         boolean      NOT NULL,  -- 종일 일정 여부
	start_at           timestamp(6) NOT NULL,  -- 시작 (종일이면 시작 날짜 00:00)
	end_at             timestamp(6) NOT NULL,  -- 끝 (종일이면 끝 날짜 00:00)
	created_at         timestamp(6) NOT NULL,  -- 만든 시각
	created_by         uuid,                   -- 만든 계정, 요청한 사람이 없으면(시스템) 없다
	updated_at         timestamp(6),           -- 마지막 수정 시각
	updated_by         uuid,                   -- 마지막으로 고친 계정, 시스템이면 없다
	is_deleted         boolean      NOT NULL DEFAULT false, -- 삭제 여부
	CONSTRAINT ck_schedule_visibility_family_id CHECK (
		(visibility = 'FAMILY' AND family_id IS NOT NULL) OR (visibility = 'PRIVATE' AND family_id IS NULL)
	),
	CONSTRAINT ck_schedule_period CHECK (start_at <= end_at)
);

CREATE INDEX idx_schedule_family_id ON planner.schedule (family_id);
CREATE INDEX idx_schedule_creator_account_id ON planner.schedule (creator_account_id);

-- 일정 참석자
-- - 가족 일정의 알림과 브리핑은 참석자에게만 간다
CREATE TABLE planner.schedule_attendee
(
	schedule_attendee_id uuid PRIMARY KEY,     -- 참석자 ID
	schedule_id          uuid         NOT NULL REFERENCES planner.schedule (schedule_id), -- 일정
	account_id           uuid         NOT NULL, -- 참석하는 계정
	created_at           timestamp(6) NOT NULL, -- 추가된 시각
	created_by           uuid,                  -- 만든 계정, 요청한 사람이 없으면(시스템) 없다
	updated_at           timestamp(6),          -- 마지막 수정 시각
	updated_by           uuid,                  -- 마지막으로 고친 계정, 시스템이면 없다
	is_deleted           boolean      NOT NULL DEFAULT false -- 참석자에서 빠졌는지
);

CREATE UNIQUE INDEX uk_schedule_attendee_schedule_id_account_id
	ON planner.schedule_attendee (schedule_id, account_id)
	WHERE is_deleted = false;

-- ShedLock 이 관리하는 스케줄 작업 락
-- - 라이브러리 표준 형식이라 공통 컬럼 규칙을 따르지 않는다
CREATE TABLE planner.shedlock
(
	name       varchar(64)  NOT NULL PRIMARY KEY, -- 스케줄 작업 이름
	lock_until timestamp(6) NOT NULL,             -- 이 시각까지 다른 Pod 는 실행하지 않는다
	locked_at  timestamp(6) NOT NULL,             -- 락을 잡은 시각
	locked_by  varchar(255) NOT NULL              -- 락을 잡은 Pod
);

-- 스케줄 작업이 어디까지 처리했는지 기록한다
-- - 다음 회차는 여기서 이어서 처리해 중복도 누락도 없다
-- - 인프라 테이블이라 공통 컬럼 규칙을 따르지 않는다
CREATE TABLE planner.scheduler_checkpoint
(
	name            varchar(64)  NOT NULL PRIMARY KEY, -- 스케줄 작업 이름
	processed_until timestamp(6) NOT NULL              -- 여기까지 처리했다
);

-- 보관 장소 (냉장고 / 냉동고 / 팬트리 등)
-- - 가족이 직접 만들어 쓰고, 같은 가족 안에서 이름이 겹치지 않는다
CREATE TABLE grocery.storage
(
	storage_id uuid PRIMARY KEY,               -- 장소 ID
	family_id  uuid         NOT NULL,          -- 가족
	name       varchar(50)  NOT NULL,          -- 장소 이름
	created_at timestamp(6) NOT NULL,          -- 만든 시각
	created_by uuid,                           -- 만든 계정, 요청한 사람이 없으면(시스템) 없다
	updated_at timestamp(6),                   -- 마지막 수정 시각
	updated_by uuid,                           -- 마지막으로 고친 계정, 시스템이면 없다
	is_deleted boolean      NOT NULL DEFAULT false -- 삭제 여부
);

CREATE UNIQUE INDEX uk_storage_family_id_name
	ON grocery.storage (family_id, name)
	WHERE is_deleted = false;

-- 재고
-- - PRIVATE 물건은 가족 장소 안에서 작성자만 본다
-- - family_id 는 장소에서 복사한다
CREATE TABLE grocery.grocery_item
(
	grocery_item_id    uuid PRIMARY KEY,       -- 재고 ID
	storage_id         uuid         NOT NULL REFERENCES grocery.storage (storage_id), -- 보관 장소
	family_id          uuid         NOT NULL,  -- 가족 (장소에서 복사)
	creator_account_id uuid         NOT NULL,  -- 등록한 사람
	name               varchar(100) NOT NULL,  -- 물건 이름
	category           varchar(30)  NOT NULL,  -- VEGETABLE / FRUIT / MEAT / SEAFOOD / DAIRY / EGG / PROCESSED / SIDE_DISH / KIMCHI / FROZEN / READY_MEAL / BAKERY / BEVERAGE / SAUCE / GRAIN / SNACK / ETC
	visibility         varchar(20)  NOT NULL,  -- FAMILY / PRIVATE
	quantity           integer      NOT NULL CHECK (quantity > 0), -- 개수
	purchase_price     integer CHECK (purchase_price >= 0), -- 산 가격 (원)
	purchase_date      date,                   -- 산 날짜
	expiry_date        date,                   -- 유통기한, 하루 전부터 브리핑에 들어간다
	created_at         timestamp(6) NOT NULL,  -- 등록한 시각
	created_by         uuid,                   -- 만든 계정, 요청한 사람이 없으면(시스템) 없다
	updated_at         timestamp(6),           -- 마지막 수정 시각
	updated_by         uuid,                   -- 마지막으로 고친 계정, 시스템이면 없다
	is_deleted         boolean      NOT NULL DEFAULT false -- 삭제 여부
);

CREATE INDEX idx_grocery_item_storage_id ON grocery.grocery_item (storage_id);
CREATE INDEX idx_grocery_item_family_id ON grocery.grocery_item (family_id);

-- 영수증 원문을 가족이 확정한 이름 / 카테고리 / 장소로 기억한다
-- - 영수증 인식 기능에서 기본값으로 쓸 예정이다 (아직 쓰는 곳 없음)
CREATE TABLE grocery.receipt_item_mapping
(
	receipt_item_mapping_id uuid PRIMARY KEY,  -- 매핑 ID
	family_id               uuid         NOT NULL, -- 가족
	raw_name                varchar(255) NOT NULL, -- 영수증에 찍힌 원래 이름
	name                    varchar(100) NOT NULL, -- 가족이 확정한 물건 이름
	category                varchar(30)  NOT NULL, -- 가족이 확정한 카테고리
	storage_id              uuid         NOT NULL REFERENCES grocery.storage (storage_id), -- 가족이 확정한 보관 장소
	created_at              timestamp(6) NOT NULL, -- 만든 시각
	created_by              uuid,                  -- 만든 계정, 요청한 사람이 없으면(시스템) 없다
	updated_at              timestamp(6),          -- 마지막 수정 시각
	updated_by              uuid,                  -- 마지막으로 고친 계정, 시스템이면 없다
	is_deleted              boolean      NOT NULL DEFAULT false -- 삭제 여부
);

CREATE UNIQUE INDEX uk_receipt_item_mapping_family_id_raw_name
	ON grocery.receipt_item_mapping (family_id, raw_name)
	WHERE is_deleted = false;

-- 지출
-- - PRIVATE 지출은 가족 가계부 안에서 작성자만 보고, 결제한 사람도 작성자 본인이다
CREATE TABLE expense.expense
(
	expense_id         uuid PRIMARY KEY,       -- 지출 ID
	family_id          uuid         NOT NULL,  -- 가족 가계부
	creator_account_id uuid         NOT NULL,  -- 기록한 사람
	payer_account_id   uuid         NOT NULL,  -- 결제한 사람
	title              varchar(100) NOT NULL,  -- 지출 내용
	memo               text,                   -- 메모
	amount             integer      NOT NULL CHECK (amount > 0), -- 금액 (원)
	category           varchar(30)  NOT NULL,  -- FOOD / CAFE_SNACK / MART / SHOPPING / HOUSING_TELECOM / TRANSPORT / MEDICAL / EDUCATION / CULTURE / EVENT_GIFT / ETC
	visibility         varchar(20)  NOT NULL,  -- FAMILY / PRIVATE
	spent_date         date         NOT NULL,  -- 쓴 날짜
	created_at         timestamp(6) NOT NULL,  -- 기록한 시각
	created_by         uuid,                   -- 만든 계정, 요청한 사람이 없으면(시스템) 없다
	updated_at         timestamp(6),           -- 마지막 수정 시각
	updated_by         uuid,                   -- 마지막으로 고친 계정, 시스템이면 없다
	is_deleted         boolean      NOT NULL DEFAULT false -- 삭제 여부
);

CREATE INDEX idx_expense_family_id_spent_date ON expense.expense (family_id, spent_date);

-- 예산
-- - account_id 가 없으면 가족 예산, 있으면 그 사람의 개인 예산이다
-- - budget_month 가 없으면 매달 적용되는 기본 예산, 있으면 그 달(1일)에만 적용되는 예산이다
CREATE TABLE expense.budget
(
	budget_id    uuid PRIMARY KEY,             -- 예산 ID
	family_id    uuid         NOT NULL,        -- 가족
	account_id   uuid,                         -- 개인 예산의 주인, 없으면 가족 예산
	budget_month date,                         -- 적용 달 (1일), 없으면 매달 기본 예산
	amount       integer      NOT NULL CHECK (amount >= 0), -- 예산 금액 (원)
	created_at   timestamp(6) NOT NULL,        -- 만든 시각
	created_by   uuid,                         -- 만든 계정, 요청한 사람이 없으면(시스템) 없다
	updated_at   timestamp(6),                 -- 마지막 수정 시각
	updated_by   uuid,                         -- 마지막으로 고친 계정, 시스템이면 없다
	is_deleted   boolean      NOT NULL DEFAULT false, -- 삭제 여부
	CONSTRAINT ck_budget_month_first_day CHECK (budget_month IS NULL OR extract(DAY FROM budget_month) = 1)
);

CREATE UNIQUE INDEX uk_budget_family_id_account_id_budget_month
	ON expense.budget (family_id, account_id, budget_month) NULLS NOT DISTINCT
	WHERE is_deleted = false;

-- 알림함
-- - 받는 사람마다 한 행이다
-- - family_id 가 없으면 가족과 무관한 알림이다 (알림함 개인 탭)
CREATE TABLE notification.notification
(
	notification_id uuid PRIMARY KEY,          -- 알림 ID
	account_id      uuid         NOT NULL,     -- 받는 사람
	family_id       uuid,                      -- 알림함 가족 탭 기준, 없으면 개인 알림
	type            varchar(40)  NOT NULL,     -- 알림 종류 (NotificationEventType)
	title           varchar(100) NOT NULL,     -- 제목
	body            varchar(200) NOT NULL,     -- 본문
	target_type     varchar(20),               -- 눌렀을 때 이동할 대상 종류, 없으면 이동하지 않는다
	target_id       uuid,                      -- 눌렀을 때 이동할 대상 ID
	dedup_key       varchar(200) NOT NULL,     -- 같은 알림이 두 번 저장되지 않게 막는 키 (이벤트 ID 또는 발행 쪽이 정한 키)
	is_read         boolean      NOT NULL DEFAULT false, -- 읽음 여부
	created_at      timestamp(6) NOT NULL,     -- 받은 시각
	created_by      uuid,                      -- 만든 계정, 요청한 사람이 없으면(시스템) 없다
	updated_at      timestamp(6),              -- 마지막 수정 시각
	updated_by      uuid,                      -- 마지막으로 고친 계정, 시스템이면 없다
	is_deleted      boolean      NOT NULL DEFAULT false -- 지웠는지
);

-- 지운 알림도 다시 오면 안 되므로 is_deleted 와 상관없이 막는다.
CREATE UNIQUE INDEX uk_notification_account_id_dedup_key ON notification.notification (account_id, dedup_key);
CREATE INDEX idx_notification_account_id_created_at ON notification.notification (account_id, created_at DESC);

-- 푸시를 받을 기기 토큰 (FCM)
-- - 한 계정이 여러 기기를 쓸 수 있다
-- - FCM 이 만료됐다고 알려 준 토큰은 is_deleted 로 끝낸다
CREATE TABLE notification.device_token
(
	device_token_id uuid PRIMARY KEY,          -- 토큰 ID
	account_id      uuid         NOT NULL,     -- 기기 주인
	token           varchar(500) NOT NULL,     -- FCM 등록 토큰
	platform        varchar(20)  NOT NULL,     -- ANDROID / IOS
	created_at      timestamp(6) NOT NULL,     -- 등록한 시각
	created_by      uuid,                      -- 만든 계정, 요청한 사람이 없으면(시스템) 없다
	updated_at      timestamp(6),              -- 마지막 수정 시각
	updated_by      uuid,                      -- 마지막으로 고친 계정, 시스템이면 없다
	is_deleted      boolean      NOT NULL DEFAULT false -- 해제됐거나 만료됐는지
);

CREATE UNIQUE INDEX uk_device_token_token ON notification.device_token (token) WHERE is_deleted = false;
CREATE INDEX idx_device_token_account_id ON notification.device_token (account_id);

-- ShedLock 이 관리하는 스케줄 작업 락
-- - 데일리 브리핑이 한 Pod 에서만 돈다
-- - 라이브러리 표준 형식이라 공통 컬럼 규칙을 따르지 않는다
CREATE TABLE notification.shedlock
(
	name       varchar(64)  NOT NULL PRIMARY KEY, -- 스케줄 작업 이름
	lock_until timestamp(6) NOT NULL,             -- 이 시각까지 다른 Pod 는 실행하지 않는다
	locked_at  timestamp(6) NOT NULL,             -- 락을 잡은 시각
	locked_by  varchar(255) NOT NULL              -- 락을 잡은 Pod
);

-- libs:scheduler 엔티티 검증용 빈 테이블
-- - notification 은 체크포인트를 쓰지 않는다
-- - 인프라 테이블이라 공통 컬럼 규칙을 따르지 않는다
CREATE TABLE notification.scheduler_checkpoint
(
	name            varchar(64)  NOT NULL PRIMARY KEY, -- 스케줄 작업 이름
	processed_until timestamp(6) NOT NULL              -- 여기까지 처리했다
);
