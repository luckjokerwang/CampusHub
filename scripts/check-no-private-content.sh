#!/usr/bin/env bash
# 公开仓自检：确认没有私有服务实现、协议常量、凭据或本机路径混进来。
#
# 私有主仓在导出时也会跑同样的检查（quality/export_public_shell.sh --check）。
# 这里放一份是为了让公开仓的 CI 能独立守住这条线——贡献者本地也能跑。
#
# 模式串以 base64 存放：本脚本自己会被扫描到，明文写在这里会让扫描永远失败。
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

FAILED=0
SELF="check-no-private-content.sh"

scan() {
  local pat="$1" mode="$2"
  local hits
  if [[ "$mode" == "regex" ]]; then
    hits=$(grep -rIl --exclude-dir=build --exclude-dir=.gradle --exclude-dir=.git \
      --exclude-dir=.kotlin --exclude-dir=.skill-eval --exclude="$SELF" \
      -E "$pat" . 2>/dev/null || true)
  else
    hits=$(grep -rIl --exclude-dir=build --exclude-dir=.gradle --exclude-dir=.git \
      --exclude-dir=.kotlin --exclude-dir=.skill-eval --exclude="$SELF" \
      -F "$pat" . 2>/dev/null || true)
  fi
  if [[ -n "$hits" ]]; then
    echo "ERROR: 命中敏感内容:" >&2
    echo "$hits" | sed 's/^/    /' >&2
    FAILED=1
  fi
}

# ── 私有服务与协议常量（正则） ────────────────────────────────────────
REGEX_PATTERNS_B64=(
  aWxpZmU3OThcLmNvbQ==     # 私有网关域名
  bHh0NlwuY24=             # 私有网关域名
  cG9ja2V0dW5pXC5uZXQ=     # 私有网关域名
  MUZGNzVFNTEy             # 私有签名盐值
  WC1HaG9zdA==             # 私有请求头
  WC1Qcm9kdWN0LVZlcg==     # 私有请求头
  L2RhdGEvbG9jYWwvdG1w     # 开发期凭据路径
  d2F0ZXJfNzk4XC5qc29u     # 开发期凭据文件
  YXBwVG9rZW4=             # 私有凭据键名
  c2NvcmVUb2tlbg==         # 私有凭据键名
  YXBpXC5yc2t5XC5uZXQ=     # 已废弃的第三方中转
)

for b64 in "${REGEX_PATTERNS_B64[@]}"; do
  scan "$(printf '%s' "$b64" | base64 -d)" regex
done

# ── 本机路径（字面量） ────────────────────────────────────────────────
LITERAL_PATTERNS_B64=(
  L2hvbWUvd3p5           # 开发者家目录
  QzpcXFVzZXJz           # Windows 用户目录
  bGlmZS03OTg=           # 上游参考仓库目录名
)

for b64 in "${LITERAL_PATTERNS_B64[@]}"; do
  scan "$(printf '%s' "$b64" | base64 -d)" literal
done

# ── 私有服务源码目录不应存在 ──────────────────────────────────────────
for dir in app/src/main/java/com/campushub/app/core/service/ilife \
           app/src/main/java/com/campushub/app/core/service/yxt \
           app/src/main/java/com/campushub/app/core/service/pu \
           app/src/main/java/com/campushub/app/core/service/jufe; do
  if [[ -d "$dir" ]]; then
    echo "ERROR: 私有服务目录不应出现在公开仓: $dir" >&2
    FAILED=1
  fi
done

if [[ $FAILED -ne 0 ]]; then
  echo "自检失败：公开仓包含不应公开的内容。" >&2
  exit 1
fi

echo "自检通过：未发现私有协议常量、凭据或本机路径。"
