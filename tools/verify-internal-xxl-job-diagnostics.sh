#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TMP_ROOT="$(mktemp -d)"
trap 'rm -rf "${TMP_ROOT}"' EXIT
FAKE_BIN="${TMP_ROOT}/bin"
mkdir -p "${FAKE_BIN}"

TROUBLESHOOTING_MANUAL="${ROOT_DIR}/deploy/internal/XXL-JOB-TROUBLESHOOTING.md"
TROUBLESHOOTING_DESIGN="${ROOT_DIR}/docs/superpowers/specs/2026-07-22-enterprise-xxl-job-troubleshooting-design.md"
TROUBLESHOOTING_PLAN="${ROOT_DIR}/docs/superpowers/plans/2026-07-22-enterprise-xxl-job-troubleshooting.md"
if [[ ! -f "${TROUBLESHOOTING_MANUAL}" ]]; then
  printf '正式手册不存在: %s\n' "${TROUBLESHOOTING_MANUAL}" >&2
  exit 1
fi

# 证据扫描器由验证器持有固定受审副本；手册中的代码块只参与逐字节合同校验，绝不作为可执行源码。
fixed_evidence_awk_program() {
  cat <<'AWK'
  {
    line=tolower($0)
    gsub(/(ticket|cookie|token|password|secret|key|authorization|platform_session_digest)[[:alnum:]_"'\''-]*[[:space:]]*[=:][[:space:]]*["'\'']*\[redacted(_query|_fragment)?\]["'\'']*/, "", line)
    gsub(/[[:alnum:]_]*(token|password|secret|digest|key)[[:alnum:]_]*=(set|set length=[0-9]+ sha256=[0-9a-f]+|unset)/, "", line)
    gsub(/\?\[redacted_query\]|#\[redacted_fragment\]/, "", line)
    if (line ~ /authorization:[[:space:]]*bearer/) leaked=1
    if (line ~ /(ticket|cookie|token|password|secret|key|authorization|platform_session_digest)[[:alnum:]_"'\''-]*[[:space:]]*[=:][[:space:]]*["'\'']*[^,;[:space:]"'\''}]/) leaked=1
    if (line ~ /[^[:space:]?#][?#][^[:space:]]+/ || line ~ /(^|[[:space:]])[?#][^[:space:]]+/) leaked=1
  }
  END { exit leaked ? 1 : 0 }
AWK
}
EVIDENCE_AWK_PROGRAM="$(fixed_evidence_awk_program)"

for required_text in \
  '122.233.30.2' \
  '122.233.30.4' \
  '122.233.30.114' \
  '122.233.30.20' \
  '122.210.106.43' \
  'diagnose-xxl-job-entry.sh' \
  'diagnose-xxl-job-frontend.sh' \
  'diagnose-xxl-job-backend.sh' \
  'xxl-job-readonly-check.sql' \
  'TEST_AGENT_NGINX_XXL_JOB_ADMINS' \
  'TEST_AGENT_XXL_JOB_ADMIN_ADDRESSES' \
  'TEST_AGENT_XXL_JOB_EXECUTOR_ADDRESS' \
  'TEST_AGENT_XXL_JOB_EXECUTOR_IP' \
  'Secure' \
  'postMessage' \
  'SKIPPED_LOCK_HELD' \
  '退出码 0' \
  '退出码 1' \
  '退出码 2'; do
  grep -Fq "${required_text}" "${TROUBLESHOOTING_MANUAL}" || {
    printf '正式手册缺少契约文本: %s\n' "${required_text}" >&2
    exit 1
  }
done

require_document_text() {
  local document_file="$1" document_text="$2"
  grep -Fq "${document_text}" "${document_file}" || {
    printf '设计/计划缺少双入口拓扑合同: %s\n' "${document_text}" >&2
    exit 1
  }
}
require_document_text "${TROUBLESHOOTING_DESIGN}" '域名 `http://mimo.sdc.cs.icbc:9996` → 企业入口/网关 → 实体 Nginx `122.233.30.2:80`'
require_document_text "${TROUBLESHOOTING_DESIGN}" 'IP `http://122.233.30.2:9996` → 实体 Nginx `122.233.30.2:9996`'
require_document_text "${TROUBLESHOOTING_DESIGN}" '实体 Nginx 同时监听 `80` 与 `9996`'
require_document_text "${TROUBLESHOOTING_PLAN}" '域名 `http://mimo.sdc.cs.icbc:9996` → 企业入口/网关 → 实体 Nginx `122.233.30.2:80`'
require_document_text "${TROUBLESHOOTING_PLAN}" 'IP `http://122.233.30.2:9996` → 实体 Nginx `122.233.30.2:9996`'
require_document_text "${TROUBLESHOOTING_PLAN}" '实体 Nginx 同时监听 `80` 与 `9996`'
for sso_contract_document in "${TROUBLESHOOTING_DESIGN}" "${TROUBLESHOOTING_PLAN}"; do
  grep -Fq 'SSO 被动证据顺序固定为：票据签发 POST → 登录 POST → iframe ready `postMessage` → Admin GET。' \
    "${sso_contract_document}" || {
    printf '设计/计划缺少真实 SSO 被动证据顺序: %s\n' "${sso_contract_document}" >&2
    exit 1
  }
done

grep -Fq 'deploy/internal/XXL-JOB-TROUBLESHOOTING.md' "${ROOT_DIR}/docs/README.md" || {
  printf 'docs/README.md 缺少 XXL-JOB 正式排查入口\n' >&2
  exit 1
}
grep -Fq 'bash tools/verify-internal-xxl-job-diagnostics.sh' "${ROOT_DIR}/docs/testing/xxl-job-integration.md" || {
  printf 'XXL-JOB 测试文档缺少诊断验证命令\n' >&2
  exit 1
}
grep -Fq '临时夹具' "${ROOT_DIR}/docs/testing/xxl-job-integration.md" || {
  printf 'XXL-JOB 测试文档缺少临时夹具边界\n' >&2
  exit 1
}
grep -Fq '浏览器现场只能被动检查事故时已经保留的证据' "${ROOT_DIR}/docs/testing/xxl-job-integration.md" || {
  printf 'XXL-JOB 测试文档缺少浏览器被动取证边界\n' >&2
  exit 1
}
portable_forbidden_sed='sed -i '
portable_forbidden_sed+="''"
if grep -Fq "${portable_forbidden_sed}" "${BASH_SOURCE[0]}"; then
  printf '诊断验证器仍使用仅 macOS 可用的 sed -i 写法\n' >&2
  exit 1
fi

validate_strict_manual_contract() {
  local manual_file="$1"
  local topology_section entry_section frontend_section backend_4_section backend_114_section
  local redis_section mysql_section browser_section task_section evidence_section executable_fences normalized_commands
  local ticket_step login_step ready_step admin_get_step relative_write_status manual_evidence_awk

  topology_section="$(awk '/^## 1\./ { active=1 } /^## 2\./ { active=0 } active' "${manual_file}")"
  entry_section="$(awk '/^## 4\./ { active=1 } /^## 5\./ { active=0 } active' "${manual_file}")"
  frontend_section="$(awk '/^## 5\./ { active=1 } /^## 6\./ { active=0 } active' "${manual_file}")"
  backend_4_section="$(awk '/^## 6\./ { active=1 } /^## 7\./ { active=0 } active' "${manual_file}")"
  backend_114_section="$(awk '/^## 7\./ { active=1 } /^## 8\./ { active=0 } active' "${manual_file}")"
  redis_section="$(awk '/^## 8\./ { active=1 } /^## 9\./ { active=0 } active' "${manual_file}")"
  mysql_section="$(awk '/^## 9\./ { active=1 } /^## 10\./ { active=0 } active' "${manual_file}")"
  browser_section="$(awk '/^## 10\./ { active=1 } /^## 11\./ { active=0 } active' "${manual_file}")"
  task_section="$(awk '/^## 11\./ { active=1 } /^## 12\./ { active=0 } active' "${manual_file}")"
  evidence_section="$(awk '/^## 14\./ { active=1 } /^## 15\./ { active=0 } active' "${manual_file}")"
  manual_evidence_awk="$(awk '
    $0 == "if awk \047" { active=1; next }
    active && $0 == "\047 \\" { exit }
    active { print }
  ' "${manual_file}")"
  [[ "${manual_evidence_awk}" == "${EVIDENCE_AWK_PROGRAM}" ]] || return 1
  executable_fences="$(awk '
    function flush_command(   command) {
      command=command_buffer
      sub(/^[[:space:]]+/, "", command)
      sub(/[[:space:]]+$/, "", command)
      if (command != "") print command
      command_buffer=""
    }
    # 只在 shell 引号与转义之外切分命令边界，不执行手册中的任何文本。
    function process_shell_line(line,   i, char, next_char, line_continued) {
      line_continued=0
      for (i=1; i<=length(line); i++) {
        char=substr(line, i, 1)
        next_char=(i<length(line) ? substr(line, i+1, 1) : "")

        if (single_quoted) {
          command_buffer=command_buffer char
          if (char == "\047") single_quoted=0
          continue
        }
        if (double_quoted) {
          if (escaped) {
            command_buffer=command_buffer char
            escaped=0
          } else if (char == "\\") {
            if (i == length(line)) line_continued=1
            else {
              command_buffer=command_buffer char
              escaped=1
            }
          } else {
            command_buffer=command_buffer char
            if (char == "\"") double_quoted=0
          }
          continue
        }
        if (escaped) {
          command_buffer=command_buffer char
          escaped=0
        } else if (char == "\\") {
          if (i == length(line)) line_continued=1
          else {
            command_buffer=command_buffer char
            escaped=1
          }
        } else if (char == "\047") {
          single_quoted=1
          command_buffer=command_buffer char
        } else if (char == "\"") {
          double_quoted=1
          command_buffer=command_buffer char
        } else if (char == ";") {
          flush_command()
        } else if (char == "&" && next_char == "&") {
          flush_command()
          i++
        } else if (char == "|") {
          flush_command()
          if (next_char == "|") i++
        } else {
          command_buffer=command_buffer char
        }
      }

      if (line_continued) command_buffer=command_buffer " "
      else if (single_quoted || double_quoted) command_buffer=command_buffer " "
      else flush_command()
      escaped=0
    }
    {
      marker=tolower($0)
      if (marker ~ /^[[:space:]]*```(bash|sh|shell|sql)[[:space:]]*$/) {
        executable=1
        next
      }
      if (marker ~ /^[[:space:]]*```[[:space:]]*$/) {
        flush_command()
        executable=0
        single_quoted=0
        double_quoted=0
        escaped=0
        next
      }
      if (executable) process_shell_line($0)
    }
    END { flush_command() }
  ' "${manual_file}")"
  normalized_commands="$(awk '
    # 将单个命令片段分词，保留引号内空格并移除 shell 引号/转义符。
    function reset_tokens(   key) {
      for (key in token) delete token[key]
      token_count=0
    }
    function finish_token() {
      if (token_started) {
        token[++token_count]=token_buffer
        token_buffer=""
        token_started=0
      }
    }
    function tokenize(line,   i, char) {
      reset_tokens()
      single_quoted=0
      double_quoted=0
      escaped=0
      for (i=1; i<=length(line); i++) {
        char=substr(line, i, 1)
        if (single_quoted) {
          if (char == "\047") single_quoted=0
          else token_buffer=token_buffer char
          token_started=1
        } else if (double_quoted) {
          if (escaped) {
            token_buffer=token_buffer char
            token_started=1
            escaped=0
          } else if (char == "\\") escaped=1
          else if (char == "\"") double_quoted=0
          else {
            token_buffer=token_buffer char
            token_started=1
          }
        } else if (escaped) {
          token_buffer=token_buffer char
          token_started=1
          escaped=0
        } else if (char == "\\") escaped=1
        else if (char == "\047") {
          single_quoted=1
          token_started=1
        } else if (char == "\"") {
          double_quoted=1
          token_started=1
        } else if (char ~ /[[:space:]]/) finish_token()
        else {
          token_buffer=token_buffer char
          token_started=1
        }
      }
      finish_token()
    }
    function is_assignment(value) {
      return value ~ /^[[:alpha:]_][[:alnum:]_]*=/
    }
    function sudo_option_needs_value(option) {
      return option == "-u" || option == "--user" || option == "-g" || option == "--group" ||
             option == "-h" || option == "--host" || option == "-p" || option == "--prompt" ||
             option == "-C" || option == "--close-from" || option == "-T" || option == "--command-timeout" ||
             option == "-r" || option == "--role" || option == "-t" || option == "--type" ||
             option == "-D" || option == "--chdir" || option == "-R" || option == "--chroot" ||
             option == "-U" || option == "--other-user"
    }
    function env_option_needs_value(option) {
      return option == "-u" || option == "--unset" || option == "-C" || option == "--chdir" ||
             option == "-S" || option == "--split-string" || option == "--argv0"
    }
    # 循环解包环境赋值、sudo 与 env，以最终可执行命令作为安全检查起点。
    function strip_wrappers(position,   changed, option) {
      do {
        changed=0
        while (position <= token_count && is_assignment(token[position])) {
          position++
          changed=1
        }
        if (tolower(token[position]) == "sudo") {
          position++
          while (position <= token_count && token[position] ~ /^-/) {
            option=token[position++]
            if (option == "--") break
            # 组合 sudo 短选项的取值位置无法在不完整模拟 sudo 的情况下证明安全，统一 fail closed。
            if (option ~ /^-[^-]/ && length(option) > 2) unsafe_wrapper=1
            if (option !~ /=/ && sudo_option_needs_value(option) && position <= token_count) position++
          }
          changed=1
        } else if (tolower(token[position]) == "env") {
          position++
          while (position <= token_count && token[position] ~ /^-/) {
            option=token[position++]
            if (option == "--") break
            # env 组合短选项可隐藏 -S 二次分词，无法证明时一律 fail closed。
            if (option ~ /^-[^-]/ && length(option) > 2) unsafe_wrapper=1
            # env split-string 会把参数重新解析成命令，本校验不执行也不模拟它，直接 fail closed。
            if (option == "-S" || option ~ /^-S./ || option == "--split-string" || option ~ /^--split-string=/) {
              unsafe_wrapper=1
              if (option !~ /=/ && option !~ /^-S./ && position <= token_count) position++
              continue
            }
            if (option !~ /=/ && env_option_needs_value(option) && position <= token_count) position++
          }
          while (position <= token_count && is_assignment(token[position])) position++
          changed=1
        }
      } while (changed && position <= token_count)
      return position
    }
    function docker_option_needs_value(option) {
      return option == "--context" || option == "-c" || option == "--config" ||
             option == "-H" || option == "--host" || option == "-l" || option == "--log-level" ||
             option == "--tlscacert" || option == "--tlscert" || option == "--tlskey"
    }
    function compose_option_needs_value(option) {
      return option == "-f" || option == "--file" || option == "-p" || option == "--project-name" ||
             option == "--profile" || option == "--env-file" || option == "--project-directory" ||
             option == "--parallel" || option == "--ansi" || option == "--progress"
    }
    function podman_option_needs_value(option) {
      return option == "-c" || option == "--connection" || option == "--url" ||
             option == "--identity" || option == "--log-level"
    }
    function option_is_known_flag(option_kind, option) {
      if (option_kind == "docker")
        return option == "-D" || option == "--debug" || option == "--tls" || option == "--tlsverify" ||
               option == "-v" || option == "--version" || option == "--help"
      if (option_kind == "compose")
        return option == "--compatibility" || option == "--dry-run" || option == "--help" || option == "--version"
      if (option_kind == "podman")
        return option == "--remote" || option == "--help" || option == "--version"
      return 0
    }
    function option_needs_value(option_kind, option) {
      return (option_kind == "docker" && docker_option_needs_value(option)) ||
             (option_kind == "compose" && compose_option_needs_value(option)) ||
             (option_kind == "podman" && podman_option_needs_value(option))
    }
    function skip_options(position, option_kind,   option, option_name) {
      while (position <= token_count && token[position] ~ /^-/) {
        option=token[position++]
        if (option == "--") break
        option_name=option
        sub(/=.*/, "", option_name)
        if (option_needs_value(option_kind, option_name)) {
          if (option !~ /=/ && position <= token_count) position++
        } else if (!option_is_known_flag(option_kind, option_name)) {
          # 未知容器选项可能吞掉下一个 action，不猜测取值位置，统一 fail closed。
          unsafe_container_option=1
        }
      }
      return position
    }
    function print_tokens(position, prefix,   output, i) {
      output=prefix
      for (i=position; i<=token_count; i++) output=output (output == "" ? "" : " ") token[i]
      if (output != "") print output
    }
    # 容器 CLI 先跳过全局/组合选项，再输出统一的实际 action 供负向规则匹配。
    function print_effective_command(position,   executable, cursor, prefix) {
      executable=tolower(token[position])
      if (executable == "docker") {
        cursor=skip_options(position + 1, "docker")
        prefix="docker"
        if (tolower(token[cursor]) == "compose") {
          cursor=skip_options(cursor + 1, "compose")
          prefix="docker compose"
        } else if (tolower(token[cursor]) == "container") {
          cursor=skip_options(cursor + 1, "docker-container")
          prefix="docker container"
        }
        print_tokens(cursor, prefix)
      } else if (executable == "docker-compose") {
        cursor=skip_options(position + 1, "compose")
        print_tokens(cursor, "docker-compose")
      } else if (executable == "podman") {
        cursor=skip_options(position + 1, "podman")
        prefix="podman"
        if (tolower(token[cursor]) == "container") {
          cursor=skip_options(cursor + 1, "podman-container")
          prefix="podman container"
        }
        print_tokens(cursor, prefix)
      } else print_tokens(position, "")
    }
    {
      unsafe_wrapper=0
      unsafe_container_option=0
      tokenize($0)
      effective_index=strip_wrappers(1)
      if (unsafe_wrapper) print "__UNSAFE_WRAPPER_OPTIONS__"
      else if (effective_index <= token_count) {
        print_effective_command(effective_index)
        if (unsafe_container_option) print "__UNSAFE_CONTAINER_OPTIONS__"
      }
    }
  ' <<<"${executable_fences}")"

  grep -Fq '| 浏览器域名入口 | `http://mimo.sdc.cs.icbc:9996` → 企业入口/网关 → 实体 Nginx `122.233.30.2:80` |' <<<"${topology_section}" || return 1
  grep -Fq '| 浏览器 IP 入口 | `http://122.233.30.2:9996` → 实体 Nginx `122.233.30.2:9996` |' <<<"${topology_section}" || return 1
  grep -Fq '| 实体 Nginx | `122.233.30.2:80` + `122.233.30.2:9996` |' <<<"${topology_section}" || return 1
  grep -Fq '| 后台 A | `122.233.30.4:8080`，Admin `18080`，executor `9999` |' <<<"${topology_section}" || return 1
  grep -Fq '| 后台 B | `122.233.30.114:8080`，Admin `18080`，executor `9999` |' <<<"${topology_section}" || return 1
  grep -Fq '| 共享 Redis | `122.233.30.20:6379` |' <<<"${topology_section}" || return 1
  grep -Fq '| 外部共享 XXL MySQL | `122.210.106.43:3306/xxl_job`（现网外部服务，不在平台节点部署容器） |' <<<"${topology_section}" || return 1

  [[ "$(grep -Fc 'bash /data/testagent/deploy/internal/diagnose-xxl-job-entry.sh' "${manual_file}")" -eq 1 ]] || return 1
  [[ "$(grep -Fc 'bash /data/testagent/deploy/internal/diagnose-xxl-job-frontend.sh' "${manual_file}")" -eq 1 ]] || return 1
  [[ "$(grep -Fc 'bash /data/testagent/deploy/internal/diagnose-xxl-job-backend.sh' "${manual_file}")" -eq 2 ]] || return 1
  [[ "$(grep -Fc -- '--expected-host 122.233.30.4 --minutes 15' "${manual_file}")" -eq 2 ]] || return 1
  [[ "$(grep -Fc -- '--expected-host 122.233.30.114 --minutes 15' "${manual_file}")" -eq 2 ]] || return 1
  [[ "$(grep -Fc -- '--expected-host' "${manual_file}")" -eq 4 ]] || return 1
  [[ "$(grep -Fc "mysql --host=122.210.106.43 --port=3306 --user='<只读账号>' --password" "${manual_file}")" -eq 1 ]] || return 1
  [[ "$(grep -Fc 'tee /data/0709/xxl-job-diagnostics-entry.log' "${manual_file}")" -eq 1 ]] || return 1
  [[ "$(grep -Fc 'tee /data/0709/xxl-job-diagnostics-frontend-122.233.30.2.log' "${manual_file}")" -eq 1 ]] || return 1
  [[ "$(grep -Fc 'tee /data/0709/xxl-job-diagnostics-backend-122.233.30.4.log' "${manual_file}")" -eq 1 ]] || return 1
  [[ "$(grep -Fc 'tee /data/0709/xxl-job-diagnostics-backend-122.233.30.114.log' "${manual_file}")" -eq 1 ]] || return 1

  grep -Fq '**操作机器：实际浏览器网段内、已放置标准发布目录的 Linux 诊断终端。工作目录：`/data/testagent`。**' <<<"${entry_section}" || return 1
  grep -Fq 'cd /data/testagent' <<<"${entry_section}" || return 1
  grep -Fq 'set -o pipefail' <<<"${entry_section}" || return 1
  grep -Fq 'bash /data/testagent/deploy/internal/diagnose-xxl-job-entry.sh' <<<"${entry_section}" || return 1
  grep -Fq 'tee /data/0709/xxl-job-diagnostics-entry.log' <<<"${entry_section}" || return 1
  grep -Fq '**操作机器：`122.233.30.2` 前端。工作目录：`/data/testagent`。**' <<<"${frontend_section}" || return 1
  grep -Fq 'cd /data/testagent' <<<"${frontend_section}" || return 1
  grep -Fq 'set -o pipefail' <<<"${frontend_section}" || return 1
  grep -Fq 'bash /data/testagent/deploy/internal/diagnose-xxl-job-frontend.sh' <<<"${frontend_section}" || return 1
  grep -Fq 'tee /data/0709/xxl-job-diagnostics-frontend-122.233.30.2.log' <<<"${frontend_section}" || return 1
  grep -Fq '只执行只读 `nginx -T` 解析并 dump 有效配置' <<<"${frontend_section}" || return 1
  if grep -Eqi '不(执行|做)配置测试' <<<"${frontend_section}"; then
    return 1
  fi

  grep -Fq '**操作机器：`122.233.30.4` 后台。工作目录：`/data/testagent`。**' <<<"${backend_4_section}" || return 1
  grep -Fq 'cd /data/testagent' <<<"${backend_4_section}" || return 1
  grep -Fq 'set -o pipefail' <<<"${backend_4_section}" || return 1
  grep -Fq 'bash /data/testagent/deploy/internal/diagnose-xxl-job-backend.sh' <<<"${backend_4_section}" || return 1
  grep -Fq -- '--expected-host 122.233.30.4 --minutes 15' <<<"${backend_4_section}" || return 1
  grep -Fq 'tee /data/0709/xxl-job-diagnostics-backend-122.233.30.4.log' <<<"${backend_4_section}" || return 1
  grep -Fq '**操作机器：`122.233.30.114` 后台。工作目录：`/data/testagent`。**' <<<"${backend_114_section}" || return 1
  grep -Fq 'cd /data/testagent' <<<"${backend_114_section}" || return 1
  grep -Fq 'set -o pipefail' <<<"${backend_114_section}" || return 1
  grep -Fq 'bash /data/testagent/deploy/internal/diagnose-xxl-job-backend.sh' <<<"${backend_114_section}" || return 1
  grep -Fq -- '--expected-host 122.233.30.114 --minutes 15' <<<"${backend_114_section}" || return 1
  grep -Fq 'tee /data/0709/xxl-job-diagnostics-backend-122.233.30.114.log' <<<"${backend_114_section}" || return 1
  grep -Fq '退出码 `2`' <<<"${backend_114_section}" || return 1
  grep -Fq '`8080` 正常而 `18080` 失败' <<<"${backend_114_section}" || return 1
  grep -Fq '`18080` 正常而 `9999` 失败' <<<"${backend_114_section}" || return 1
  grep -Fq '成功条件：本机地址为 `.114`' <<<"${backend_114_section}" || return 1
  grep -Fq '任何 `[FAIL]` 都先保存 `/data/0709/xxl-job-diagnostics-backend-122.233.30.114.log` 并停止该节点推断' <<<"${backend_114_section}" || return 1

  if grep -Eqi 'redis-cli|(^|[^[:alnum:]_])(GET|GETDEL|KEYS|SCAN)([^[:alnum:]_]|$)|test-agent:[^[:space:]]*(ticket|session)|(ticket|session)[_ -]*key' <<<"${redis_section}"; then
    return 1
  fi

  grep -Fq "mysql --host=122.210.106.43 --port=3306 --user='<只读账号>' --password" <<<"${mysql_section}" || return 1
  grep -Fq '**操作机器：获准访问 `122.210.106.43:3306` 的 DBA 受控客户端。工作目录：`/data/testagent`。**' <<<"${mysql_section}" || return 1
  grep -Fq '不在 `.2/.4/.114` 或其它平台节点部署 MySQL 容器' <<<"${mysql_section}" || return 1
  grep -Fq '不要求或允许按本手册登录外部服务宿主机' <<<"${mysql_section}" || return 1
  grep -Fq 'cd /data/testagent' <<<"${mysql_section}" || return 1
  grep -Fq 'set -o pipefail' <<<"${mysql_section}" || return 1
  grep -Fq -- '--database=xxl_job' <<<"${mysql_section}" || return 1
  grep -Fq '< /data/testagent/deploy/internal/xxl-job-readonly-check.sql' <<<"${mysql_section}" || return 1
  if grep -Eq -- '--password=' <<<"${mysql_section}"; then
    return 1
  fi

  grep -Fq '不得以删除 `Secure`' <<<"${browser_section}" || return 1
  grep -Fq '不得导出未脱敏 HAR' <<<"${browser_section}" || return 1
  grep -Fq '`postMessage` ready' <<<"${browser_section}" || return 1
  grep -Fq "Content-Security-Policy: frame-ancestors 'self'" <<<"${browser_section}" || return 1
  grep -Fq 'X-Frame-Options: SAMEORIGIN' <<<"${browser_section}" || return 1
  grep -Fq '`POST /api/internal/platform/xxl-job/sso-tickets`' <<<"${browser_section}" || return 1
  grep -Fq '`POST /xxl-job-admin/platform-sso/login`' <<<"${browser_section}" || return 1
  grep -Fq 'ready 之后的已保留 Network' <<<"${browser_section}" || return 1
  ticket_step="$(grep -nF '1. 已保留的 Network：`POST /api/internal/platform/xxl-job/sso-tickets`。' <<<"${browser_section}" | cut -d: -f1)"
  login_step="$(grep -nF '2. 已保留的 Network：`POST /xxl-job-admin/platform-sso/login` 完成响应。' <<<"${browser_section}" | cut -d: -f1)"
  ready_step="$(grep -nF '3. Network 之外的被动证据：父页面已显示 connected/ready，或已有 instrumentation 记录同源 ready `postMessage`。' <<<"${browser_section}" | cut -d: -f1)"
  admin_get_step="$(grep -nF '4. ready 之后的已保留 Network：重定向及 Admin `GET /xxl-job-admin/`、静态资源。' <<<"${browser_section}" | cut -d: -f1)"
  [[ "${ticket_step}" =~ ^[0-9]+$ && "${login_step}" =~ ^[0-9]+$ && "${ready_step}" =~ ^[0-9]+$ && "${admin_get_step}" =~ ^[0-9]+$ ]] || return 1
  (( ticket_step < login_step && login_step < ready_step && ready_step < admin_get_step )) || return 1
  grep -Fq 'DevTools Network 不会记录 `postMessage`' <<<"${browser_section}" || return 1
  grep -Fq '如果这两类 ready 证据都未保留，立即停止并升级，不得重放' <<<"${browser_section}" || return 1
  grep -Fq '禁止为了诊断主动刷新、重试、重放或重新进入页面' <<<"${browser_section}" || return 1
  grep -Fq '如果 Network 未保留本次失败请求，立即停止并升级' <<<"${browser_section}" || return 1
  if grep -Eqi '打开开发者工具后再复现|刷新页面并|重新加载页面并|重试(该|上述|SSO|登录|请求)|重放(该|上述|SSO|登录|请求)|再次(打开|进入|访问).*(复现|重试)|请.*复现|复现一次|reproduce|retry the|reload the' <<<"${browser_section}"; then
    return 1
  fi
  if grep -Eqi '删除.*(Content-Security-Policy|X-Frame-Options)|关闭.*(CSP|Content-Security-Policy|X-Frame-Options)|frame-ancestors[[:space:]]+\*|放宽.*(CSP|frame|iframe|origin)|允许跨源[[:space:]]*iframe' <<<"${browser_section}"; then
    return 1
  fi

  grep -Fq '**操作机器：`122.233.30.4` 后台。证据目录：`/data/0709`。**' <<<"${task_section}" || return 1
  grep -Fq '/data/0709/xxl-job-diagnostics-backend-122.233.30.4.log' <<<"${task_section}" || return 1
  grep -Fq '**操作机器：`122.233.30.114` 后台。证据目录：`/data/0709`。**' <<<"${task_section}" || return 1
  grep -Fq '/data/0709/xxl-job-diagnostics-backend-122.233.30.114.log' <<<"${task_section}" || return 1
  grep -Fq '**操作机器：持有外部 MySQL 只读查询结果的 DBA 受控客户端。证据目录：`/data/0709`。**' <<<"${task_section}" || return 1
  grep -Fq '/data/0709/xxl-job-diagnostics-mysql-external.log' <<<"${task_section}" || return 1
  grep -Fq '禁止手动触发任务' <<<"${task_section}" || return 1
  [[ "$(grep -Fc '成功证据：' <<<"${task_section}")" -eq 3 ]] || return 1
  [[ "$(grep -Fo '停止点：' <<<"${task_section}" | wc -l | tr -d ' ')" -eq 3 ]] || return 1
  grep -Fq '停止并把该绝对路径交 `.4` executor/网络负责人' <<<"${task_section}" || return 1
  grep -Fq '停止并把该绝对路径交 `.114` executor/网络负责人' <<<"${task_section}" || return 1
  grep -Fq '任一 registry 节点或任务元数据缺失时停止' <<<"${task_section}" || return 1

  grep -Fq '**操作机器：持有五份脱敏日志的受控取证终端。证据目录：`/data/0709`。**' <<<"${evidence_section}" || return 1
  for evidence_file in \
    xxl-job-diagnostics-entry.log \
    xxl-job-diagnostics-frontend-122.233.30.2.log \
    xxl-job-diagnostics-backend-122.233.30.4.log \
    xxl-job-diagnostics-backend-122.233.30.114.log \
    xxl-job-diagnostics-mysql-external.log; do
    grep -Fq "/data/0709/${evidence_file}" <<<"${evidence_section}" || return 1
  done
  grep -Fq '>/dev/null' <<<"${evidence_section}" || return 1
  grep -Fq '[STOP] 证据疑似仍含敏感值' <<<"${evidence_section}" || return 1
  grep -Fq '成功条件：只输出 `[PASS]` 且退出 `0`' <<<"${evidence_section}" || return 1
  grep -Fq '失败停止点：出现 `[STOP]` 或退出非零时' <<<"${evidence_section}" || return 1

  if grep -Eiq '^[[:space:]]*(curl|wget|http|httpie|xh|Invoke-WebRequest|python3?)[[:space:]].*(sso-tickets|platform-sso)' "${manual_file}"; then
    return 1
  fi
  if grep -Eiq '^[[:space:]]*(xxl-job(-cli)?|job-cli)[[:space:]]+(trigger|run)|^[[:space:]]*(curl|wget|http|httpie|xh)[[:space:]].*/(trigger|run)' "${manual_file}"; then
    return 1
  fi
  if grep -Eqi '^[[:space:]]*(INSERT|UPDATE|DELETE|REPLACE|MERGE|TRUNCATE|ALTER|CREATE|DROP|CALL|GRANT|REVOKE|LOCK|UNLOCK|SET[[:space:]]+GLOBAL)([[:space:];]|$)' "${manual_file}"; then
    return 1
  fi
  if grep -Eqi '^[[:space:]]*(sudo[[:space:]]+)?(systemctl|service)[[:space:]]+(start|stop|restart|reload)|^[[:space:]]*nginx[[:space:]].*(-s[[:space:]]+reload|reload)|^[[:space:]]*(sed[[:space:]]+-i|perl[[:space:]]+-pi)|^[[:space:]]*(echo|printf|cat)[[:space:]].*>+[[:space:]]*/data/testagent/(config|deploy)|^[[:space:]]*(cp|mv|install)[[:space:]].*[[:space:]]/data/testagent/config/|(^|[[:space:]])>>[[:space:]]*/data/testagent/(config|deploy)' "${manual_file}"; then
    return 1
  fi
  if grep -Eqi '^[[:space:]]*(redis-cli|valkey-cli)[[:space:]].*(GET|GETDEL|MGET|HGET|SCAN|KEYS).*(ticket|session)' <<<"${normalized_commands}"; then
    return 1
  fi
  if grep -Eq '__UNSAFE_(WRAPPER|CONTAINER)_OPTIONS__' <<<"${normalized_commands}"; then
    return 1
  fi
  if grep -Eqi '^[[:space:]]*mysql[[:space:]].*((--execute(=|[[:space:]])|-e[^[:space:]]*).*(INSERT|UPDATE|DELETE|REPLACE|MERGE|TRUNCATE|ALTER|CREATE|DROP|CALL|GRANT|REVOKE))' <<<"${normalized_commands}"; then
    return 1
  fi
  if grep -Eqi '^[[:space:]]*(systemctl[[:space:]]+(start|stop|restart|reload)|service[[:space:]]+[^[:space:]]+[[:space:]]+(start|stop|restart|reload)|(kill|pkill|killall)[[:space:]].*(-HUP|-1|SIGHUP|-s[[:space:]]+HUP))' <<<"${normalized_commands}"; then
    return 1
  fi
  if grep -Eqi '^[[:space:]]*(docker[[:space:]]+((compose|container)[[:space:]]+)?|docker-compose[[:space:]]+|podman[[:space:]]+(container[[:space:]]+)?)(restart|start|stop|rm|kill|up|down|create|run|pause|unpause|scale)([[:space:]]|$)' <<<"${normalized_commands}"; then
    return 1
  fi
  if grep -Eqi '^[[:space:]]*(sed[[:space:]]+-i|perl[[:space:]]+-pi)|(^|[[:space:]])>+[[:space:]]*(/data/testagent/config/|/data/apps/nginx/([^[:space:]]*/)?conf/)|(^|[[:space:]|;&])tee([[:space:]]+-[^[:space:]]+)*[[:space:]]+(/data/testagent/config/|/data/apps/nginx/([^[:space:]]*/)?conf/)|^[[:space:]]*(cp|mv|install)[[:space:]].*[[:space:]](/data/testagent/config/|/data/apps/nginx/([^[:space:]]*/)?conf/)' <<<"${normalized_commands}"; then
    return 1
  fi
  if awk '
    {
      marker=tolower($0)
      if (marker ~ /^[[:space:]]*```(bash|sh|shell|sql)[[:space:]]*$/) {
        executable=1
        in_config_dir=0
        next
      }
      if (marker ~ /^[[:space:]]*```[[:space:]]*$/) {
        executable=0
        in_config_dir=0
        next
      }
      if (!executable) next

      line=tolower($0)
      if (line ~ /(^|[;&|][[:space:]]*)cd[[:space:]]+(--[[:space:]]+)?["'\''"]*\/data\/testagent\/config([\/"'\''[:space:]]|$)/ ||
          line ~ /(^|[;&|][[:space:]]*)cd[[:space:]]+(--[[:space:]]+)?["'\''"]*\/data\/apps\/nginx\/([^[:space:]"'\''\/]+\/)*conf([\/"'\''[:space:]]|$)/) {
        in_config_dir=1
      } else if (line ~ /(^|[;&|][[:space:]]*)cd[[:space:]]+/) {
        in_config_dir=0
      }
      if (in_config_dir &&
          (line ~ /(^|[[:space:]])>+[[:space:]]*[^\/[:space:]]/ ||
           line ~ /(^|[[:space:]|;&])tee([[:space:]]+-[^[:space:]]+)*[[:space:]]+[^\/[:space:]]/ ||
           line ~ /^[[:space:]]*(cp|mv|install)[[:space:]].*[[:space:]][^\/[:space:]]+[[:space:]]*$/)) {
        unsafe=1
      }
    }
    END { exit unsafe ? 0 : 1 }
  ' "${manual_file}"; then
    return 1
  else
    relative_write_status=$?
    [[ "${relative_write_status}" -eq 1 ]] || return 1
  fi
}

validate_strict_manual_contract "${TROUBLESHOOTING_MANUAL}" || {
  printf '正式手册未满足逐机绝对命令或安全负向契约\n' >&2
  exit 1
}
grep -Fq '不访问 `122.233.30.2`、`122.233.30.4`、`122.233.30.114`、`122.233.30.20` 或外部 MySQL `122.210.106.43`' \
  "${ROOT_DIR}/docs/testing/xxl-job-integration.md" || {
  printf 'XXL-JOB 测试文档缺少不访问固定企业地址的边界\n' >&2
  exit 1
}

UNSAFE_HOST_MANUAL="${TMP_ROOT}/unsafe-host-manual.md"
UNSAFE_REDIS_MANUAL="${TMP_ROOT}/unsafe-redis-manual.md"
UNSAFE_PASSWORD_MANUAL="${TMP_ROOT}/unsafe-password-manual.md"
UNSAFE_SECURE_MANUAL="${TMP_ROOT}/unsafe-secure-manual.md"
UNSAFE_ACTIVE_REPLAY_MANUAL="${TMP_ROOT}/unsafe-active-replay-manual.md"
UNSAFE_HTTP_CLIENT_MANUAL="${TMP_ROOT}/unsafe-http-client-manual.md"
UNSAFE_TASK_TRIGGER_MANUAL="${TMP_ROOT}/unsafe-task-trigger-manual.md"
UNSAFE_SQL_DML_MANUAL="${TMP_ROOT}/unsafe-sql-dml-manual.md"
UNSAFE_CONFIG_WRITE_MANUAL="${TMP_ROOT}/unsafe-config-write-manual.md"
UNSAFE_SERVICE_RELOAD_MANUAL="${TMP_ROOT}/unsafe-service-reload-manual.md"
UNSAFE_FRAME_POLICY_MANUAL="${TMP_ROOT}/unsafe-frame-policy-manual.md"
UNSAFE_DOMAIN_MANUAL="${TMP_ROOT}/unsafe-domain-manual.md"
UNSAFE_PORT_MANUAL="${TMP_ROOT}/unsafe-port-manual.md"
UNSAFE_DATABASE_MANUAL="${TMP_ROOT}/unsafe-database-manual.md"
UNSAFE_EXTRA_BACKEND_MANUAL="${TMP_ROOT}/unsafe-extra-backend-manual.md"
UNSAFE_MISSING_TASK_BOUNDARIES_MANUAL="${TMP_ROOT}/unsafe-missing-task-boundaries-manual.md"
UNSAFE_MISSING_EVIDENCE_BOUNDARIES_MANUAL="${TMP_ROOT}/unsafe-missing-evidence-boundaries-manual.md"
UNSAFE_MISSING_114_SUCCESS_MANUAL="${TMP_ROOT}/unsafe-missing-114-success-manual.md"
UNSAFE_REOPEN_BROWSER_MANUAL="${TMP_ROOT}/unsafe-reopen-browser-manual.md"
UNSAFE_NGINX_WORDING_MANUAL="${TMP_ROOT}/unsafe-nginx-wording-manual.md"
UNSAFE_SSO_ORDER_MANUAL="${TMP_ROOT}/unsafe-sso-order-manual.md"
UNSAFE_GLOBAL_REDIS_MANUAL="${TMP_ROOT}/unsafe-global-redis-manual.md"
UNSAFE_GLOBAL_MYSQL_EXEC_MANUAL="${TMP_ROOT}/unsafe-global-mysql-exec-manual.md"
UNSAFE_GLOBAL_MYSQL_E_MANUAL="${TMP_ROOT}/unsafe-global-mysql-e-manual.md"
UNSAFE_GLOBAL_SERVICE_MANUAL="${TMP_ROOT}/unsafe-global-service-manual.md"
UNSAFE_GLOBAL_HUP_MANUAL="${TMP_ROOT}/unsafe-global-hup-manual.md"
UNSAFE_GLOBAL_SED_MANUAL="${TMP_ROOT}/unsafe-global-sed-manual.md"
UNSAFE_GLOBAL_REDIRECT_MANUAL="${TMP_ROOT}/unsafe-global-redirect-manual.md"
UNSAFE_GLOBAL_TEE_NGINX_MANUAL="${TMP_ROOT}/unsafe-global-tee-nginx-manual.md"
UNSAFE_GLOBAL_CP_MANUAL="${TMP_ROOT}/unsafe-global-cp-manual.md"
UNSAFE_GLOBAL_REDIS_MULTILINE_MANUAL="${TMP_ROOT}/unsafe-global-redis-multiline-manual.md"
UNSAFE_GLOBAL_MYSQL_COMPACT_E_MANUAL="${TMP_ROOT}/unsafe-global-mysql-compact-e-manual.md"
UNSAFE_GLOBAL_SUDO_SERVICE_MANUAL="${TMP_ROOT}/unsafe-global-sudo-service-manual.md"
UNSAFE_GLOBAL_KILL_SIGNAL_MANUAL="${TMP_ROOT}/unsafe-global-kill-signal-manual.md"
UNSAFE_GLOBAL_RELATIVE_CONFIG_WRITE_MANUAL="${TMP_ROOT}/unsafe-global-relative-config-write-manual.md"
UNSAFE_GLOBAL_CHAINED_REDIS_MANUAL="${TMP_ROOT}/unsafe-global-chained-redis-manual.md"
UNSAFE_GLOBAL_SUDO_REDIS_MANUAL="${TMP_ROOT}/unsafe-global-sudo-redis-manual.md"
UNSAFE_GLOBAL_PIPE_ENV_REDIS_MANUAL="${TMP_ROOT}/unsafe-global-pipe-env-redis-manual.md"
UNSAFE_GLOBAL_SEMICOLON_REDIS_MANUAL="${TMP_ROOT}/unsafe-global-semicolon-redis-manual.md"
UNSAFE_GLOBAL_OR_REDIS_MANUAL="${TMP_ROOT}/unsafe-global-or-redis-manual.md"
UNSAFE_GLOBAL_SUDO_MYSQL_MANUAL="${TMP_ROOT}/unsafe-global-sudo-mysql-manual.md"
UNSAFE_GLOBAL_ENV_MYSQL_MANUAL="${TMP_ROOT}/unsafe-global-env-mysql-manual.md"
UNSAFE_GLOBAL_DOCKER_RESTART_MANUAL="${TMP_ROOT}/unsafe-global-docker-restart-manual.md"
UNSAFE_GLOBAL_DOCKER_COMPOSE_START_MANUAL="${TMP_ROOT}/unsafe-global-docker-compose-start-manual.md"
UNSAFE_GLOBAL_DOCKER_COMPOSE_STOP_MANUAL="${TMP_ROOT}/unsafe-global-docker-compose-stop-manual.md"
UNSAFE_GLOBAL_PODMAN_RM_MANUAL="${TMP_ROOT}/unsafe-global-podman-rm-manual.md"
UNSAFE_GLOBAL_PODMAN_KILL_MANUAL="${TMP_ROOT}/unsafe-global-podman-kill-manual.md"
UNSAFE_GLOBAL_SUDO_USER_REDIS_MANUAL="${TMP_ROOT}/unsafe-global-sudo-user-redis-manual.md"
UNSAFE_GLOBAL_SUDO_LONG_USER_REDIS_MANUAL="${TMP_ROOT}/unsafe-global-sudo-long-user-redis-manual.md"
UNSAFE_GLOBAL_EMPTY_ASSIGNMENT_REDIS_MANUAL="${TMP_ROOT}/unsafe-global-empty-assignment-redis-manual.md"
UNSAFE_GLOBAL_ENV_IGNORE_REDIS_MANUAL="${TMP_ROOT}/unsafe-global-env-ignore-redis-manual.md"
UNSAFE_GLOBAL_ENV_QUOTED_MYSQL_MANUAL="${TMP_ROOT}/unsafe-global-env-quoted-mysql-manual.md"
UNSAFE_GLOBAL_DOCKER_COMPOSE_FILE_STOP_MANUAL="${TMP_ROOT}/unsafe-global-docker-compose-file-stop-manual.md"
UNSAFE_GLOBAL_DOCKER_COMPOSE_FILE_RESTART_MANUAL="${TMP_ROOT}/unsafe-global-docker-compose-file-restart-manual.md"
UNSAFE_GLOBAL_DOCKER_CONTEXT_RESTART_MANUAL="${TMP_ROOT}/unsafe-global-docker-context-restart-manual.md"
UNSAFE_GLOBAL_PODMAN_REMOTE_KILL_MANUAL="${TMP_ROOT}/unsafe-global-podman-remote-kill-manual.md"
UNSAFE_GLOBAL_ENV_SPLIT_REDIS_MANUAL="${TMP_ROOT}/unsafe-global-env-split-redis-manual.md"
UNSAFE_GLOBAL_ENV_LONG_SPLIT_MYSQL_MANUAL="${TMP_ROOT}/unsafe-global-env-long-split-mysql-manual.md"
UNSAFE_GLOBAL_SUDO_CHDIR_REDIS_MANUAL="${TMP_ROOT}/unsafe-global-sudo-chdir-redis-manual.md"
UNSAFE_GLOBAL_SUDO_SHORT_CHDIR_REDIS_MANUAL="${TMP_ROOT}/unsafe-global-sudo-short-chdir-redis-manual.md"
UNSAFE_GLOBAL_SUDO_COMBINED_USER_REDIS_MANUAL="${TMP_ROOT}/unsafe-global-sudo-combined-user-redis-manual.md"
UNSAFE_GLOBAL_SUDO_COMBINED_CHDIR_REDIS_MANUAL="${TMP_ROOT}/unsafe-global-sudo-combined-chdir-redis-manual.md"
UNSAFE_GLOBAL_ENV_COMBINED_SPLIT_MANUAL="${TMP_ROOT}/unsafe-global-env-combined-split-manual.md"
UNSAFE_GLOBAL_DOCKER_TLSCACERT_RESTART_MANUAL="${TMP_ROOT}/unsafe-global-docker-tlscacert-restart-manual.md"
UNSAFE_GLOBAL_DOCKER_COMPOSE_UP_MANUAL="${TMP_ROOT}/unsafe-global-docker-compose-up-manual.md"
UNSAFE_GLOBAL_DOCKER_COMPOSE_DOWN_MANUAL="${TMP_ROOT}/unsafe-global-docker-compose-down-manual.md"
UNSAFE_GLOBAL_DOCKER_CREATE_MANUAL="${TMP_ROOT}/unsafe-global-docker-create-manual.md"
UNSAFE_GLOBAL_DOCKER_RUN_MANUAL="${TMP_ROOT}/unsafe-global-docker-run-manual.md"
UNSAFE_GLOBAL_DOCKER_PAUSE_MANUAL="${TMP_ROOT}/unsafe-global-docker-pause-manual.md"
UNSAFE_GLOBAL_DOCKER_UNPAUSE_MANUAL="${TMP_ROOT}/unsafe-global-docker-unpause-manual.md"
UNSAFE_GLOBAL_DOCKER_COMPOSE_SCALE_MANUAL="${TMP_ROOT}/unsafe-global-docker-compose-scale-manual.md"
UNSAFE_GLOBAL_DOCKER_CONTAINER_UNKNOWN_OPTION_MANUAL="${TMP_ROOT}/unsafe-global-docker-container-unknown-option-manual.md"
UNSAFE_GLOBAL_PODMAN_CONTAINER_UNKNOWN_OPTION_MANUAL="${TMP_ROOT}/unsafe-global-podman-container-unknown-option-manual.md"
SAFE_PASSIVE_PATH_MANUAL="${TMP_ROOT}/safe-passive-path-manual.md"
SAFE_PASSIVE_COMMAND_MENTIONS_MANUAL="${TMP_ROOT}/safe-passive-command-mentions-manual.md"
SAFE_PREFIXED_READONLY_MANUAL="${TMP_ROOT}/safe-prefixed-readonly-manual.md"
SAFE_QUOTED_OPERATORS_MANUAL="${TMP_ROOT}/safe-quoted-operators-manual.md"
UNSAFE_EVIDENCE_AWK_INJECTION_MANUAL="${TMP_ROOT}/unsafe-evidence-awk-injection-manual.md"
EVIDENCE_AWK_INJECTION_MARKER="${TMP_ROOT}/evidence-awk-injection-executed"
awk '
  !changed && /--expected-host 122\.233\.30\.4 --minutes 15/ {
    sub(/--expected-host 122\.233\.30\.4 --minutes 15/, "--expected-host 122.233.30.114 --minutes 15")
    changed=1
  }
  { print }
' "${TROUBLESHOOTING_MANUAL}" >"${UNSAFE_HOST_MANUAL}"
awk '/^## 9\./ { print "redis-cli GET diagnostic-ticket-key" } { print }' \
  "${TROUBLESHOOTING_MANUAL}" >"${UNSAFE_REDIS_MANUAL}"
awk '
  /^## 9\./ { mysql_section=1 }
  mysql_section && !changed && /--password / {
    sub(/--password /, "--password=diagnostic-plaintext ")
    changed=1
  }
  { print }
' "${TROUBLESHOOTING_MANUAL}" >"${UNSAFE_PASSWORD_MANUAL}"
awk '
  !changed && /不得以删除 `Secure`/ {
    sub(/不得以删除 `Secure`/, "建议删除 `Secure`")
    changed=1
  }
  { print }
' "${TROUBLESHOOTING_MANUAL}" >"${UNSAFE_SECURE_MANUAL}"
awk '/^## 11\./ { print "打开开发者工具后刷新页面并重试上述 SSO 请求。" } { print }' \
  "${TROUBLESHOOTING_MANUAL}" >"${UNSAFE_ACTIVE_REPLAY_MANUAL}"
awk '/^## 11\./ { print "wget --post-data=diagnostic http://mimo.sdc.cs.icbc:9996/api/internal/platform/xxl-job/sso-tickets" } { print }' \
  "${TROUBLESHOOTING_MANUAL}" >"${UNSAFE_HTTP_CLIENT_MANUAL}"
awk '/^## 12\./ { print "xxl-job-cli trigger diagnostic-task" } { print }' \
  "${TROUBLESHOOTING_MANUAL}" >"${UNSAFE_TASK_TRIGGER_MANUAL}"
awk '/^## 10\./ { print "UPDATE xxl_job_info SET trigger_status = 1;" } { print }' \
  "${TROUBLESHOOTING_MANUAL}" >"${UNSAFE_SQL_DML_MANUAL}"
awk '/^## 8\./ { print "sed -i s/old/new/ /data/testagent/config/backend.env" } { print }' \
  "${TROUBLESHOOTING_MANUAL}" >"${UNSAFE_CONFIG_WRITE_MANUAL}"
awk '/^## 6\./ { print "systemctl reload nginx" } { print }' \
  "${TROUBLESHOOTING_MANUAL}" >"${UNSAFE_SERVICE_RELOAD_MANUAL}"
awk '/^## 11\./ { print "删除 Content-Security-Policy 和 X-Frame-Options，将 frame-ancestors *，允许跨源 iframe。" } { print }' \
  "${TROUBLESHOOTING_MANUAL}" >"${UNSAFE_FRAME_POLICY_MANUAL}"
awk '
  !changed && /http:\/\/mimo\.sdc\.cs\.icbc:9996/ {
    sub(/mimo\.sdc\.cs\.icbc/, "unsafe.example.internal")
    changed=1
  }
  { print }
' "${TROUBLESHOOTING_MANUAL}" >"${UNSAFE_DOMAIN_MANUAL}"
awk '
  /^## 1\./ { topology=1 }
  /^## 2\./ { topology=0 }
  topology && !changed && /122\.233\.30\.2:9996/ {
    sub(/122\.233\.30\.2:9996/, "122.233.30.2:9997")
    changed=1
  }
  { print }
' "${TROUBLESHOOTING_MANUAL}" >"${UNSAFE_PORT_MANUAL}"
awk '
  /^## 1\./ { topology=1 }
  /^## 2\./ { topology=0 }
  topology && !changed && /3306\/xxl_job/ {
    sub(/3306\/xxl_job/, "3306/unsafe_xxl_job")
    changed=1
  }
  { print }
' "${TROUBLESHOOTING_MANUAL}" >"${UNSAFE_DATABASE_MANUAL}"
awk '/^## 8\./ { print "bash /data/testagent/deploy/internal/diagnose-xxl-job-backend.sh --expected-host 122.233.30.5 --minutes 15" } { print }' \
  "${TROUBLESHOOTING_MANUAL}" >"${UNSAFE_EXTRA_BACKEND_MANUAL}"
awk '
  /^## 11\./ { section=1 }
  /^## 12\./ { section=0 }
  section && /^(成功证据|停止点)：/ { next }
  { print }
' "${TROUBLESHOOTING_MANUAL}" >"${UNSAFE_MISSING_TASK_BOUNDARIES_MANUAL}"
awk '
  /^## 14\./ { section=1 }
  /^## 15\./ { section=0 }
  section && /^(成功条件|失败停止点)：/ { next }
  { print }
' "${TROUBLESHOOTING_MANUAL}" >"${UNSAFE_MISSING_EVIDENCE_BOUNDARIES_MANUAL}"
awk '
  /^## 7\./ { section=1 }
  /^## 8\./ { section=0 }
  section && /^成功条件：/ { next }
  { print }
' "${TROUBLESHOOTING_MANUAL}" >"${UNSAFE_MISSING_114_SUCCESS_MANUAL}"
awk '/^## 11\./ { print "请再次打开管理页以复现一次 SSO 故障。" } { print }' \
  "${TROUBLESHOOTING_MANUAL}" >"${UNSAFE_REOPEN_BROWSER_MANUAL}"
awk '/^## 6\./ { print "该脚本不执行配置测试。" } { print }' \
  "${TROUBLESHOOTING_MANUAL}" >"${UNSAFE_NGINX_WORDING_MANUAL}"
awk '
  /^3\. Network 之外的被动证据：/ { sub(/^3\./, "4.") }
  /^4\. ready 之后的已保留 Network：/ { sub(/^4\./, "3.") }
  { print }
' "${TROUBLESHOOTING_MANUAL}" >"${UNSAFE_SSO_ORDER_MANUAL}"

make_global_fence_mutation() {
  local target="$1" fixture_command="$2"
  XXL_DIAG_FIXTURE_COMMAND="${fixture_command}" awk '
    /^## 2\./ {
      print "```bash"
      print ENVIRON["XXL_DIAG_FIXTURE_COMMAND"]
      print "```"
      print ""
    }
    { print }
  ' "${TROUBLESHOOTING_MANUAL}" >"${target}"
}

make_global_fence_mutation "${UNSAFE_GLOBAL_REDIS_MANUAL}" \
  'redis-cli GET test-agent:ticket:diagnostic'
make_global_fence_mutation "${UNSAFE_GLOBAL_MYSQL_EXEC_MANUAL}" \
  "mysql --execute='UPDATE xxl_job_info SET trigger_status=1'"
make_global_fence_mutation "${UNSAFE_GLOBAL_MYSQL_E_MANUAL}" \
  "mysql -e 'DELETE FROM xxl_job_info'"
make_global_fence_mutation "${UNSAFE_GLOBAL_SERVICE_MANUAL}" \
  'service nginx restart'
make_global_fence_mutation "${UNSAFE_GLOBAL_HUP_MANUAL}" \
  'kill -HUP 4242'
make_global_fence_mutation "${UNSAFE_GLOBAL_SED_MANUAL}" \
  'sed -i s/old/new/ /data/testagent/config/backend.env'
make_global_fence_mutation "${UNSAFE_GLOBAL_REDIRECT_MANUAL}" \
  "printf '%s\\n' unsafe > /data/testagent/config/backend.env"
make_global_fence_mutation "${UNSAFE_GLOBAL_TEE_NGINX_MANUAL}" \
  "printf '%s\\n' unsafe | tee /data/apps/nginx/conf/nginx.conf"
make_global_fence_mutation "${UNSAFE_GLOBAL_CP_MANUAL}" \
  'cp /tmp/backend.env /data/testagent/config/backend.env'
make_global_fence_mutation "${UNSAFE_GLOBAL_REDIS_MULTILINE_MANUAL}" \
  'redis-cli \
  GET \
  test-agent:ticket:diagnostic'
make_global_fence_mutation "${UNSAFE_GLOBAL_MYSQL_COMPACT_E_MANUAL}" \
  "mysql -e'DELETE FROM xxl_job_info'"
make_global_fence_mutation "${UNSAFE_GLOBAL_SUDO_SERVICE_MANUAL}" \
  'sudo service nginx restart'
make_global_fence_mutation "${UNSAFE_GLOBAL_KILL_SIGNAL_MANUAL}" \
  'kill -s HUP 4242'
make_global_fence_mutation "${UNSAFE_GLOBAL_RELATIVE_CONFIG_WRITE_MANUAL}" \
  'cd /data/apps/nginx/conf
printf '\''unsafe\n'\'' > nginx.conf'
make_global_fence_mutation "${UNSAFE_GLOBAL_CHAINED_REDIS_MANUAL}" \
  'cd /tmp && redis-cli GET test-agent:ticket:diagnostic'
make_global_fence_mutation "${UNSAFE_GLOBAL_SUDO_REDIS_MANUAL}" \
  'sudo redis-cli GET test-agent:session:diagnostic'
make_global_fence_mutation "${UNSAFE_GLOBAL_PIPE_ENV_REDIS_MANUAL}" \
  "printf '%s\\n' diagnostic | env TRACE=1 redis-cli GET test-agent:ticket:diagnostic"
make_global_fence_mutation "${UNSAFE_GLOBAL_SEMICOLON_REDIS_MANUAL}" \
  'cd /tmp; redis-cli GET test-agent:session:diagnostic'
make_global_fence_mutation "${UNSAFE_GLOBAL_OR_REDIS_MANUAL}" \
  'false || sudo redis-cli GET test-agent:ticket:diagnostic'
make_global_fence_mutation "${UNSAFE_GLOBAL_SUDO_MYSQL_MANUAL}" \
  "sudo mysql -e'UPDATE xxl_job_info SET trigger_status=1'"
make_global_fence_mutation "${UNSAFE_GLOBAL_ENV_MYSQL_MANUAL}" \
  "env X=1 mysql --execute='DELETE FROM xxl_job_info'"
make_global_fence_mutation "${UNSAFE_GLOBAL_DOCKER_RESTART_MANUAL}" \
  'docker restart nginx'
make_global_fence_mutation "${UNSAFE_GLOBAL_DOCKER_COMPOSE_START_MANUAL}" \
  'docker compose start nginx'
make_global_fence_mutation "${UNSAFE_GLOBAL_DOCKER_COMPOSE_STOP_MANUAL}" \
  'docker-compose stop nginx'
make_global_fence_mutation "${UNSAFE_GLOBAL_PODMAN_RM_MANUAL}" \
  'podman rm nginx'
make_global_fence_mutation "${UNSAFE_GLOBAL_PODMAN_KILL_MANUAL}" \
  'podman kill nginx'
make_global_fence_mutation "${UNSAFE_GLOBAL_SUDO_USER_REDIS_MANUAL}" \
  'sudo -u redis redis-cli GET test-agent:ticket:x'
make_global_fence_mutation "${UNSAFE_GLOBAL_SUDO_LONG_USER_REDIS_MANUAL}" \
  'sudo --user=redis redis-cli GET test-agent:session:x'
make_global_fence_mutation "${UNSAFE_GLOBAL_EMPTY_ASSIGNMENT_REDIS_MANUAL}" \
  'TRACE= redis-cli GET test-agent:session:x'
make_global_fence_mutation "${UNSAFE_GLOBAL_ENV_IGNORE_REDIS_MANUAL}" \
  'env -i TRACE=1 redis-cli GET test-agent:ticket:x'
make_global_fence_mutation "${UNSAFE_GLOBAL_ENV_QUOTED_MYSQL_MANUAL}" \
  "env --ignore-environment TRACE='a b' mysql -e'UPDATE x SET y=1'"
make_global_fence_mutation "${UNSAFE_GLOBAL_DOCKER_COMPOSE_FILE_STOP_MANUAL}" \
  'docker compose -f stack.yml stop nginx'
make_global_fence_mutation "${UNSAFE_GLOBAL_DOCKER_COMPOSE_FILE_RESTART_MANUAL}" \
  'docker-compose -f stack.yml restart nginx'
make_global_fence_mutation "${UNSAFE_GLOBAL_DOCKER_CONTEXT_RESTART_MANUAL}" \
  'docker --context prod restart nginx'
make_global_fence_mutation "${UNSAFE_GLOBAL_PODMAN_REMOTE_KILL_MANUAL}" \
  'podman --remote kill nginx'
make_global_fence_mutation "${UNSAFE_GLOBAL_ENV_SPLIT_REDIS_MANUAL}" \
  "env -S 'redis-cli GET test-agent:ticket:x'"
make_global_fence_mutation "${UNSAFE_GLOBAL_ENV_LONG_SPLIT_MYSQL_MANUAL}" \
  "env --split-string='mysql -eDELETE FROM x'"
make_global_fence_mutation "${UNSAFE_GLOBAL_SUDO_CHDIR_REDIS_MANUAL}" \
  'sudo --chdir /tmp redis-cli GET test-agent:ticket:x'
make_global_fence_mutation "${UNSAFE_GLOBAL_SUDO_SHORT_CHDIR_REDIS_MANUAL}" \
  'sudo -D /tmp redis-cli GET test-agent:session:x'
make_global_fence_mutation "${UNSAFE_GLOBAL_SUDO_COMBINED_USER_REDIS_MANUAL}" \
  'sudo -nu redis redis-cli GET test-agent:ticket:x'
make_global_fence_mutation "${UNSAFE_GLOBAL_SUDO_COMBINED_CHDIR_REDIS_MANUAL}" \
  'sudo -nD /tmp redis-cli GET test-agent:ticket:x'
make_global_fence_mutation "${UNSAFE_GLOBAL_ENV_COMBINED_SPLIT_MANUAL}" \
  "env -iS 'printf harmless'"
make_global_fence_mutation "${UNSAFE_GLOBAL_DOCKER_TLSCACERT_RESTART_MANUAL}" \
  'docker --tlscacert /tmp/ca.pem restart nginx'
make_global_fence_mutation "${UNSAFE_GLOBAL_DOCKER_COMPOSE_UP_MANUAL}" \
  'docker compose up -d nginx'
make_global_fence_mutation "${UNSAFE_GLOBAL_DOCKER_COMPOSE_DOWN_MANUAL}" \
  'docker compose down'
make_global_fence_mutation "${UNSAFE_GLOBAL_DOCKER_CREATE_MANUAL}" \
  'docker create diagnostic-image'
make_global_fence_mutation "${UNSAFE_GLOBAL_DOCKER_RUN_MANUAL}" \
  'docker run diagnostic-image'
make_global_fence_mutation "${UNSAFE_GLOBAL_DOCKER_PAUSE_MANUAL}" \
  'docker pause diagnostic-container'
make_global_fence_mutation "${UNSAFE_GLOBAL_DOCKER_UNPAUSE_MANUAL}" \
  'docker unpause diagnostic-container'
make_global_fence_mutation "${UNSAFE_GLOBAL_DOCKER_COMPOSE_SCALE_MANUAL}" \
  'docker compose scale nginx=2'
make_global_fence_mutation "${UNSAFE_GLOBAL_DOCKER_CONTAINER_UNKNOWN_OPTION_MANUAL}" \
  'docker container --future-option /tmp restart nginx'
make_global_fence_mutation "${UNSAFE_GLOBAL_PODMAN_CONTAINER_UNKNOWN_OPTION_MANUAL}" \
  'podman container --future-option /tmp kill nginx'
awk '/^## 11\./ { print "被动识别路径：\n`POST /api/internal/platform/xxl-job/sso-tickets`\n`POST /xxl-job-admin/platform-sso/login`" } { print }' \
  "${TROUBLESHOOTING_MANUAL}" >"${SAFE_PASSIVE_PATH_MANUAL}"
awk '/^## 2\./ { print "禁止执行 `redis-cli GET test-agent:ticket:diagnostic`、`service nginx restart`、`mysql --execute=\047UPDATE xxl_job_info SET trigger_status=1\047`；这些只是被动识别的禁令文本。" } { print }' \
  "${TROUBLESHOOTING_MANUAL}" >"${SAFE_PASSIVE_COMMAND_MENTIONS_MANUAL}"
make_global_fence_mutation "${SAFE_PREFIXED_READONLY_MANUAL}" \
  'cd /tmp && sudo env TRACE=1 mysql --host=diagnostic.invalid < /tmp/xxl-job-readonly.sql
printf '\''diagnostic\n'\'' | env TRACE=1 redis-cli PING
sudo -n env -i TRACE=1 redis-cli PING
docker ps
docker container ps
docker --tlscacert /tmp/ca.pem ps
docker --context prod ps
docker compose ps
docker compose -f stack.yml ps
docker-compose ps
docker-compose -f stack.yml ps
podman ps
podman container ps
podman --remote ps'
make_global_fence_mutation "${SAFE_QUOTED_OPERATORS_MANUAL}" \
  'grep -E '\''redis-cli GET|docker restart;mysql -e|safe && text|safe \|\| text'\'' /tmp/diagnostic.log
printf '\''%s\n'\'' '\''docker restart; redis-cli GET test-agent:ticket:x && mysql -eUPDATE || podman kill nginx'\''
grep -E "quoted \\| operator; docker restart && redis-cli GET \\|\\| mysql -e" /tmp/diagnostic.log
grep -E safe\\|docker\\ restart\\;redis-cli\\ GET /tmp/diagnostic.log
printf '\''%s\n'\'' '\''quoted multiline safe |
docker restart; redis-cli GET && mysql -eUPDATE || podman kill'\'''

# Markdown 只作为数据：夹具仅写入恶意 AWK 文本，验证过程不得执行该文本。
XXL_DIAG_EVIDENCE_AWK_MARKER="${EVIDENCE_AWK_INJECTION_MARKER}" awk '
  !changed && $0 == "if awk \047" {
    print
    print "  BEGIN { system(\"touch " ENVIRON["XXL_DIAG_EVIDENCE_AWK_MARKER"] "\") }"
    changed=1
    next
  }
  { print }
' "${TROUBLESHOOTING_MANUAL}" >"${UNSAFE_EVIDENCE_AWK_INJECTION_MANUAL}"

for unsafe_manual in \
  "${UNSAFE_HOST_MANUAL}" \
  "${UNSAFE_REDIS_MANUAL}" \
  "${UNSAFE_PASSWORD_MANUAL}" \
  "${UNSAFE_SECURE_MANUAL}" \
  "${UNSAFE_ACTIVE_REPLAY_MANUAL}" \
  "${UNSAFE_HTTP_CLIENT_MANUAL}" \
  "${UNSAFE_TASK_TRIGGER_MANUAL}" \
  "${UNSAFE_SQL_DML_MANUAL}" \
  "${UNSAFE_CONFIG_WRITE_MANUAL}" \
  "${UNSAFE_SERVICE_RELOAD_MANUAL}" \
  "${UNSAFE_FRAME_POLICY_MANUAL}" \
  "${UNSAFE_DOMAIN_MANUAL}" \
  "${UNSAFE_PORT_MANUAL}" \
  "${UNSAFE_DATABASE_MANUAL}" \
  "${UNSAFE_EXTRA_BACKEND_MANUAL}" \
  "${UNSAFE_MISSING_TASK_BOUNDARIES_MANUAL}" \
  "${UNSAFE_MISSING_EVIDENCE_BOUNDARIES_MANUAL}" \
  "${UNSAFE_MISSING_114_SUCCESS_MANUAL}" \
  "${UNSAFE_REOPEN_BROWSER_MANUAL}" \
  "${UNSAFE_NGINX_WORDING_MANUAL}" \
  "${UNSAFE_SSO_ORDER_MANUAL}" \
  "${UNSAFE_GLOBAL_REDIS_MANUAL}" \
  "${UNSAFE_GLOBAL_MYSQL_EXEC_MANUAL}" \
  "${UNSAFE_GLOBAL_MYSQL_E_MANUAL}" \
  "${UNSAFE_GLOBAL_SERVICE_MANUAL}" \
  "${UNSAFE_GLOBAL_HUP_MANUAL}" \
  "${UNSAFE_GLOBAL_SED_MANUAL}" \
  "${UNSAFE_GLOBAL_REDIRECT_MANUAL}" \
  "${UNSAFE_GLOBAL_TEE_NGINX_MANUAL}" \
  "${UNSAFE_GLOBAL_CP_MANUAL}" \
  "${UNSAFE_GLOBAL_REDIS_MULTILINE_MANUAL}" \
  "${UNSAFE_GLOBAL_MYSQL_COMPACT_E_MANUAL}" \
  "${UNSAFE_GLOBAL_SUDO_SERVICE_MANUAL}" \
  "${UNSAFE_GLOBAL_KILL_SIGNAL_MANUAL}" \
  "${UNSAFE_GLOBAL_RELATIVE_CONFIG_WRITE_MANUAL}" \
  "${UNSAFE_GLOBAL_CHAINED_REDIS_MANUAL}" \
  "${UNSAFE_GLOBAL_SUDO_REDIS_MANUAL}" \
  "${UNSAFE_GLOBAL_PIPE_ENV_REDIS_MANUAL}" \
  "${UNSAFE_GLOBAL_SEMICOLON_REDIS_MANUAL}" \
  "${UNSAFE_GLOBAL_OR_REDIS_MANUAL}" \
  "${UNSAFE_GLOBAL_SUDO_MYSQL_MANUAL}" \
  "${UNSAFE_GLOBAL_ENV_MYSQL_MANUAL}" \
  "${UNSAFE_GLOBAL_DOCKER_RESTART_MANUAL}" \
  "${UNSAFE_GLOBAL_DOCKER_COMPOSE_START_MANUAL}" \
  "${UNSAFE_GLOBAL_DOCKER_COMPOSE_STOP_MANUAL}" \
  "${UNSAFE_GLOBAL_PODMAN_RM_MANUAL}" \
  "${UNSAFE_GLOBAL_PODMAN_KILL_MANUAL}" \
  "${UNSAFE_GLOBAL_SUDO_USER_REDIS_MANUAL}" \
  "${UNSAFE_GLOBAL_SUDO_LONG_USER_REDIS_MANUAL}" \
  "${UNSAFE_GLOBAL_EMPTY_ASSIGNMENT_REDIS_MANUAL}" \
  "${UNSAFE_GLOBAL_ENV_IGNORE_REDIS_MANUAL}" \
  "${UNSAFE_GLOBAL_ENV_QUOTED_MYSQL_MANUAL}" \
  "${UNSAFE_GLOBAL_DOCKER_COMPOSE_FILE_STOP_MANUAL}" \
  "${UNSAFE_GLOBAL_DOCKER_COMPOSE_FILE_RESTART_MANUAL}" \
  "${UNSAFE_GLOBAL_DOCKER_CONTEXT_RESTART_MANUAL}" \
  "${UNSAFE_GLOBAL_PODMAN_REMOTE_KILL_MANUAL}" \
  "${UNSAFE_GLOBAL_ENV_SPLIT_REDIS_MANUAL}" \
  "${UNSAFE_GLOBAL_ENV_LONG_SPLIT_MYSQL_MANUAL}" \
  "${UNSAFE_GLOBAL_SUDO_CHDIR_REDIS_MANUAL}" \
  "${UNSAFE_GLOBAL_SUDO_SHORT_CHDIR_REDIS_MANUAL}" \
  "${UNSAFE_GLOBAL_SUDO_COMBINED_USER_REDIS_MANUAL}" \
  "${UNSAFE_GLOBAL_SUDO_COMBINED_CHDIR_REDIS_MANUAL}" \
  "${UNSAFE_GLOBAL_ENV_COMBINED_SPLIT_MANUAL}" \
  "${UNSAFE_GLOBAL_DOCKER_TLSCACERT_RESTART_MANUAL}" \
  "${UNSAFE_GLOBAL_DOCKER_COMPOSE_UP_MANUAL}" \
  "${UNSAFE_GLOBAL_DOCKER_COMPOSE_DOWN_MANUAL}" \
  "${UNSAFE_GLOBAL_DOCKER_CREATE_MANUAL}" \
  "${UNSAFE_GLOBAL_DOCKER_RUN_MANUAL}" \
  "${UNSAFE_GLOBAL_DOCKER_PAUSE_MANUAL}" \
  "${UNSAFE_GLOBAL_DOCKER_UNPAUSE_MANUAL}" \
  "${UNSAFE_GLOBAL_DOCKER_COMPOSE_SCALE_MANUAL}" \
  "${UNSAFE_GLOBAL_DOCKER_CONTAINER_UNKNOWN_OPTION_MANUAL}" \
  "${UNSAFE_GLOBAL_PODMAN_CONTAINER_UNKNOWN_OPTION_MANUAL}"; do
  if validate_strict_manual_contract "${unsafe_manual}" >/dev/null 2>&1; then
    printf '严格文档契约错误接受危险变异夹具: %s\n' "${unsafe_manual##*/}" >&2
    exit 1
  fi
done

if validate_strict_manual_contract "${UNSAFE_EVIDENCE_AWK_INJECTION_MANUAL}" >/dev/null 2>&1; then
  printf '严格文档契约错误接受证据 AWK 注入夹具\n' >&2
  exit 1
fi
if [[ -e "${EVIDENCE_AWK_INJECTION_MARKER}" ]]; then
  printf '验证器错误执行了 Markdown 中的证据 AWK 注入文本\n' >&2
  exit 1
fi

if ! validate_strict_manual_contract "${SAFE_PASSIVE_PATH_MANUAL}" >/dev/null 2>&1; then
  printf '严格文档契约错误拒绝合法被动路径夹具\n' >&2
  exit 1
fi
if ! validate_strict_manual_contract "${SAFE_PASSIVE_COMMAND_MENTIONS_MANUAL}" >/dev/null 2>&1; then
  printf '严格文档契约错误拒绝合法的行内禁令文本夹具\n' >&2
  exit 1
fi
if ! validate_strict_manual_contract "${SAFE_PREFIXED_READONLY_MANUAL}" >/dev/null 2>&1; then
  printf '严格文档契约错误拒绝合法的包装只读命令夹具\n' >&2
  exit 1
fi
if ! validate_strict_manual_contract "${SAFE_QUOTED_OPERATORS_MANUAL}" >/dev/null 2>&1; then
  printf '严格文档契约错误拒绝引号内运算符的合法只读夹具\n' >&2
  exit 1
fi

[[ -n "${EVIDENCE_AWK_PROGRAM}" ]] || {
  printf '验证器固定证据扫描器为空\n' >&2
  exit 1
}
EVIDENCE_SCAN_CLEAN_FIXTURE="${TMP_ROOT}/evidence-scan-clean.log"
EVIDENCE_SCAN_RAW_TOKEN_FIXTURE="${TMP_ROOT}/evidence-scan-raw-token.log"
EVIDENCE_SCAN_RAW_KEY_FIXTURE="${TMP_ROOT}/evidence-scan-raw-key.log"
EVIDENCE_SCAN_ABSOLUTE_QUERY_FIXTURE="${TMP_ROOT}/evidence-scan-absolute-query.log"
EVIDENCE_SCAN_RELATIVE_QUERY_FIXTURE="${TMP_ROOT}/evidence-scan-relative-query.log"
EVIDENCE_SCAN_ABSOLUTE_FRAGMENT_FIXTURE="${TMP_ROOT}/evidence-scan-absolute-fragment.log"
EVIDENCE_SCAN_RELATIVE_FRAGMENT_FIXTURE="${TMP_ROOT}/evidence-scan-relative-fragment.log"
EVIDENCE_SCAN_PATH_RELATIVE_QUERY_FIXTURE="${TMP_ROOT}/evidence-scan-path-relative-query.log"
EVIDENCE_SCAN_PATH_RELATIVE_FRAGMENT_FIXTURE="${TMP_ROOT}/evidence-scan-path-relative-fragment.log"
EVIDENCE_SCAN_QUERY_ONLY_FIXTURE="${TMP_ROOT}/evidence-scan-query-only.log"
EVIDENCE_SCAN_FRAGMENT_ONLY_FIXTURE="${TMP_ROOT}/evidence-scan-fragment-only.log"
EVIDENCE_SCAN_OUTPUT="${TMP_ROOT}/evidence-scan-output.log"
cat >"${EVIDENCE_SCAN_CLEAN_FIXTURE}" <<'EOF'
TEST_AGENT_XXL_JOB_ACCESS_TOKEN=SET length=32 sha256=0123456789abcdef
TEST_AGENT_DB_PASSWORD=SET
TEST_AGENT_API_TOKEN=UNSET
TEST_AGENT_INTERNAL_PROXY_API_KEY=SET
Authorization=[REDACTED]
https://diag.example/xxl-job-admin/?[REDACTED_QUERY]
/xxl-job-admin/?[REDACTED_QUERY]
https://diag.example/xxl-job-admin/#[REDACTED_FRAGMENT]
/xxl-job-admin/#[REDACTED_FRAGMENT]
EOF
cat >"${EVIDENCE_SCAN_RAW_TOKEN_FIXTURE}" <<'EOF'
token=diagnostic-raw-secret https://diag.example/xxl-job-admin/?[REDACTED_QUERY]
EOF
cat >"${EVIDENCE_SCAN_RAW_KEY_FIXTURE}" <<'EOF'
TEST_AGENT_INTERNAL_PROXY_API_KEY=diagnostic-raw-key
EOF
cat >"${EVIDENCE_SCAN_ABSOLUTE_QUERY_FIXTURE}" <<'EOF'
https://diag.example/xxl-job-admin/?opaque-query
EOF
cat >"${EVIDENCE_SCAN_RELATIVE_QUERY_FIXTURE}" <<'EOF'
/xxl-job-admin/?opaque-query
EOF
cat >"${EVIDENCE_SCAN_ABSOLUTE_FRAGMENT_FIXTURE}" <<'EOF'
https://diag.example/xxl-job-admin/#raw-fragment
EOF
cat >"${EVIDENCE_SCAN_RELATIVE_FRAGMENT_FIXTURE}" <<'EOF'
/xxl-job-admin/#raw-fragment
EOF
cat >"${EVIDENCE_SCAN_PATH_RELATIVE_QUERY_FIXTURE}" <<'EOF'
page?opaque-query
EOF
cat >"${EVIDENCE_SCAN_PATH_RELATIVE_FRAGMENT_FIXTURE}" <<'EOF'
page#raw-fragment
EOF
cat >"${EVIDENCE_SCAN_QUERY_ONLY_FIXTURE}" <<'EOF'
?opaque-query
EOF
cat >"${EVIDENCE_SCAN_FRAGMENT_ONLY_FIXTURE}" <<'EOF'
#raw-fragment
EOF
if ! awk "${EVIDENCE_AWK_PROGRAM}" "${EVIDENCE_SCAN_CLEAN_FIXTURE}" >"${EVIDENCE_SCAN_OUTPUT}"; then
  printf '正式手册证据扫描器错误拒绝安全摘要夹具\n' >&2
  exit 1
fi
[[ ! -s "${EVIDENCE_SCAN_OUTPUT}" ]] || {
  printf '正式手册证据扫描器不应输出安全夹具内容\n' >&2
  exit 1
}
for unsafe_evidence_fixture in \
  "${EVIDENCE_SCAN_RAW_TOKEN_FIXTURE}" \
  "${EVIDENCE_SCAN_RAW_KEY_FIXTURE}" \
  "${EVIDENCE_SCAN_ABSOLUTE_QUERY_FIXTURE}" \
  "${EVIDENCE_SCAN_RELATIVE_QUERY_FIXTURE}" \
  "${EVIDENCE_SCAN_ABSOLUTE_FRAGMENT_FIXTURE}" \
  "${EVIDENCE_SCAN_RELATIVE_FRAGMENT_FIXTURE}" \
  "${EVIDENCE_SCAN_PATH_RELATIVE_QUERY_FIXTURE}" \
  "${EVIDENCE_SCAN_PATH_RELATIVE_FRAGMENT_FIXTURE}" \
  "${EVIDENCE_SCAN_QUERY_ONLY_FIXTURE}" \
  "${EVIDENCE_SCAN_FRAGMENT_ONLY_FIXTURE}"; do
  : >"${EVIDENCE_SCAN_OUTPUT}"
  if awk "${EVIDENCE_AWK_PROGRAM}" "${unsafe_evidence_fixture}" >"${EVIDENCE_SCAN_OUTPUT}"; then
    printf '正式手册证据扫描器错误接受未脱敏夹具: %s\n' "${unsafe_evidence_fixture##*/}" >&2
    exit 1
  fi
  [[ ! -s "${EVIDENCE_SCAN_OUTPUT}" ]] || {
    printf '正式手册证据扫描器不应输出未脱敏夹具内容\n' >&2
    exit 1
  }
done

cat >"${FAKE_BIN}/getent" <<'EOF'
#!/usr/bin/env bash
[[ "${XXL_DIAG_FIXTURE_MODE:-healthy}" != "dns-fail" ]] || exit 2
printf '122.233.30.2 STREAM mimo.sdc.cs.icbc\n'
EOF

cat >"${FAKE_BIN}/curl" <<'EOF'
#!/usr/bin/env bash
if [[ -n "${XXL_DIAG_CURL_MARKER:-}" ]]; then
  printf 'called\n' >>"${XXL_DIAG_CURL_MARKER}"
fi
url="${*: -1}"
if [[ "${XXL_DIAG_FIXTURE_MODE:-healthy}" == "backend-admin-down" && "${url}" == 'http://127.0.0.1:18080/xxl-job-admin/actuator/health/readiness' ]]; then
  printf '{"status":"DOWN"}\n__TEST_AGENT_HTTP_STATUS__:503'
elif [[ "${XXL_DIAG_FIXTURE_MODE:-healthy}" == "admin-down" && "${url}" == *'/actuator/health/readiness' ]]; then
  printf '<html>Bad Gateway</html>\n__TEST_AGENT_HTTP_STATUS__:502'
else
  case "${url}" in
    */actuator/health/readiness) printf '{"status":"UP"}\n__TEST_AGENT_HTTP_STATUS__:200' ;;
    *) printf '<!doctype html><title>MIMO</title>\n__TEST_AGENT_HTTP_STATUS__:200' ;;
  esac
fi
EOF
cat >"${FAKE_BIN}/ip" <<'EOF'
#!/usr/bin/env bash
[[ "${XXL_DIAG_FIXTURE_MODE:-healthy}" != 'entry-ip-fail' ]] || exit 42
[[ "${XXL_DIAG_FIXTURE_MODE:-healthy}" != 'entry-ip-empty' ]] || exit 0
address="${XXL_DIAG_ENTRY_IP:-${XXL_DIAG_BACKEND_IP:-${XXL_DIAG_FRONTEND_IP:-122.233.30.2}}}"
printf '2: eth0    inet %s/24 brd 122.233.30.255 scope global eth0\n' "${address}"
EOF
cat >"${FAKE_BIN}/systemctl" <<'EOF'
#!/usr/bin/env bash
test "$1" = 'show'
test "$2" = 'test-agent-backend'
exec_start='/usr/bin/java -jar /data/testagent/dist/backend/test-agent-app.jar'
environment_files='/data/testagent/config/backend.env (ignore_errors=no)'
if [[ "${XXL_DIAG_FIXTURE_MODE:-healthy}" == 'backend-wrong-execstart' ]]; then
  exec_start='/usr/bin/java -jar /data/testagent/dist/backend/wrong-app.jar'
elif [[ "${XXL_DIAG_FIXTURE_MODE:-healthy}" == 'backend-execstart-jar-backup' ]]; then
  exec_start='/usr/bin/java -jar /data/testagent/dist/backend/test-agent-app.jar.bak'
elif [[ "${XXL_DIAG_FIXTURE_MODE:-healthy}" == 'backend-wrong-environment-files' ]]; then
  environment_files='/data/testagent/config/wrong-backend.env (ignore_errors=no)'
elif [[ "${XXL_DIAG_FIXTURE_MODE:-healthy}" == 'backend-environment-files-old' ]]; then
  environment_files='/data/testagent/config/backend.env.old (ignore_errors=no)'
fi
cat <<'OUTPUT'
LoadState=loaded
ActiveState=active
SubState=running
MainPID=4242
ActiveEnterTimestamp=Wed 2026-07-22 20:00:00 CST
OUTPUT
printf 'ExecStart={ path=/usr/bin/java ; argv[]=%s ; }\n' "${exec_start}"
printf 'EnvironmentFiles=%s\n' "${environment_files}"
EOF
cat >"${FAKE_BIN}/ss" <<'EOF'
#!/usr/bin/env bash
cat <<'OUTPUT'
State  Recv-Q Send-Q Local Address:Port Peer Address:Port Process
LISTEN 0      4096        0.0.0.0:8080      0.0.0.0:* users:(("java",pid=4242,fd=100))
LISTEN 0      4096        0.0.0.0:18080     0.0.0.0:* users:(("java",pid=4242,fd=101))
LISTEN 0      4096        0.0.0.0:9999      0.0.0.0:* users:(("java",pid=4242,fd=102))
LISTEN 0      4096        0.0.0.0:4096      0.0.0.0:* users:(("opencode",pid=5000,fd=10))
OUTPUT
EOF
cat >"${FAKE_BIN}/nc" <<'EOF'
#!/usr/bin/env bash
test "$1" = '-z'
test "$2" = '-w'
test "$3" = '3'
test -n "$4"
test -n "$5"
EOF
cat >"${FAKE_BIN}/ps" <<'EOF'
#!/usr/bin/env bash
test "$1" = '-eo'
test "$2" = 'pid=,args='
if [[ "${XXL_DIAG_FIXTURE_MODE:-healthy}" == 'backend-ps-error' ]]; then
  exit 42
fi
if [[ "${XXL_DIAG_FIXTURE_MODE:-healthy}" == 'backend-ps-jar-backup' ]]; then
  printf ' 4242 /usr/bin/java -jar /data/testagent/dist/backend/test-agent-app.jar.bak\n'
  exit 0
fi
printf ' 4242 /usr/bin/java -jar /data/testagent/dist/backend/test-agent-app.jar\n'
if [[ "${XXL_DIAG_FIXTURE_MODE:-healthy}" == 'backend-extra-java' ]]; then
  printf ' 5252 /usr/bin/java -jar /data/testagent/dist/backend/another-java-app.jar\n'
fi
EOF
cat >"${FAKE_BIN}/journalctl" <<'EOF'
#!/usr/bin/env bash
if [[ "${XXL_DIAG_FIXTURE_MODE:-healthy}" == 'backend-log-no-match' ]]; then
  printf '%s\n' '2026-07-22T21:00:00+0800 backend ordinary lifecycle line'
  exit 0
fi
printf '%s\n' \
  '2026-07-22T21:00:00+0800 backend XxlJob ExecutorRegistryThread started token=raw-log-token-value digest=raw-log-digest-value' \
  '2026-07-22T21:00:01+0800 backend Hikari MySQL jdbc:mysql://raw-jdbc-user:raw-jdbc-password@122.210.106.43:3306/xxl_job?password=raw-jdbc-query-value' \
  '2026-07-22T21:00:02+0800 backend XxlJob GET https://diag.example/xxl-job-admin/#raw-absolute-fragment-value' \
  '2026-07-22T21:00:03+0800 backend XxlJob GET https://diag.example/xxl-job-admin/?opaque=raw-absolute-query-value' \
  '2026-07-22T21:00:03+0800 backend XxlJob GET /xxl-job-admin/?opaque=raw-relative-query-value' \
  '2026-07-22T21:00:04+0800 backend XxlJob GET /xxl-job-admin/#raw-relative-fragment-value' \
  '2026-07-22T21:00:05+0800 backend XxlJob Authorization: Bearer raw-bearer-token-value' \
  '2026-07-22T21:00:05+0800 backend XxlJob Authorization: Bearer "raw-sensitive-bearer-double"' \
  "2026-07-22T21:00:05+0800 backend XxlJob Authorization: Bearer 'raw-sensitive-bearer-single'" \
  '2026-07-22T21:00:06+0800 backend XxlJob payload={"token":"raw-quoted-token-value"}' \
  "2026-07-22T21:00:07+0800 backend XxlJob ticket=raw-sensitive-ticket-bare cookie='raw-sensitive-cookie-single' token=\"raw-sensitive-token-double\" password=raw-sensitive-password-bare secret='raw-sensitive-secret-single' authorization=\"raw-sensitive-authorization-double\" digest='raw-sensitive-digest-single'" \
  "2026-07-22T21:00:08+0800 backend XxlJob accessToken=raw-sensitive-access-camel access_token='raw-sensitive-access-snake' apiToken=\"raw-sensitive-api-token-camel\" api_token=raw-sensitive-api-token-snake apiKey='raw-sensitive-api-key-camel' api_key=\"raw-sensitive-api-key-snake\" platform_session_digest='raw-sensitive-platform-session-digest'" \
  '2026-07-22T21:00:02+0800 backend unrelated raw-unrelated-value'
EOF
chmod +x "${FAKE_BIN}/getent" "${FAKE_BIN}/curl" "${FAKE_BIN}/ip" \
  "${FAKE_BIN}/systemctl" "${FAKE_BIN}/ss" "${FAKE_BIN}/nc" "${FAKE_BIN}/ps" "${FAKE_BIN}/journalctl"

BROKEN_SHA_BIN="${TMP_ROOT}/broken-sha-bin"
FILTER_FAILURE_BIN="${TMP_ROOT}/filter-failure-bin"
REDACTION_FAILURE_BIN="${TMP_ROOT}/redaction-failure-bin"
mkdir -p "${BROKEN_SHA_BIN}" "${FILTER_FAILURE_BIN}" "${REDACTION_FAILURE_BIN}"
cat >"${BROKEN_SHA_BIN}/sha256sum" <<'EOF'
#!/usr/bin/env bash
exit 127
EOF
cat >"${BROKEN_SHA_BIN}/shasum" <<'EOF'
#!/usr/bin/env bash
exit 127
EOF
cat >"${FILTER_FAILURE_BIN}/grep" <<'EOF'
#!/usr/bin/env bash
if [[ "${XXL_DIAG_FIXTURE_MODE:-healthy}" == 'backend-filter-failure' && "$*" == *'xxl-job|XxlJob|Flyway'* ]]; then
  exit 2
fi
exec /usr/bin/grep "$@"
EOF
cat >"${REDACTION_FAILURE_BIN}/sed" <<'EOF'
#!/usr/bin/env bash
input="$(cat)"
if [[ "${XXL_DIAG_FIXTURE_MODE:-healthy}" == 'backend-redaction-failure' && "${input}" == *'XxlJob'* ]]; then
  exit 2
fi
printf '%s' "${input}" | /usr/bin/sed "$@"
EOF
chmod +x "${BROKEN_SHA_BIN}/sha256sum" "${BROKEN_SHA_BIN}/shasum" \
  "${FILTER_FAILURE_BIN}/grep" "${REDACTION_FAILURE_BIN}/sed"

ENTRY_SCRIPT="${ROOT_DIR}/deploy/internal/diagnose-xxl-job-entry.sh"
ENTRY_NO_IP_BIN="${TMP_ROOT}/entry-no-ip-bin"
mkdir -p "${ENTRY_NO_IP_BIN}"
ln -s "${FAKE_BIN}/getent" "${ENTRY_NO_IP_BIN}/getent"
ln -s "${FAKE_BIN}/curl" "${ENTRY_NO_IP_BIN}/curl"
ln -s "$(command -v awk)" "${ENTRY_NO_IP_BIN}/awk"
ln -s "$(command -v grep)" "${ENTRY_NO_IP_BIN}/grep"
set +e
PATH="${FAKE_BIN}:${PATH}" XXL_DIAG_FIXTURE_MODE=healthy bash "${ENTRY_SCRIPT}" --unexpected-argument >"${TMP_ROOT}/entry-argument.log" 2>&1
status=$?
set -e
test "${status}" -eq 2
grep -Fq '[FAIL] 不接受命令行参数' "${TMP_ROOT}/entry-argument.log"

PATH="${FAKE_BIN}:${PATH}" XXL_DIAG_FIXTURE_MODE=healthy XXL_DIAG_ENTRY_IP=122.233.31.9 \
  bash "${ENTRY_SCRIPT}" >"${TMP_ROOT}/entry-ok.log"
grep -Fq '[PASS] 域名解析' "${TMP_ROOT}/entry-ok.log"
grep -Fq '[WARN] 当前入口使用 HTTP' "${TMP_ROOT}/entry-ok.log"
grep -Fxq '[PASS] 诊断完成：未发现关键异常' "${TMP_ROOT}/entry-ok.log" || {
  printf '入口诊断正常结束缺少统一 PASS 摘要\n' >&2
  exit 1
}

set +e
PATH="${FAKE_BIN}:${PATH}" XXL_DIAG_FIXTURE_MODE=admin-down XXL_DIAG_ENTRY_IP=122.233.31.9 \
  bash "${ENTRY_SCRIPT}" >"${TMP_ROOT}/entry-down.log" 2>&1
status=$?
set -e
test "${status}" -eq 1
grep -Fq '[FAIL]' "${TMP_ROOT}/entry-down.log"
grep -Eq '^\[FAIL\] 诊断完成：发现 [0-9]+ 个关键异常$' "${TMP_ROOT}/entry-down.log" || {
  printf '入口诊断失败结束缺少统一 FAIL 摘要\n' >&2
  exit 1
}

set +e
PATH="${FAKE_BIN}:${PATH}" XXL_DIAG_FIXTURE_MODE=dns-fail XXL_DIAG_ENTRY_IP=122.233.31.9 \
  bash "${ENTRY_SCRIPT}" >"${TMP_ROOT}/entry-dns.log" 2>&1
status=$?
set -e
test "${status}" -eq 1

for wrong_entry_host in 122.233.30.2 122.233.30.4 122.233.30.114 122.233.30.20 122.210.106.43; do
  entry_curl_marker="${TMP_ROOT}/entry-curl-${wrong_entry_host}.marker"
  set +e
  PATH="${FAKE_BIN}:${PATH}" XXL_DIAG_FIXTURE_MODE=healthy XXL_DIAG_ENTRY_IP="${wrong_entry_host}" \
    XXL_DIAG_CURL_MARKER="${entry_curl_marker}" bash "${ENTRY_SCRIPT}" \
    >"${TMP_ROOT}/entry-wrong-host-${wrong_entry_host}.log" 2>&1
  status=$?
  set -e
  if [[ "${status}" -ne 2 ]]; then
    printf '入口脚本错误机器 %s 应返回 2，实际返回 %s\n' "${wrong_entry_host}" "${status}" >&2
    exit 1
  fi
  grep -Fq '[FAIL] 当前机器是已知基础设施节点' "${TMP_ROOT}/entry-wrong-host-${wrong_entry_host}.log" || {
    printf '入口脚本错误机器 %s 缺少明确提示\n' "${wrong_entry_host}" >&2
    exit 1
  }
  test ! -e "${entry_curl_marker}"
done

for entry_ip_mode in entry-ip-fail entry-ip-empty; do
  set +e
  PATH="${FAKE_BIN}:${PATH}" XXL_DIAG_FIXTURE_MODE="${entry_ip_mode}" \
    bash "${ENTRY_SCRIPT}" >"${TMP_ROOT}/${entry_ip_mode}.log" 2>&1
  status=$?
  set -e
  if [[ "${status}" -ne 2 ]]; then
    printf '入口脚本地址检测模式 %s 应返回 2，实际返回 %s\n' "${entry_ip_mode}" "${status}" >&2
    exit 1
  fi
  grep -Fq '[FAIL] 无法可靠识别本机全局 IPv4' "${TMP_ROOT}/${entry_ip_mode}.log" || {
    printf '入口脚本地址检测模式 %s 缺少关键前提提示\n' "${entry_ip_mode}" >&2
    exit 1
  }
done

set +e
PATH="${ENTRY_NO_IP_BIN}" /bin/bash "${ENTRY_SCRIPT}" >"${TMP_ROOT}/entry-ip-missing.log" 2>&1
status=$?
set -e
if [[ "${status}" -ne 2 ]]; then
  printf '入口脚本缺少 ip 工具时应返回 2，实际返回 %s\n' "${status}" >&2
  exit 1
fi
grep -Fq '[FAIL] 缺少 ip，无法可靠识别本机全局 IPv4' "${TMP_ROOT}/entry-ip-missing.log" || {
  printf '入口脚本缺少 ip 工具时未输出关键前提提示\n' >&2
  exit 1
}

FRONTEND_FIXTURE="${TMP_ROOT}/frontend-fixture"
mkdir -p "${FRONTEND_FIXTURE}"
cat >"${FRONTEND_FIXTURE}/nginx.env" <<'EOF'
TEST_AGENT_NGINX_MODE=multi
TEST_AGENT_NGINX_XXL_JOB_ADMINS=122.233.30.4:18080,122.233.30.114:18080
TEST_AGENT_NGINX_LISTEN_PORT=80
TEST_AGENT_NGINX_ADDITIONAL_LISTEN_PORTS=9996
EOF
cat >"${FRONTEND_FIXTURE}/nginx.conf" <<'EOF'
upstream test_agent_xxl_job_admin {
    server 122.233.30.4:18080 max_fails=3 fail_timeout=10s;
    server 122.233.30.114:18080 max_fails=3 fail_timeout=10s;
}
location /xxl-job-admin/ {
    proxy_pass http://test_agent_xxl_job_admin;
}
EOF
cat >"${FRONTEND_FIXTURE}/nginx-comments-only.conf" <<'EOF'
# upstream test_agent_xxl_job_admin {
#     server 122.233.30.4:18080 max_fails=3 fail_timeout=10s;
#     server 122.233.30.114:18080 max_fails=3 fail_timeout=10s;
# }
# location /xxl-job-admin/ {
#     proxy_pass http://test_agent_xxl_job_admin;
# }
EOF
cat >"${FRONTEND_FIXTURE}/nginx-one-commented.conf" <<'EOF'
upstream test_agent_xxl_job_admin {
    server 122.233.30.4:18080 max_fails=3 fail_timeout=10s;
    # server 122.233.30.114:18080 max_fails=3 fail_timeout=10s;
}
location /xxl-job-admin/ {
    proxy_pass http://test_agent_xxl_job_admin;
}
EOF
cat >"${FRONTEND_FIXTURE}/nginx" <<'EOF'
#!/usr/bin/env bash
test "$1" = '-p'
test "$3" = '-c'
test "$5" = '-T'
cat "${XXL_DIAG_FRONTEND_NGINX_CONFIG}"
EOF
cat >"${FRONTEND_FIXTURE}/access.log" <<'EOF'
122.233.30.9 - - [22/Jul/2026:21:00:00 +0800] "GET /xxl-job-admin/?ticket=raw-ticket-value&token=raw-token-value HTTP/1.1" 502 0 "-" "curl"
122.233.30.9 - - [22/Jul/2026:21:00:01 +0800] "GET https://diag.example/xxl-job-admin/#raw-fragment-value HTTP/1.1" 502 0 "-" "curl"
122.233.30.9 - - [22/Jul/2026:21:00:02 +0800] "GET /xxl-job-admin/?opaque=raw-relative-query-value HTTP/1.1" 502 0 "-" "curl"
122.233.30.9 - - [22/Jul/2026:21:00:03 +0800] "GET /xxl-job-admin/#raw-relative-fragment-value HTTP/1.1" 502 0 "-" "curl"
EOF
cat >"${FRONTEND_FIXTURE}/error.log" <<'EOF'
2026/07/22 21:00:01 [error] 1#1: *1 connect() failed (111: Connection refused) while connecting to upstream, request: "GET /xxl-job-admin/ HTTP/1.1", cookie=raw-cookie-value authorization=raw-authorization-value password=raw-password-value
EOF
chmod +x "${FRONTEND_FIXTURE}/nginx"

FRONTEND_SCRIPT="${ROOT_DIR}/deploy/internal/diagnose-xxl-job-frontend.sh"
frontend_run() {
  PATH="${FAKE_BIN}:${PATH}" \
    XXL_DIAG_FRONTEND_IP="${XXL_DIAG_FRONTEND_IP:-122.233.30.2}" \
    XXL_DIAG_FRONTEND_NGINX_CONFIG="${XXL_DIAG_FRONTEND_NGINX_CONFIG:-${FRONTEND_FIXTURE}/nginx.conf}" \
    TEST_AGENT_DIAG_NGINX_BIN="${FRONTEND_FIXTURE}/nginx" \
    TEST_AGENT_DIAG_NGINX_ENV="${FRONTEND_FIXTURE}/nginx.env" \
    TEST_AGENT_DIAG_NGINX_ACCESS_LOG="${FRONTEND_FIXTURE}/access.log" \
    TEST_AGENT_DIAG_NGINX_ERROR_LOG="${FRONTEND_FIXTURE}/error.log" \
    bash "${FRONTEND_SCRIPT}"
}

frontend_run >"${TMP_ROOT}/frontend-ok.log"
grep -Fq '[PASS] Nginx effective configuration contains XXL Admin upstream' "${TMP_ROOT}/frontend-ok.log"
grep -Fq '[PASS] 122.233.30.4:18080 readiness is UP' "${TMP_ROOT}/frontend-ok.log"
grep -Fq '[PASS] 122.233.30.114:18080 readiness is UP' "${TMP_ROOT}/frontend-ok.log"
grep -Fxq '[PASS] 诊断完成：未发现关键异常' "${TMP_ROOT}/frontend-ok.log" || {
  printf '前端诊断正常结束缺少统一 PASS 摘要\n' >&2
  exit 1
}
grep -Fq 'https://diag.example/xxl-job-admin/#[REDACTED_FRAGMENT]' "${TMP_ROOT}/frontend-ok.log"
grep -Fq '/xxl-job-admin/?[REDACTED_QUERY]' "${TMP_ROOT}/frontend-ok.log"
grep -Fq '/xxl-job-admin/#[REDACTED_FRAGMENT]' "${TMP_ROOT}/frontend-ok.log"
if grep -Eq 'raw-(ticket|token|cookie|authorization|password|fragment|relative-query|relative-fragment)-value' "${TMP_ROOT}/frontend-ok.log"; then
  printf 'frontend diagnostics leaked sensitive fixture values\n' >&2
  exit 1
fi

for commented_nginx_case in comments-only one-commented; do
  set +e
  XXL_DIAG_FRONTEND_NGINX_CONFIG="${FRONTEND_FIXTURE}/nginx-${commented_nginx_case}.conf" \
    frontend_run >"${TMP_ROOT}/frontend-${commented_nginx_case}.log" 2>&1
  status=$?
  set -e
  if [[ "${status}" -ne 1 ]]; then
    printf '前端诊断错误接受 Nginx 注释指令夹具 %s，退出码为 %s\n' \
      "${commented_nginx_case}" "${status}" >&2
    exit 1
  fi
  grep -Eq '^\[FAIL\] 诊断完成：发现 [0-9]+ 个关键异常$' \
    "${TMP_ROOT}/frontend-${commented_nginx_case}.log" || {
    printf '前端诊断失败结束缺少统一 FAIL 摘要: %s\n' "${commented_nginx_case}" >&2
    exit 1
  }
done

sed 's/122\.233\.30\.114:18080 max_fails/122.233.30.115:18080 max_fails/' \
  "${FRONTEND_FIXTURE}/nginx.conf" >"${FRONTEND_FIXTURE}/nginx.conf.tmp"
mv "${FRONTEND_FIXTURE}/nginx.conf.tmp" "${FRONTEND_FIXTURE}/nginx.conf"
set +e
frontend_run >"${TMP_ROOT}/frontend-missing-upstream.log" 2>&1
status=$?
set -e
test "${status}" -eq 1
grep -Fq '[FAIL] Nginx effective configuration missing XXL Admin server 122.233.30.114:18080' "${TMP_ROOT}/frontend-missing-upstream.log"

sed 's/122\.233\.30\.115:18080 max_fails/122.233.30.114:18080 max_fails/' \
  "${FRONTEND_FIXTURE}/nginx.conf" >"${FRONTEND_FIXTURE}/nginx.conf.tmp"
mv "${FRONTEND_FIXTURE}/nginx.conf.tmp" "${FRONTEND_FIXTURE}/nginx.conf"
set +e
XXL_DIAG_FRONTEND_IP=122.233.30.3 frontend_run >"${TMP_ROOT}/frontend-wrong-host.log" 2>&1
status=$?
set -e
test "${status}" -eq 2
grep -Fq '[FAIL] 当前机器不是 122.233.30.2' "${TMP_ROOT}/frontend-wrong-host.log"

BACKEND_SCRIPT="${ROOT_DIR}/deploy/internal/diagnose-xxl-job-backend.sh"
if [[ ! -f "${BACKEND_SCRIPT}" ]]; then
  printf 'missing backend diagnostics script: %s\n' "${BACKEND_SCRIPT}" >&2
  exit 1
fi

BACKEND_FIXTURE="${TMP_ROOT}/backend-fixture"
BACKEND_DATA_ROOT="${BACKEND_FIXTURE}/data"
mkdir -p "${BACKEND_DATA_ROOT}"
printf '122.233.30.4\n' >"${BACKEND_DATA_ROOT}/.serverhost"
printf 'test-agent-backend-122-233-30-4\n' >"${BACKEND_DATA_ROOT}/.serverid"
MALICIOUS_MARKER="${BACKEND_FIXTURE}/dotenv-executed"
cat >"${BACKEND_FIXTURE}/backend.env" <<'EOF'
SPRING_PROFILES_ACTIVE=prod
TEST_AGENT_DEPLOYMENT_MODE=internal
TEST_AGENT_SERVER_ADVERTISED_HOST=122.233.30.4
TEST_AGENT_LINUX_SERVER_ID=test-agent-backend-122-233-30-4
TEST_AGENT_DB_URL=jdbc:postgresql://raw-db-url-user:raw-db-url-password@122.233.30.147:5432/postgres?password=raw-db-query-value
TEST_AGENT_DB_USERNAME=postgres
TEST_AGENT_DB_PASSWORD=raw-db-password-value
TEST_AGENT_REDIS_HOST=122.233.30.20
TEST_AGENT_REDIS_PORT=6379
TEST_AGENT_REDIS_PASSWORD=raw-redis-password-value
TEST_AGENT_XXL_JOB_ENABLED=true
TEST_AGENT_XXL_JOB_MYSQL_URL=jdbc:mysql://raw-mysql-url-user:raw-mysql-url-password@122.210.106.43:3306/xxl_job?password=raw-mysql-query-value
TEST_AGENT_XXL_JOB_MYSQL_USERNAME=xxl_job
TEST_AGENT_XXL_JOB_MYSQL_PASSWORD=raw-xxl-password-value
TEST_AGENT_XXL_JOB_ACCESS_TOKEN=raw-xxl-access-token-value
TEST_AGENT_XXL_JOB_ADMIN_PORT=18080
TEST_AGENT_XXL_JOB_EXECUTOR_PORT=9999
TEST_AGENT_XXL_JOB_COOKIE_SECURE=false
TEST_AGENT_OPENCODE_MANAGER_TOKEN=raw-manager-token-value
TEST_AGENT_INTERNAL_PROXY_API_KEY=raw-proxy-api-key-value
SYS_DATA_ROOT_DIR=/must/not/be/used/by/fixture
EOF
printf 'TEST_AGENT_API_TOKEN=$(touch %s)\n' "${MALICIOUS_MARKER}" >>"${BACKEND_FIXTURE}/backend.env"
cp "${BACKEND_FIXTURE}/backend.env" "${BACKEND_FIXTURE}/backend-mismatch.env"
sed 's/TEST_AGENT_SERVER_ADVERTISED_HOST=122\.233\.30\.4/TEST_AGENT_SERVER_ADVERTISED_HOST=122.233.30.114/' \
  "${BACKEND_FIXTURE}/backend-mismatch.env" >"${BACKEND_FIXTURE}/backend-mismatch.env.tmp"
mv "${BACKEND_FIXTURE}/backend-mismatch.env.tmp" "${BACKEND_FIXTURE}/backend-mismatch.env"

make_backend_env_variant() {
  local target="$1" expression="$2"
  sed "${expression}" "${BACKEND_FIXTURE}/backend.env" >"${target}"
}
make_backend_env_variant "${BACKEND_FIXTURE}/backend-wrong-redis-host.env" \
  's/TEST_AGENT_REDIS_HOST=122\.233\.30\.20/TEST_AGENT_REDIS_HOST=122.233.30.21/'
make_backend_env_variant "${BACKEND_FIXTURE}/backend-wrong-redis-port.env" \
  's/TEST_AGENT_REDIS_PORT=6379/TEST_AGENT_REDIS_PORT=6380/'
make_backend_env_variant "${BACKEND_FIXTURE}/backend-wrong-mysql-host.env" \
  's/122\.210\.106\.43:3306\/xxl_job/122.210.106.44:3306\/xxl_job/'
make_backend_env_variant "${BACKEND_FIXTURE}/backend-wrong-mysql-port.env" \
  's/122\.210\.106\.43:3306\/xxl_job/122.210.106.43:3307\/xxl_job/'

backend_run() {
  PATH="${TEST_AGENT_DIAG_EXTRA_PATH:-}${FAKE_BIN}:${PATH}" \
    XXL_DIAG_FIXTURE_MODE="${XXL_DIAG_FIXTURE_MODE:-healthy}" \
    XXL_DIAG_BACKEND_IP="${XXL_DIAG_BACKEND_IP:-122.233.30.4}" \
    TEST_AGENT_DIAG_BACKEND_ENV="${TEST_AGENT_DIAG_BACKEND_ENV:-${BACKEND_FIXTURE}/backend.env}" \
    TEST_AGENT_DIAG_DATA_ROOT="${BACKEND_DATA_ROOT}" \
    TEST_AGENT_DIAG_PS_BIN="${TEST_AGENT_DIAG_PS_BIN:-ps}" \
    bash "${BACKEND_SCRIPT}" "$@"
}

backend_run --expected-host 122.233.30.4 --minutes 15 >"${TMP_ROOT}/backend-ok.log" 2>&1
grep -Fq '[PASS] 当前机器包含 expected host 122.233.30.4' "${TMP_ROOT}/backend-ok.log"
grep -Fq '[PASS] systemd test-agent-backend is active/running with MainPID=4242' "${TMP_ROOT}/backend-ok.log"
grep -Fq '[PASS] 127.0.0.1:8080 readiness is UP' "${TMP_ROOT}/backend-ok.log"
grep -Fq '[PASS] 127.0.0.1:18080 XXL Admin readiness is UP' "${TMP_ROOT}/backend-ok.log"
grep -Fxq '[PASS] 诊断完成：未发现关键异常' "${TMP_ROOT}/backend-ok.log" || {
  printf '后台诊断正常结束缺少统一 PASS 摘要\n' >&2
  exit 1
}
grep -Fq '[INFO] REDIS_ENDPOINT=122.233.30.20:6379' "${TMP_ROOT}/backend-ok.log"
grep -Fq '[INFO] XXL_MYSQL_ENDPOINT=jdbc:mysql://122.210.106.43:3306/xxl_job' "${TMP_ROOT}/backend-ok.log"
grep -Fq '[INFO] ADMIN_PORT=18080' "${TMP_ROOT}/backend-ok.log"
grep -Fq '[INFO] EXECUTOR_PORT=9999' "${TMP_ROOT}/backend-ok.log"
grep -Fq '[INFO] COOKIE_SECURE=false' "${TMP_ROOT}/backend-ok.log"
grep -Eq '\[INFO\] TEST_AGENT_XXL_JOB_ACCESS_TOKEN=SET length=[0-9]+ sha256=[0-9a-f]{16}$' "${TMP_ROOT}/backend-ok.log"
for status_only_secret in \
  TEST_AGENT_DB_PASSWORD \
  TEST_AGENT_REDIS_PASSWORD \
  TEST_AGENT_XXL_JOB_MYSQL_PASSWORD \
  TEST_AGENT_API_TOKEN \
  TEST_AGENT_OPENCODE_MANAGER_TOKEN \
  TEST_AGENT_INTERNAL_PROXY_API_KEY; do
  grep -Fxq "[INFO] ${status_only_secret}=SET" "${TMP_ROOT}/backend-ok.log" || {
    printf '后台诊断未将低熵凭据 %s 限制为 SET/UNSET\n' "${status_only_secret}" >&2
    exit 1
  }
  if grep -Eq "^\[INFO\] ${status_only_secret}=SET[[:space:]].*(length|sha256)=" "${TMP_ROOT}/backend-ok.log"; then
    printf '后台诊断泄露低熵凭据 %s 的长度或无盐摘要\n' "${status_only_secret}" >&2
    exit 1
  fi
done
grep -Fq 'https://diag.example/xxl-job-admin/#[REDACTED_FRAGMENT]' "${TMP_ROOT}/backend-ok.log"
grep -Fq 'https://diag.example/xxl-job-admin/?[REDACTED_QUERY]' "${TMP_ROOT}/backend-ok.log"
grep -Fq '/xxl-job-admin/?[REDACTED_QUERY]' "${TMP_ROOT}/backend-ok.log"
grep -Fq '/xxl-job-admin/#[REDACTED_FRAGMENT]' "${TMP_ROOT}/backend-ok.log"
grep -Fq 'Authorization=[REDACTED]' "${TMP_ROOT}/backend-ok.log"
test "$(grep -Fc 'Authorization=[REDACTED]' "${TMP_ROOT}/backend-ok.log")" -eq 3
grep -Fq 'payload={"token":"[REDACTED]"}' "${TMP_ROOT}/backend-ok.log"
grep -Fq 'ticket=[REDACTED]' "${TMP_ROOT}/backend-ok.log"
grep -Fq "cookie='[REDACTED]'" "${TMP_ROOT}/backend-ok.log"
grep -Fq 'token="[REDACTED]"' "${TMP_ROOT}/backend-ok.log"
grep -Fq 'password=[REDACTED]' "${TMP_ROOT}/backend-ok.log"
grep -Fq "secret='[REDACTED]'" "${TMP_ROOT}/backend-ok.log"
grep -Fq 'authorization="[REDACTED]"' "${TMP_ROOT}/backend-ok.log"
grep -Fq "digest='[REDACTED]'" "${TMP_ROOT}/backend-ok.log"
grep -Fq 'accessToken=[REDACTED]' "${TMP_ROOT}/backend-ok.log"
grep -Fq "access_token='[REDACTED]'" "${TMP_ROOT}/backend-ok.log"
grep -Fq 'apiToken="[REDACTED]"' "${TMP_ROOT}/backend-ok.log"
grep -Fq 'api_token=[REDACTED]' "${TMP_ROOT}/backend-ok.log"
grep -Fq "apiKey='[REDACTED]'" "${TMP_ROOT}/backend-ok.log"
grep -Fq 'api_key="[REDACTED]"' "${TMP_ROOT}/backend-ok.log"
grep -Fq "platform_session_digest='[REDACTED]'" "${TMP_ROOT}/backend-ok.log"
grep -Fq '[PASS] systemd ExecStart 指向固定后台 JAR' "${TMP_ROOT}/backend-ok.log"
grep -Fq '[PASS] systemd EnvironmentFiles 包含固定 backend.env' "${TMP_ROOT}/backend-ok.log"
grep -Fq '[PASS] 专用 Linux 仅有一个后台 Java，PID 与 MainPID=4242 一致' "${TMP_ROOT}/backend-ok.log"
grep -Fq '[INFO] 14096-16095 为当前双后台 opencode 用户进程端口池，非管理页首要链路；本脚本不执行 Docker/worker/manager 操作' "${TMP_ROOT}/backend-ok.log"
if grep -Fq 'sed:' "${TMP_ROOT}/backend-ok.log"; then
  printf 'backend diagnostics redaction failed\n' >&2
  exit 1
fi
test ! -e "${MALICIOUS_MARKER}"
if grep -Eq 'raw-(db|redis|xxl|manager|proxy|api|log|jdbc|mysql|unrelated|absolute|relative|bearer|quoted|sensitive)' "${TMP_ROOT}/backend-ok.log"; then
  printf 'backend diagnostics leaked sensitive fixture values\n' >&2
  exit 1
fi

set +e
XXL_DIAG_FIXTURE_MODE=backend-admin-down backend_run --expected-host 122.233.30.4 >"${TMP_ROOT}/backend-admin-down.log" 2>&1
status=$?
set -e
test "${status}" -eq 1
grep -Fq '[FAIL] 127.0.0.1:18080 XXL Admin readiness 返回 HTTP 503 或非 UP 响应' "${TMP_ROOT}/backend-admin-down.log"
grep -Eq '^\[FAIL\] 诊断完成：发现 [0-9]+ 个关键异常$' "${TMP_ROOT}/backend-admin-down.log" || {
  printf '后台诊断失败结束缺少统一 FAIL 摘要\n' >&2
  exit 1
}

set +e
TEST_AGENT_DIAG_BACKEND_ENV="${BACKEND_FIXTURE}/backend-mismatch.env" backend_run --expected-host 122.233.30.4 >"${TMP_ROOT}/backend-config-mismatch.log" 2>&1
status=$?
set -e
test "${status}" -eq 1
grep -Fq '[FAIL] advertised host 与 expected host 不一致' "${TMP_ROOT}/backend-config-mismatch.log"

for topology_case in wrong-redis-host wrong-redis-port wrong-mysql-host wrong-mysql-port; do
  set +e
  TEST_AGENT_DIAG_BACKEND_ENV="${BACKEND_FIXTURE}/backend-${topology_case}.env" \
    backend_run --expected-host 122.233.30.4 >"${TMP_ROOT}/backend-${topology_case}.log" 2>&1
  status=$?
  set -e
  test "${status}" -eq 1
  grep -Fq '[FAIL] 固定共享拓扑不一致' "${TMP_ROOT}/backend-${topology_case}.log"
done

for systemd_case in backend-extra-java backend-wrong-execstart backend-execstart-jar-backup backend-wrong-environment-files backend-environment-files-old backend-ps-jar-backup; do
  set +e
  XXL_DIAG_FIXTURE_MODE="${systemd_case}" backend_run --expected-host 122.233.30.4 >"${TMP_ROOT}/${systemd_case}.log" 2>&1
  status=$?
  set -e
  test "${status}" -eq 1
  case "${systemd_case}" in
    backend-extra-java) grep -Fq '[FAIL] 专用 Linux 上的 Java 进程数量不是 1' "${TMP_ROOT}/${systemd_case}.log" ;;
    backend-wrong-execstart) grep -Fq '[FAIL] systemd ExecStart 未指向 /data/testagent/dist/backend/test-agent-app.jar' "${TMP_ROOT}/${systemd_case}.log" ;;
    backend-execstart-jar-backup) grep -Fq '[FAIL] systemd ExecStart 未指向 /data/testagent/dist/backend/test-agent-app.jar' "${TMP_ROOT}/${systemd_case}.log" ;;
    backend-wrong-environment-files) grep -Fq '[FAIL] systemd EnvironmentFiles 未包含 /data/testagent/config/backend.env' "${TMP_ROOT}/${systemd_case}.log" ;;
    backend-environment-files-old) grep -Fq '[FAIL] systemd EnvironmentFiles 未包含 /data/testagent/config/backend.env' "${TMP_ROOT}/${systemd_case}.log" ;;
    backend-ps-jar-backup) grep -Fq '[FAIL] 专用 Linux 上的 Java 进程数量不是 1' "${TMP_ROOT}/${systemd_case}.log" ;;
  esac
