#!/usr/bin/env bash
# 带网络重试的 Gradle 包装器。
#
# GitHub Actions runner 访问 github.com 的 Release 资源时会偶发 502/503/504
# 或读取超时（Gradle 分发包、Kotlin 编译器等都走这个域名）。这类失败不是
# 代码问题，但会让 CI / Release 直接变红。这里只对「下载类」错误重试，
# 真正的编译或测试失败立即返回，避免掩盖问题。
#
# 用法（必须在仓库根目录执行）：
#   GRADLE_RETRY_LOG=gradle_test.log ./scripts/gradle-retry.sh :app:testPublicDebugUnitTest
set -u

log_file="${GRADLE_RETRY_LOG:-gradle-retry.log}"
max_attempts="${GRADLE_RETRY_ATTEMPTS:-3}"
attempt=1
exit_code=1

while [ "$attempt" -le "$max_attempts" ]; do
  : > "$log_file"
  ./gradlew "$@" > "$log_file" 2>&1
  exit_code=$?

  if [ "$exit_code" -eq 0 ]; then
    exit 0
  fi

  cat "$log_file"

  # 只重试网络下载失败；编译错误、测试断言失败等直接退出。
  if ! grep -Eiq 'HTTP (502|503|504)|Read timed out|Connect timed out|Connection reset|Could not GET|Could not download|Downloading .* failed' "$log_file"; then
    exit "$exit_code"
  fi

  if [ "$attempt" -lt "$max_attempts" ]; then
    delay=$((attempt * 15))
    echo "Gradle 下载失败（第 $attempt/$max_attempts 次），${delay}s 后重试..." >&2
    sleep "$delay"
  fi
  attempt=$((attempt + 1))
done

exit "$exit_code"
