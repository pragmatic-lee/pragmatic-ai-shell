#!/usr/bin/env bash
#
# pragmatic-ai-shell.app 启动器（打进 .app bundle 的 Contents/MacOS/launcher）。
# 职责：
#   1. 不传 --config 启动：依赖程序三级回落在 ~/.smartcli/config.yaml 自动生成配置；
#      不采用"复制模板 + 显式 --config"：占位密钥 sk-REPLACE_ME 会被判定为已配置，
#      且 --config 指定路径缺失时直接报错退出；程序自动生成的模板 apiKey 留空，干净降级直通；
#   2. 检测运行环境：终端内直接启动；Finder 双击（无终端）时唤起 Terminal.app。
#
set -euo pipefail

APP_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BIN="$APP_DIR/pragmatic-ai-shell"

run_in_terminal() {
    # 不传 --config：工作目录无 config.yaml 时，程序自动在 ~/.smartcli/config.yaml 生成（apiKey 留空）
    exec "$BIN" "$@"
}

if [ -t 0 ]; then
    # 终端内启动（终端直接执行 launcher / .app 内二进制）
    run_in_terminal "$@"
else
    # Finder 双击启动：无终端，REPL 无法交互，转交 Terminal.app
    osascript <<APPLESCRIPT
tell application "Terminal"
    activate
    do script "exec '$APP_DIR/launcher'"
end tell
APPLESCRIPT
fi