done

set +e
XXL_DIAG_FIXTURE_MODE=backend-ps-error backend_run --expected-host 122.233.30.4 >"${TMP_ROOT}/backend-ps-error.log" 2>&1
status=$?
set -e
test "${status}" -eq 2
grep -Fq '[FAIL] ps 不可用，无法读取 Java 进程状态' "${TMP_ROOT}/backend-ps-error.log"

XXL_DIAG_FIXTURE_MODE=backend-log-no-match backend_run --expected-host 122.233.30.4 >"${TMP_ROOT}/backend-log-no-match.log" 2>&1
grep -Fq '[INFO] 最近日志中没有命中诊断关键词' "${TMP_ROOT}/backend-log-no-match.log"

set +e
TEST_AGENT_DIAG_EXTRA_PATH="${BROKEN_SHA_BIN}:" backend_run --expected-host 122.233.30.4 >"${TMP_ROOT}/backend-missing-sha.log" 2>&1
status=$?
set -e
test "${status}" -eq 2
grep -Fq '[FAIL] 缺少可用的 SHA-256 实现' "${TMP_ROOT}/backend-missing-sha.log"

set +e
TEST_AGENT_DIAG_EXTRA_PATH="${FILTER_FAILURE_BIN}:" XXL_DIAG_FIXTURE_MODE=backend-filter-failure \
  backend_run --expected-host 122.233.30.4 >"${TMP_ROOT}/backend-filter-failure.log" 2>&1
