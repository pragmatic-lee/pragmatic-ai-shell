# 🤖 Pragmatic AI Shell（smartcli）

> **一句话介绍：用自然语言驱动终端的智能命令行工具——你说人话，它出命令，确认后执行。**

```
🤖 > 找出占用 8080 端口的进程
➜ 建议执行: lsof -i :8080
确认执行？[y/N] y
COMMAND   PID USER   FD   TYPE DEVICE SIZE/OFF NODE NAME
java    12345 user   45u  IPv6 0x1234      0t0  TCP *:http-alt (LISTEN)
（退出码 0）
```

不用背命令、不用翻 man 手册、不用 Stack Overflow 来回切窗口——描述意图，AI 生成命令，安全过滤后执行，全程可审计。

---

## ✨ 它解决什么问题？

| 场景 | 传统方式 | 用 smartcli |
| --- | --- | --- |
| 忘了某个命令的参数写法 | 搜文档 → 试错 → 再搜 | 直接说「找出 3 天前修改的日志文件」 |
| 运维排障多工具组合 | `ps` + `netstat` + `awk` 现拼 | 一句「看下 8080 端口是谁占的」 |
| 危险命令手滑 | 敲完 `rm -rf` 回车后心跳骤停 | 危险命令自动拦截或强制二次确认 |
| 事后复盘「刚才执行了啥」 | 翻 shell history | 结构化审计日志，含来源/耗时/退出码 |

## 🎯 核心特性

- **🧠 语义模式（Smart）**：自然语言 → LLM 生成 shell 命令 → 确认 → 执行，支持取消（Ctrl+C）、超时/异常自动降级
- **⚡ 直通模式（Direct）**：`!` 前缀或 `/mode direct` 切换，命令原样执行，与原生终端无异
- **🛡️ 三层安全防线**：模型自审（UNSAFE）→ 黑名单/风险分级过滤 → 危险命令二次确认；默认拦截内网地址扫描类请求
- **📋 全量审计日志**：每条命令的来源（用户/LLM）、原文、执行结果、耗时，JSON 格式落盘
- **🖥️ 真终端体验**：JLine REPL、Tab 路径补全、命令历史（↑↓ 翻阅）、等待动画（可关闭）
- **🔌 交互式命令支持**：`ssh`/`vim`/`top` 等直连终端（inheritIO），可正常分配伪终端交互
- **🔒 只读模式**：`--read-only` 启动，仅允许只读命令，适合生产环境排障

## 🚀 快速开始

### 1. 环境要求

| 依赖 | 要求 | 说明 |
| --- | --- | --- |
| JDK | 17+（17 / 21 / 24+ 均可） | 24+ 启动脚本会自动追加兼容参数 |
| Maven | 3.6+ | 构建用 |
| LLM API | 任意 OpenAI 兼容协议 | 智谱 / DeepSeek / OpenAI / Ollama 等 |
| 操作系统 | macOS / Linux / Windows | 跨平台（Unix 用 `/bin/sh -c`，Windows 用 `cmd.exe /c`） |

### 2. 构建

```bash
git clone https://github.com/yourname/pragmatic-ai-shell.git
cd pragmatic-ai-shell
mvn clean package -DskipTests
```

产物：`target/pragmatic-ai-shell-1.0-SNAPSHOT.jar`

### 3. 配置 API Key

编辑项目根目录 `config.yaml`，替换为你自己的密钥：

```yaml
llm:
  provider: openai                        # OpenAI 兼容协议（含智谱等）
  baseUrl: https://api.openai.com/v1      # 你的服务地址
  model: gpt-4o-mini                      # 模型名
  apiKey: sk-替换成你的密钥
```

> ⚠️ **安全提示**：请勿将含真实密钥的 `config.yaml` 提交到公开仓库，建议加入 `.gitignore`。

### 4. 启动

