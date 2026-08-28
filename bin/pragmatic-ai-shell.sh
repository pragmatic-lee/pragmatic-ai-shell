#!/usr/bin/env bash
#
# pragmatic-ai-shell 启动脚本（可随二进制一起挪动，无需加入 PATH）。
# 用法：把本脚本与编译好的 pragmatic-ai-shell 二进制放在同一目录，直接运行：
#   ./pragmatic-ai-shell.sh                 # 自动用同目录的 config.yaml
#   ./pragmatic-ai-shell.sh --config x.yaml # 指定配置
#   ./pragmatic-ai-shell.sh --help
#
set -euo pipefail

# 脚本自身所在目录（二进制、config.yaml 都默认在这里）
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

BIN="$SCRIPT_DIR/pragmatic-ai-shell"
CONFIG="$SCRIPT_DIR/config.yaml"

# 校验二进制是否存在（与脚本同目录）
if [ ! -x "$BIN" ]; then
    echo "错误：未找到原生二进制 $BIN" >&2
    echo "请把本脚本与编译好的 pragmatic-ai-shell 放在同一目录。" >&2
    exit 1
fi

# 若用户未显式传 --config，且同目录有 config.yaml，则自动带上
has_config_arg=false
for a in "$@"; do
    [ "$a" = "--config" ] && has_config_arg=true
done

if [ "$has_config_arg" = false ] && [ -f "$CONFIG" ]; then
    exec "$BIN" --config "$CONFIG" "$@"
fi

exec "$BIN" "$@"