status=$?
set -e
test "${status}" -eq 2
grep -Fq '[FAIL] 日志关键词过滤执行失败' "${TMP_ROOT}/backend-filter-failure.log"

set +e
TEST_AGENT_DIAG_EXTRA_PATH="${REDACTION_FAILURE_BIN}:" XXL_DIAG_FIXTURE_MODE=backend-redaction-failure \
  backend_run --expected-host 122.233.30.4 >"${TMP_ROOT}/backend-redaction-failure.log" 2>&1
status=$?
set -e
test "${status}" -eq 2
grep -Fq '[FAIL] 日志脱敏执行失败' "${TMP_ROOT}/backend-redaction-failure.log"

set +e
TEST_AGENT_DIAG_PS_BIN=missing-ps-command backend_run --expected-host 122.233.30.4 >"${TMP_ROOT}/backend-missing-ps.log" 2>&1
status=$?
set -e
test "${status}" -eq 2
grep -Fq '[FAIL] 缺少 ps，无法完成后台诊断' "${TMP_ROOT}/backend-missing-ps.log"

for misuse in missing-host invalid-host minutes-low minutes-high wrong-machine; do
  set +e
  case "${misuse}" in
    missing-host) backend_run >"${TMP_ROOT}/backend-${misuse}.log" 2>&1 ;;
    invalid-host) backend_run --expected-host 122.233.30.5 >"${TMP_ROOT}/backend-${misuse}.log" 2>&1 ;;
    minutes-low) backend_run --expected-host 122.233.30.4 --minutes 4 >"${TMP_ROOT}/backend-${misuse}.log" 2>&1 ;;
    minutes-high) backend_run --expected-host 122.233.30.4 --minutes 121 >"${TMP_ROOT}/backend-${misuse}.log" 2>&1 ;;
    wrong-machine) XXL_DIAG_BACKEND_IP=122.233.30.114 backend_run --expected-host 122.233.30.4 >"${TMP_ROOT}/backend-${misuse}.log" 2>&1 ;;
  esac
  status=$?
  set -e
  test "${status}" -eq 2
