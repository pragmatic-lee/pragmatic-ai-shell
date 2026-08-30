#!/usr/bin/env bash
#
# 打包 macOS 标准 DMG（拖拽安装，方案 A）。
# 用法： ./bin/build-dmg.sh            # 需先构建 target/pragmatic-ai-shell（./mvnw package -Pnative -DskipTests）
# 产物： dist/pragmatic-ai-shell-<VERSION>-macos-<ARCH>.dmg
#
# 设计要点（见 docs/design/core/macOS-DMG分发打包方案.md 与《方案A实施计划》）：
# - 配置零脚本逻辑：launcher 不传 --config，依赖程序三级回落在 ~/.smartcli/config.yaml 自动生成；
# - 安全底线：内置模板 config.example.yaml 仅含占位密钥，打包前强制校验；
# - staging 目录分离：.app 组装目录与 DMG 内容目录分开，避免嵌套拷贝；
# - 一步出只读压缩镜像（UDZO），不走可写中间镜像、不做窗口美化。
#
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
cd "$PROJECT_DIR"

VERSION="${VERSION:-1.0}"
ARCH="$(uname -m)"                      # arm64 | x86_64
APP_NAME="pragmatic-ai-shell"
DIST_DIR="$PROJECT_DIR/dist"
APP_BUILD_DIR="$DIST_DIR/app-build"     # .app 组装目录（与 DMG 内容目录分离）
DMG_SRC="$DIST_DIR/dmg-src"             # DMG 内容目录
APP="$APP_BUILD_DIR/$APP_NAME.app"
DMG="$DIST_DIR/$APP_NAME-$VERSION-macos-$ARCH.dmg"

# ---------- 0) 前置检查 ----------
[ -x "target/$APP_NAME" ] || { echo "错误: 未找到 target/$APP_NAME，请先执行 ./mvnw package -Pnative -DskipTests"; exit 1; }
grep -q "sk-REPLACE_ME" config.example.yaml || { echo "错误: config.example.yaml 疑似含真实密钥，中止打包"; exit 1; }
command -v hdiutil >/dev/null || { echo "错误: 未找到 hdiutil（仅支持 macOS）"; exit 1; }

rm -rf "$APP_BUILD_DIR" "$DMG_SRC" "$DMG"
mkdir -p "$APP/Contents/MacOS" "$APP/Contents/Resources" "$DMG_SRC"

# ---------- 1) 生成图标（系统自带 sips + iconutil） ----------
echo "==> 生成图标 AppIcon.icns ..."
ICONSET="$DIST_DIR/AppIcon.iconset"
rm -rf "$ICONSET"; mkdir -p "$ICONSET"
sips -z 16 16     logo.png --out "$ICONSET/icon_16x16.png"      >/dev/null
sips -z 32 32     logo.png --out "$ICONSET/icon_16x16@2x.png"   >/dev/null
sips -z 32 32     logo.png --out "$ICONSET/icon_32x32.png"      >/dev/null
sips -z 64 64     logo.png --out "$ICONSET/icon_32x32@2x.png"   >/dev/null
sips -z 128 128   logo.png --out "$ICONSET/icon_128x128.png"    >/dev/null
sips -z 256 256   logo.png --out "$ICONSET/icon_128x128@2x.png" >/dev/null
sips -z 256 256   logo.png --out "$ICONSET/icon_256x256.png"    >/dev/null
sips -z 512 512   logo.png --out "$ICONSET/icon_256x256@2x.png" >/dev/null
sips -z 512 512   logo.png --out "$ICONSET/icon_512x512.png"    >/dev/null
iconutil -c icns "$ICONSET" -o "$APP/Contents/Resources/AppIcon.icns"

# ---------- 2) 组装 .app bundle ----------
echo "==> 组装 $APP_NAME.app ..."
cp "target/$APP_NAME"        "$APP/Contents/MacOS/$APP_NAME"
cp "bin/macos-launcher.sh"   "$APP/Contents/MacOS/launcher"
cp "config.example.yaml"     "$APP/Contents/Resources/config.example.yaml"
cat > "$APP/Contents/Info.plist" <<PLIST
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
    <key>CFBundleName</key><string>$APP_NAME</string>
    <key>CFBundleDisplayName</key><string>SmartCli</string>
    <key>CFBundleIdentifier</key><string>io.pragmatic.shell</string>
    <key>CFBundleExecutable</key><string>launcher</string>
    <key>CFBundleIconFile</key><string>AppIcon</string>
    <key>CFBundleVersion</key><string>$VERSION</string>
    <key>CFBundlePackageType</key><string>APPL</string>
    <key>LSMinimumSystemVersion</key><string>11.0</string>
</dict>
</plist>
PLIST
chmod +x "$APP/Contents/MacOS/$APP_NAME" "$APP/Contents/MacOS/launcher"

# ---------- 3) DMG 内容：应用 + Applications 替身 ----------
cp -R "$APP" "$DMG_SRC/"
ln -s /Applications "$DMG_SRC/Applications"

# ---------- 4) 一步生成只读压缩 DMG（UDZO） ----------
echo "==> 生成 DMG ..."
hdiutil create -volname "$APP_NAME" -srcfolder "$DMG_SRC" \
    -ov -format UDZO -imagekey zlib-level=9 "$DMG"

# ---------- 5) 清理中间目录 ----------
rm -rf "$APP_BUILD_DIR" "$DMG_SRC" "$ICONSET"
echo "==> 产物: $DMG"
ls -lh "$DMG"
