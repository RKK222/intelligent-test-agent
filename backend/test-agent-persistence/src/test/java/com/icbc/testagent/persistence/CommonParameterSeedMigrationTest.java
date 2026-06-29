package com.icbc.testagent.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * 直接校验通用参数种子 migration，作为 H2 全量 Flyway 被旧 PostgreSQL 方言阻断时的轻量回归保护。
 */
class CommonParameterSeedMigrationTest {

    @Test
    void sysDataRootDirSeedContainsThreePlatformDefaults() throws Exception {
        String sql = Files.readString(Path.of(
                "src/main/resources/db/migration/V20260629203006__seed_sys_data_root_dir_param.sql"));

        assertThat(sql).contains("SYS_DATA_ROOT_DIR", "系统数据根目录");
        assertThat(sql).contains(
                "'param_sys_data_root_dir_macos', 'SYS_DATA_ROOT_DIR', '系统数据根目录', '$HOME/.testagent', 'macos'");
        assertThat(sql).contains(
                "'param_sys_data_root_dir_linux', 'SYS_DATA_ROOT_DIR', '系统数据根目录', '/data/.testagent', 'linux'");
        assertThat(sql).contains(
                "'param_sys_data_root_dir_windows', 'SYS_DATA_ROOT_DIR', '系统数据根目录', 'D:/data/.testagent', 'windows'");
    }
}