done

READONLY_SQL_FILE="${ROOT_DIR}/deploy/internal/xxl-job-readonly-check.sql"
if [[ ! -f "${READONLY_SQL_FILE}" ]]; then
  printf 'missing XXL-JOB read-only SQL: %s\n' "${READONLY_SQL_FILE}" >&2
  exit 1
fi

validate_readonly_sql_file() {
  local sql_file="$1"
  local readonly_sql statement

  # 只检查去除 -- 注释后的可执行 SQL，避免运维说明中的敏感词触发误报。
  readonly_sql="$(sed -E 's/--.*$//' "${sql_file}")"
  # read -d 在 EOF 前无分号时会返回非零但保留内容；|| 条件确保该最后语句仍被校验。
  while IFS= read -r -d ';' statement || [[ -n "${statement}" ]]; do
    validate_readonly_sql_statement "${statement}" || return 1
  done < <(printf '%s' "${readonly_sql}")
}

validate_readonly_sql_statement() {
  local statement="$1"
  local trimmed_statement statement_first_word safe_replace_statement safe_digest_statement

  trimmed_statement="$(printf '%s' "${statement}" | sed -E 's/^[[:space:]]+//; s/[[:space:]]+$//')"
  statement_first_word="$(printf '%s\n' "${trimmed_statement}" | awk 'NF { print $1; exit }' | tr '[:lower:]' '[:upper:]')"
  [[ -z "${statement_first_word}" ]] && return 0
  case "${statement_first_word}" in
    SELECT|SHOW|WITH) ;;
    *)
      printf 'XXL-JOB read-only SQL contains a non-read-only statement: %s\n' "${statement_first_word:-<empty>}" >&2
      return 1
      ;;
  esac

  # 只移除 REPLACE(...) 字符串函数；任何独立 REPLACE（包括 WITH 后的 REPLACE INTO）仍须拒绝。
  safe_replace_statement="$(printf '%s' "${trimmed_statement}" | sed -E 's/REPLACE[[:space:]]*\([^)]*\)//Ig')"
  if printf '%s\n' "${safe_replace_statement}" | grep -Eqi '(^|[^[:alnum:]_])(INSERT|UPDATE|DELETE|REPLACE|MERGE|TRUNCATE|ALTER|CREATE|DROP|CALL|GRANT|REVOKE|LOCK|UNLOCK|SET|START|TRANSACTION|COMMIT|ROLLBACK)([^[:alnum:]_]|$)'; then
    printf 'XXL-JOB read-only SQL contains a forbidden keyword\n' >&2
    return 1
  fi
  if printf '%s\n' "${trimmed_statement}" | grep -Eqi \
    '(^|[^[:alnum:]_])INTO[[:space:]]+(OUTFILE|DUMPFILE)([^[:alnum:]_]|$)|(^|[^[:alnum:]_])(GET_LOCK|RELEASE_LOCK|IS_FREE_LOCK|IS_USED_LOCK)[[:space:]]*\(|:='; then
    printf 'XXL-JOB read-only SQL contains a file write, lock side effect, or user-variable assignment\n' >&2
    return 1
  fi

  safe_digest_statement="$(printf '%s' "${trimmed_statement}" | sed -E 's/CHAR_LENGTH[[:space:]]*\([[:space:]]*platform_session_digest[[:space:]]*\)//Ig')"
  if printf '%s\n' "${safe_digest_statement}" | grep -Eqi '(^|[^[:alnum:]_])(password|token|platform_session_digest|executor_param|trigger_msg|handle_msg)([^[:alnum:]_]|$)'; then
    printf 'XXL-JOB read-only SQL projects a sensitive column or value\n' >&2
    return 1
  fi
}

