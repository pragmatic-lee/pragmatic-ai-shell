#!/usr/bin/env bash
#
# 在 Docker 内构建 Linux x86_64 原生二进制（Apple Silicon 上通过 QEMU 模拟，较慢）。
# 用法： ./bin/build-linux-amd64.sh
# 产物： dist/pragmatic-ai-shell-linux-amd64
#
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
IMAGE_TAG="smartcli-native:linux-amd64"
OUTPUT_DIR="$PROJECT_DIR/dist"
OUTPUT_FILE="$OUTPUT_DIR/pragmatic-ai-shell-linux-amd64"

cd "$PROJECT_DIR"

echo "==> 构建 Linux amd64 镜像（QEMU 模拟，可能需要 20-40 分钟）..."
DOCKER_BUILDKIT=1 docker build --platform linux/amd64 -t "$IMAGE_TAG" -f Dockerfile.native .

echo "==> 导出产物..."
mkdir -p "$OUTPUT_DIR"
# scratch 镜像无 CMD/ENTRYPOINT，docker create 需传占位命令（只创建不运行）
CONTAINER=$(docker create --platform linux/amd64 "$IMAGE_TAG" true)
docker cp "$CONTAINER":/pragmatic-ai-shell "$OUTPUT_FILE"
docker rm "$CONTAINER" >/dev/null
chmod +x "$OUTPUT_FILE"

echo "==> 完成: $OUTPUT_FILE"
file "$OUTPUT_FILE" 2>/dev/null || true
