package com.enterprise.testagent.persistence;

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

    @Test
    void referencesParamsSeedContainsAllPlatformDefaults() throws Exception {
        String sql = Files.readString(Path.of(
                "src/main/resources/db/migration/V20260718100000__seed_references_params.sql"));

        assertThat(sql).contains("OPENCODE_REFERENCES_DIR", "引用资产根目录", "REFERENCES_SDD_FOLDER_NAMES", "规格驱动标准目录名称");
        // 引用资产根目录：all 平台、值引用 SYS_DATA_ROOT_DIR、只读（editable=false）。
        // 值用 '$' || '{SYS_DATA_ROOT_DIR}/...' 拼接规避 Flyway 占位符替换，故断言拼接片段。
        assertThat(sql).contains(
                "'param_opencode_references_dir_all', 'OPENCODE_REFERENCES_DIR', '引用资产根目录', '$' || '{SYS_DATA_ROOT_DIR}/agent-opencode/references', 'all', false");
        // 规格驱动标准目录名称：all 平台、可改（editable=true）。
        assertThat(sql).contains(
                "'param_references_sdd_folder_names_all', 'REFERENCES_SDD_FOLDER_NAMES', '规格驱动标准目录名称', 'docs,spec', 'all', true");
    }

    @Test
    void nightExecutionCapacitySeedContainsEditableGlobalDefault() throws Exception {
        String sql = Files.readString(Path.of(
                "src/main/resources/db/migration/V20260719210000__seed_night_execution_capacity_parameter.sql"));

        assertThat(sql).contains(
                "'param_night_execution_slot_capacity_all'",
                "'NIGHT_EXECUTION_SLOT_CAPACITY'",
                "'夜间任务每时段任务上限'",
                "'20'",
                "'all'",
                "true");
    }
}