EOF_DELETE_SQL_FILE="${TMP_ROOT}/readonly-eof-delete.sql"
WITH_REPLACE_SQL_FILE="${TMP_ROOT}/readonly-with-replace.sql"
INTO_OUTFILE_SQL_FILE="${TMP_ROOT}/readonly-into-outfile.sql"
INTO_DUMPFILE_SQL_FILE="${TMP_ROOT}/readonly-into-dumpfile.sql"
GET_LOCK_SQL_FILE="${TMP_ROOT}/readonly-get-lock.sql"
RELEASE_LOCK_SQL_FILE="${TMP_ROOT}/readonly-release-lock.sql"
IS_FREE_LOCK_SQL_FILE="${TMP_ROOT}/readonly-is-free-lock.sql"
IS_USED_LOCK_SQL_FILE="${TMP_ROOT}/readonly-is-used-lock.sql"
USER_VARIABLE_ASSIGNMENT_SQL_FILE="${TMP_ROOT}/readonly-user-variable-assignment.sql"
printf 'DELETE FROM xxl_job_user' >"${EOF_DELETE_SQL_FILE}"
printf "WITH cte AS (SELECT 1) REPLACE INTO xxl_job_user (username) VALUES ('unsafe');\n" >"${WITH_REPLACE_SQL_FILE}"
printf "SELECT 1 INTO OUTFILE '/tmp/xxl-job-diagnostic';\n" >"${INTO_OUTFILE_SQL_FILE}"
printf "SELECT 1 INTO DUMPFILE '/tmp/xxl-job-diagnostic';\n" >"${INTO_DUMPFILE_SQL_FILE}"
printf "SELECT GET_LOCK('xxl-job-diagnostic', 0);\n" >"${GET_LOCK_SQL_FILE}"
printf "SELECT RELEASE_LOCK('xxl-job-diagnostic');\n" >"${RELEASE_LOCK_SQL_FILE}"
printf "SELECT IS_FREE_LOCK('xxl-job-diagnostic');\n" >"${IS_FREE_LOCK_SQL_FILE}"
printf "SELECT IS_USED_LOCK('xxl-job-diagnostic');\n" >"${IS_USED_LOCK_SQL_FILE}"
printf 'SELECT @diagnostic := 1;\n' >"${USER_VARIABLE_ASSIGNMENT_SQL_FILE}"
if validate_readonly_sql_file "${EOF_DELETE_SQL_FILE}"; then
  printf 'XXL-JOB read-only SQL accepted DELETE without a trailing semicolon\n' >&2
  exit 1
