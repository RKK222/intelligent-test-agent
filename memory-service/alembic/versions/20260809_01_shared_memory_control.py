"""创建共享 Mem0 history、逻辑记忆、幂等和投影 outbox。"""

from alembic import op


revision = "20260809_01"
down_revision = None
branch_labels = None
depends_on = None


def upgrade() -> None:
    op.execute("create extension if not exists vector")
    op.execute(
        """
        create table memory_mem0_history (
            history_id varchar(64) primary key,
            profile_key varchar(512) not null,
            mem0_memory_id varchar(128) not null,
            old_memory text,
            new_memory text,
            event varchar(32) not null,
            created_at timestamptz not null default now(),
            updated_at timestamptz,
            is_deleted boolean not null default false,
            actor_id varchar(255),
            role varchar(32)
        );
        create index idx_memory_mem0_history_lookup
            on memory_mem0_history(profile_key, mem0_memory_id, created_at);

        create table memory_operations (
            operation_id varchar(128) primary key,
            partition_key varchar(512) not null,
            status varchar(32) not null,
            result_json jsonb,
            error_code varchar(64),
            native_started boolean not null default false,
            started_at timestamptz not null,
            updated_at timestamptz not null,
            constraint ck_memory_operations_status
                check (status in ('STARTED', 'COMPLETED', 'FAILED'))
        );
        create index idx_memory_operations_partition on memory_operations(partition_key, updated_at);

        create table memory_logical_records (
            logical_memory_id varchar(64) primary key,
            scope varchar(64) not null,
            owner_user_id varchar(128),
            application_id varchar(128),
            content text not null,
            metadata jsonb not null default '{}'::jsonb,
            version bigint not null,
            deleted boolean not null default false,
            created_at timestamptz not null,
            updated_at timestamptz not null,
            constraint ck_memory_logical_version check (version >= 1),
            constraint ck_memory_logical_scope check (
                (scope = 'PERSONAL_GLOBAL' and owner_user_id is not null and application_id is null)
                or (scope = 'PERSONAL_APPLICATION' and owner_user_id is not null and application_id is not null)
                or (scope = 'TEAM_APPLICATION' and owner_user_id is null and application_id is not null)
            )
        );
        create index idx_memory_logical_partition
            on memory_logical_records(scope, owner_user_id, application_id, deleted);

        create table memory_projections (
            logical_memory_id varchar(64) not null references memory_logical_records(logical_memory_id),
            profile_key varchar(512) not null,
            mem0_memory_id varchar(128),
            projected_version bigint not null default 0,
            status varchar(32) not null,
            last_error_code varchar(64),
            updated_at timestamptz not null,
            primary key (logical_memory_id, profile_key),
            unique (profile_key, mem0_memory_id),
            constraint ck_memory_projection_version check (projected_version >= 0),
            constraint ck_memory_projection_status
                check (status in ('PENDING', 'SYNCED', 'FAILED', 'DELETED'))
        );

        create table memory_projection_outbox (
            outbox_id varchar(64) primary key,
            logical_memory_id varchar(64) not null references memory_logical_records(logical_memory_id),
            target_profile_key varchar(512) not null,
            operation varchar(16) not null,
            target_version bigint not null,
            idempotency_key varchar(512) not null unique,
            status varchar(16) not null,
            attempts integer not null default 0,
            available_at timestamptz not null,
            claimed_by varchar(128),
            lease_until timestamptz,
            last_error_code varchar(64),
            created_at timestamptz not null,
            updated_at timestamptz not null,
            constraint ck_memory_projection_outbox_operation
                check (operation in ('UPSERT', 'DELETE')),
            constraint ck_memory_projection_outbox_status
                check (status in ('PENDING', 'PROCESSING', 'COMPLETED', 'DEAD')),
            constraint ck_memory_projection_outbox_attempts check (attempts >= 0)
        );
        create index idx_memory_projection_outbox_claim
            on memory_projection_outbox(status, available_at, lease_until);

        create table memory_logical_history (
            event_id varchar(64) primary key,
            logical_memory_id varchar(64) not null references memory_logical_records(logical_memory_id),
            version bigint not null,
            event varchar(16) not null,
            old_memory text,
            new_memory text,
            created_at timestamptz not null,
            unique (logical_memory_id, version),
            constraint ck_memory_logical_history_event check (event in ('ADD', 'UPDATE', 'DELETE'))
        );
        """
    )


def downgrade() -> None:
    op.execute(
        """
        drop table if exists memory_logical_history;
        drop table if exists memory_projection_outbox;
        drop table if exists memory_projections;
        drop table if exists memory_logical_records;
        drop table if exists memory_operations;
        drop table if exists memory_mem0_history;
        """
    )