```bash
# 推荐：使用启动脚本（自动处理 JDK 24+ 兼容参数）
bin/smartcli

# 或手动启动（JDK 24+ 需追加参数消除告警）
java --enable-final-field-mutation=ALL-UNNAMED \
     --enable-native-access=ALL-UNNAMED \
     -jar target/pragmatic-ai-shell-1.0-SNAPSHOT.jar
```

启动参数：

| 参数 | 说明 |
| --- | --- |
| `--config <路径>` | 指定配置文件（默认当前目录 `config.yaml`） |
| `--mode smart\|direct` | 启动模式，默认语义模式 |
| `--read-only` | 只读模式，拒绝一切写操作 |

## 📖 使用方式

### 语义模式（默认，提示符 🤖）

直接输入自然语言：

```
🤖 > 查看当前目录下最大的 5 个文件
➜ 建议执行: du -ah . | sort -rh | head -5
确认执行？[y/N] y
...
```

不满意可以输入 `n` 跳过，或按 `Ctrl+C` 取消正在进行的 LLM 请求。

### 直通模式（提示符 ▶）

三种进入方式：

```
! ls -al            # 任意模式下，! 前缀直接执行
/mode direct        # 切换为直通模式
```

### 内置命令

| 命令 | 说明 |
| --- | --- |
| `/help` | 查看帮助 |
| `/mode smart\|direct` | 切换模式 |
| `/config` | 查看当前配置 |
| `/exit`、`/quit` | 退出 |
| `cd` / `pwd` | 内置目录切换（REPL 级持久生效，Tab 补全同步跟随） |

### 异常降级

LLM 超时或服务不可用时自动降级为直通模式，不会崩溃中断会话：

```
⏳ 正在理解意图…
模型响应超时（60s）。
[降级] 语义能力不可用（超时），已切换为直通模式。
```

## ⚙️ 配置说明（config.yaml）

```yaml
version: 1
llm:
  provider: openai          # openai / ollama
  baseUrl: https://...      # API 地址
  model: gpt-4o-mini        # 模型名
  apiKey: sk-xxx            # API 密钥
  timeoutSeconds: 60        # LLM 调用超时
  showProgress: true        # 等待动画开关
execution:
  defaultTimeoutSeconds: 60 # 命令执行超时
  workDir: .                # 默认工作目录
  readOnly: false           # 只读模式
safety:
  strictMode: false         # 严格模式：所有命令都需确认
  confirmDestructive: true  # 危险命令二次确认
  blockPrivateAddresses: true # 拦截内网地址探测
logging:
  auditEnabled: true        # 审计日志开关
  auditPath: ~/.smartcli/audit.log
```

### 审计日志示例（`~/.smartcli/audit.log`）

```json
{"timestamp":"2026-08-23T03:00:00Z","source":"LLM","input":"找出占用8080端口的进程","command":"lsof -i :8080","exitCode":0,"durationMs":120}
```

## 🏗️ 架构一览

```
用户输入 ──▶ 路由（/ 内置 │ ! 直通 │ 语义）
                │
语义路径：LLM 生成命令 ──▶ 安全过滤链（模型自审→黑名单→风险分级→内网拦截）
                │                      │
                ▼                 需确认？──▶ 二次确认
           命令执行器（ProcessBuilder，交互命令 inheritIO）
                │
                ▼
           审计日志落盘
```

## 🙋 常见问题

**Q：没有 API Key 能用吗？**
A：可以。用 `--mode direct` 或 `!` 前缀走直通模式，完全不调用 LLM。

**Q：启动时出现 `WARNING: Final field ... has been mutated reflectively`？**
A：这是 JDK 24+ 对 Gson 反射行为的告警，用 `bin/smartcli` 脚本启动会自动处理。

**Q：`ssh`/`vim` 这类交互命令能用吗？**
A：可以。交互类命令（ssh/telnet/vim/top 等）自动走 `inheritIO` 直连终端。

**Q：模型生成的危险命令会被执行吗？**
A：三重防护——模型侧返回 `UNSAFE` 拒答；黑名单与风险分类器拦截 `rm -rf /` 等高危命令；危险操作强制人工确认。