fi
if validate_readonly_sql_file "${WITH_REPLACE_SQL_FILE}"; then
  printf 'XXL-JOB read-only SQL accepted WITH + REPLACE DML\n' >&2
  exit 1
fi
sql_negative_failures=0
for unsafe_sql_fixture in \
  "${INTO_OUTFILE_SQL_FILE}" \
  "${INTO_DUMPFILE_SQL_FILE}" \
  "${GET_LOCK_SQL_FILE}" \
  "${RELEASE_LOCK_SQL_FILE}" \
  "${IS_FREE_LOCK_SQL_FILE}" \
  "${IS_USED_LOCK_SQL_FILE}" \
  "${USER_VARIABLE_ASSIGNMENT_SQL_FILE}"; do
  if validate_readonly_sql_file "${unsafe_sql_fixture}"; then
    printf 'XXL-JOB 只读 SQL 错误接受静态副作用夹具: %s\n' "${unsafe_sql_fixture##*/}" >&2
    sql_negative_failures=$((sql_negative_failures + 1))
  fi
done
if (( sql_negative_failures > 0 )); then
  printf 'XXL-JOB 只读 SQL 共错误接受 %s 类静态副作用\n' "${sql_negative_failures}" >&2
  exit 1
fi
if ! validate_readonly_sql_file "${READONLY_SQL_FILE}"; then
  exit 1
fi

printf 'XXL-JOB read-only SQL static boundary verified\n'

printf 'XXL-JOB enterprise diagnostics verified\n'
