#!/usr/bin/env bash

# 企业归档必须保留项目需要的合法点文件，但统一排除 macOS 自动生成的元数据文件。
TEST_AGENT_ARCHIVE_METADATA_EXCLUDES=(
  '._*'
  '*/._*'
  '.DS_Store'
  '*/.DS_Store'
  '__MACOSX'
  '__MACOSX/*'
  '*/__MACOSX'
  '*/__MACOSX/*'
  '.Spotlight-V100'
  '.Spotlight-V100/*'
  '*/.Spotlight-V100'
  '*/.Spotlight-V100/*'
  '.Trashes'
  '.Trashes/*'
  '*/.Trashes'
  '*/.Trashes/*'
  '.fseventsd'
  '.fseventsd/*'
  '*/.fseventsd'
  '*/.fseventsd/*'
)

archive_tar_metadata_flags() {
  if tar --version 2>&1 | grep -qi 'bsdtar'; then
    printf '%s\n' --no-mac-metadata --no-xattrs --no-acls --no-fflags
  fi
}

archive_strip_file_metadata() {
  if command -v xattr >/dev/null 2>&1; then
    # 清除交付物自身的扩展属性，避免复制到不支持 xattr 的介质时再产生同名 `._*` 旁车文件。
    xattr -c "$@"
  fi
}

archive_create_tar_gz() {
  local output="$1" base_dir="$2"
  shift 2
  local -a metadata_flags=() exclude_flags=()
  local value
  while IFS= read -r value; do
    [[ -z "${value}" ]] || metadata_flags+=("${value}")
  done < <(archive_tar_metadata_flags)
  for value in "${TEST_AGENT_ARCHIVE_METADATA_EXCLUDES[@]}"; do
    exclude_flags+=("--exclude=${value}")
  done
  COPYFILE_DISABLE=1 COPY_EXTENDED_ATTRIBUTES_DISABLE=1 \
    tar "${metadata_flags[@]}" "${exclude_flags[@]}" \
      -C "${base_dir}" -czf "${output}" "$@"
  archive_strip_file_metadata "${output}"
}

archive_create_tar() {
  local output="$1" base_dir="$2"
  shift 2
  local -a metadata_flags=() exclude_flags=()
  local value
  while IFS= read -r value; do
    [[ -z "${value}" ]] || metadata_flags+=("${value}")
  done < <(archive_tar_metadata_flags)
  for value in "${TEST_AGENT_ARCHIVE_METADATA_EXCLUDES[@]}"; do
    exclude_flags+=("--exclude=${value}")
  done
  COPYFILE_DISABLE=1 COPY_EXTENDED_ATTRIBUTES_DISABLE=1 \
    tar "${metadata_flags[@]}" "${exclude_flags[@]}" \
      -C "${base_dir}" -cf "${output}" "$@"
  archive_strip_file_metadata "${output}"
}

archive_create_zip() {
  local output="$1" base_dir="$2"
  shift 2
  local -a exclude_args=()
  local value listing
  for value in "${TEST_AGENT_ARCHIVE_METADATA_EXCLUDES[@]}"; do
    exclude_args+=("${value}")
  done
  (
    cd "${base_dir}"
    COPYFILE_DISABLE=1 COPY_EXTENDED_ATTRIBUTES_DISABLE=1 \
      zip -X -qr "${output}" "$@" -x "${exclude_args[@]}"
  )
  listing="$(unzip -Z1 "${output}")"
  if grep -Eq '(^|/)(\._[^/]*|\.DS_Store|__MACOSX|\.Spotlight-V100|\.Trashes|\.fseventsd)(/|$)' \
    <<<"${listing}"; then
    echo "ZIP contains forbidden macOS metadata: ${output}" >&2
    exit 1
  fi
  archive_strip_file_metadata "${output}"
}
