package com.enterprise.testagent.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

/** 验证一次性展示字段对存量凭据失败关闭，同时允许新凭据保留未展示状态。 */
class LocalClientCredentialRevealMigrationIntegrationTest {

    private SingleConnectionDataSource dataSource;
    private JdbcClient jdbc;

    @BeforeEach
    void setUp() {
        dataSource = new SingleConnectionDataSource(
                "jdbc:h2:mem:testagent_local_client_reveal_%s;MODE=PostgreSQL;DATABASE_TO_UPPER=false"
                        .formatted(UUID.randomUUID().toString().replace("-", "")),
                "sa", "", true);
        jdbc = JdbcClient.create(dataSource);
        jdbc.sql("""
                        create table local_client_credentials (
                            user_id varchar(128) primary key,
                            encrypted_client_key text not null,
                            client_key_fingerprint varchar(64) not null unique,
                            key_hint varchar(64) not null,
                            version bigint not null,
                            status varchar(32) not null,
                            created_at timestamp with time zone not null,
                            updated_at timestamp with time zone not null,
                            revoked_at timestamp with time zone
                        )
                        """).update();
        jdbc.sql("""
                        insert into local_client_credentials(
                            user_id, encrypted_client_key, client_key_fingerprint, key_hint,
                            version, status, created_at, updated_at, revoked_at
                        ) values (
                            'usr_existing', 'ciphertext', :fingerprint, 'tack_v1_ABC...WXYZ',
                            3, 'ACTIVE', timestamp with time zone '2026-08-19 10:00:00+00',
                            timestamp with time zone '2026-08-20 11:00:00+00', null
                        )
                        """)
                .param("fingerprint", "a".repeat(64))
                .update();
    }

    @AfterEach
    void tearDown() {
        dataSource.destroy();
    }

    @Test
    void backfillsExistingRowsButLeavesNewRowsEligibleForOneReveal() {
        new ResourceDatabasePopulator(new ClassPathResource(
                "db/migration/V20260820202529__local_client_credentials_add_revealed_at.sql"))
                .execute(dataSource);

        Long backfilled = jdbc.sql("""
                        select count(*)
                        from local_client_credentials
                        where user_id = 'usr_existing' and revealed_at = updated_at
                        """).query(Long.class).single();
        assertThat(backfilled).isEqualTo(1L);

        jdbc.sql("""
                        insert into local_client_credentials(
                            user_id, encrypted_client_key, client_key_fingerprint, key_hint,
                            version, status, created_at, updated_at, revealed_at, revoked_at
                        ) values (
                            'usr_new', 'ciphertext-new', :fingerprint, 'tack_v1_DEF...QRST',
                            1, 'ACTIVE', current_timestamp, current_timestamp, null, null
                        )
                        """)
                .param("fingerprint", "b".repeat(64))
                .update();
        Long unrevealed = jdbc.sql("""
                        select count(*)
                        from local_client_credentials
                        where user_id = 'usr_new' and revealed_at is null
                        """).query(Long.class).single();
        assertThat(unrevealed).isEqualTo(1L);
    }
}
