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

- **🧠 语义模式（Smart）**：自然语言 → LLM 生成 shell 命令 → 确认 → 执行，支持取消（Ctrl+C）、超时/异常自动降级直通
- **💬 多轮对话上下文**：保留最近 10 轮对话与命令执行结果（含直通 `!` 命令），模型可引用历史输出完成后续操作；`/context` 查看、`/clear` 清空，退出会话即清空
- **⚡ 直通模式（Direct）**：`!` 前缀或 `/mode direct` 切换，命令原样执行，与原生终端无异
- **🛡️ 三层安全防线**：模型自审（UNSAFE）→ 黑名单/风险分级过滤 → 危险命令二次确认；默认拦截内网地址扫描类请求
- **📋 全量审计日志**：每条命令的来源（用户/LLM）、原文、执行结果、耗时，JSON 格式落盘
- **🖥️ 真终端体验**：JLine REPL、Tab 路径补全、命令历史（↑↓ 翻阅）、等待动画（可关闭）
- **🔌 交互式命令支持**：`ssh`/`vim`/`top` 等直连终端（inheritIO），可正常分配伪终端交互
- **📂 会话状态命令 REPL 级处理**：`cd`/`pwd`/`pushd`/`popd`/`dirs`/`export`/`unset`/`source` 跨命令持久生效（详见[使用方式](#-使用方式)与[限制与约束](#-限制与约束)）
- **🔒 只读模式**：`execution.readOnly: true` 配置开启，仅允许只读命令，适合生产环境排障
- **⚙️ 配置中心化**：启动模式、只读开关等初始化项全部收编入 `config.yaml`，启动参数仅保留 `--config`

## 🚀 快速开始

### 1. 环境要求

| 依赖 | 要求 | 说明 |
| --- | --- | --- |
| JDK | 17+（17 / 21 / 24+ 均可） | 24+ 启动脚本会自动追加兼容参数 |
| Maven | 3.6+ | 构建用 |
| LLM API | 任意 OpenAI 兼容协议 | 智谱 / DeepSeek / OpenAI / Ollama 等 |
| 操作系统 | macOS / Linux / Windows | 执行层跨平台；**状态命令语法为 Unix 向**（见[限制与约束](#-限制与约束)） |

### 2. 构建

```bash
git clone https://github.com/yourname/pragmatic-ai-shell.git
cd pragmatic-ai-shell
mvn clean package -DskipTests
```

产物：`target/pragmatic-ai-shell-1.0-SNAPSHOT.jar`

### 3. 配置文件（必需）

**启动时必须存在有效配置文件**（默认当前目录 `config.yaml`，或用 `--config` 指定），缺失/不可读/解析失败将报错退出（退出码 1）。

基于模板创建并替换密钥：

```bash
cp config.example.yaml config.yaml
```

编辑 `config.yaml`，至少填写 LLM 密钥（语义模式需要）：

```yaml
llm:
  provider: openai                        # OpenAI 兼容协议（含智谱等）
  baseUrl: https://api.openai.com/v1      # 你的服务地址
  model: gpt-4o-mini                      # 模型名
  apiKey: sk-替换成你的密钥
```

> 未配置 LLM 参数时，程序会警告并自动以直通模式启动（纯直通使用无需 LLM）。
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
| `--config <路径>` | 指定配置文件（默认当前目录 `config.yaml`，缺失则报错退出） |

> v4 起启动参数仅保留 `--config`：启动模式与只读开关等初始化项全部由 `config.yaml` 配置。

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

### 多轮对话与上下文（默认开启）

语义模式保留最近 10 轮对话（`llm.context.maxTurns`），每轮包含你的输入、生成的命令与执行结果摘要（`llm.context.maxResultChars` 控制单轮摘要上限）：

- **直通命令同样入史**：`!` 前缀 / DIRECT 模式执行的命令及结果也会记录，切回语义模式后可引用其输出；
- **失败轮次也记录**：被安全策略拒绝、被跳过、超时的轮次一并入史，模型不会尝试规避安全策略；
- **仅会话内存**：历史不落盘、不跨会话持久化，退出即清空；
- **敏感信息打码**：`sk-` 密钥与 `token=`/`password=` 等凭据在入史前自动脱敏。

```
🤖 > 找出占用 8080 端口的进程
➜ 建议执行: lsof -i :8080
确认执行？[y/N] y
COMMAND   PID USER   FD   TYPE DEVICE SIZE/OFF NODE NAME
java    12345 user   45u  IPv6 0x1234      0t0  TCP *:http-alt (LISTEN)
（退出码 0）
🤖 > 杀掉这个进程
➜ 建议执行: kill -9 12345
```

### 直通模式（提示符 ▶）

两种进入方式：

```
! ls -al            # 任意模式下，! 前缀直接执行
/mode direct        # 切换为直通模式
```

### 内置命令

| 命令 | 说明 |
| --- | --- |
| `/help` | 查看帮助 |
| `/mode smart\|direct` | 切换模式（`smart` 需 LLM 配置完整，否则拒绝切换） |
| `/config` | 查看当前配置（apiKey 打码显示） |
| `/context` | 查看当前多轮上下文（脱敏展示；与 ↑↓ 翻阅的命令历史不同，它是发给模型的历史） |
| `/clear` | 清空多轮上下文，后续对话不再引用此前轮次 |
| `/exit`、`/quit` | 退出 |

### 会话状态命令（REPL 层处理）

以下命令不落入子进程，由 REPL 直接处理，**跨命令持久生效**（Tab 补全同步跟随）：

| 命令 | 说明 |
| --- | --- |
| `cd [path]` | 切换工作目录（无参回配置的 `workDir`）；不存在的目录报错且不改变当前目录 |
| `pwd` | 打印当前工作目录 |
| `pushd [dir]` / `popd` / `dirs` | 目录栈操作（对齐 bash 语义：`pushd` 无参时与栈顶交换） |
| `export KEY=VAL` | 设置会话环境变量，**后续命令可见**（`export` 无参列出已设置项） |
| `unset KEY` | 移除会话环境变量 |
| `source <file>` | **浅解析**：仅提取脚本中的 `export KEY=VAL` / `KEY=VAL` 行（见[限制与约束](#-限制与约束)） |
| `alias` / `function` | ⚠ 仅提示"不会持久生效"，命令仍放行到子进程原样执行 |

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
shell:
  initialMode: smart        # 启动模式: smart / direct（v4 起替代 --mode）
llm:
  provider: openai          # openai / deepseek / ollama
  baseUrl: https://...      # API 地址
  model: gpt-4o-mini        # 模型名
  apiKey: sk-xxx            # API 密钥（语义模式必填，缺失自动降级直通；ollama 免填）
  temperature: 0.0          # 采样温度 [0, 2]
  timeoutSeconds: 60        # LLM 调用超时
  showProgress: true        # 等待动画开关
  context:                  # 多轮对话上下文（v5 新增）
    enabled: true           # 默认开启；false 时每次调用独立（v2 无上下文行为）
    maxTurns: 10            # 保留最近轮数（≥ 1）
    maxResultChars: 2000    # 单轮命令结果摘要上限字符数（≥ 100），超出保留尾部
execution:
  defaultTimeoutSeconds: 60 # 命令执行超时
  workDir: .                # 默认工作目录（~ 展开，相对进程启动目录）
  readOnly: false           # 只读模式
safety:
  strictMode: false         # 严格模式：所有命令都需确认
  confirmDestructive: true  # 危险命令二次确认
  blockPrivateAddresses: true # 拦截内网地址探测
logging:
  auditEnabled: true        # 审计日志开关
  auditPath: ~/.smartcli/audit.log  # 审计路径（~ 展开，相对进程启动目录）
```

> 路径字段（`workDir`/`auditPath`）支持 `~` 展开，相对路径以**进程启动目录**为基准。
> 配置中的未知字段会在启动时告警并忽略；`version` 与程序支持版本不一致时告警。

### 审计日志示例（`~/.smartcli/audit.log`）

```json
{"timestamp":"2026-08-23T03:00:00Z","source":"LLM","input":"找出占用8080端口的进程","command":"lsof -i :8080","exitCode":0,"durationMs":120}
```

## 🏗️ 架构一览

```
用户输入 ──▶ 路由（/ 内置 │ ! 直通 │ 语义）
                │
                ├─ 会话状态命令分发器（cd/pwd/pushd/popd/dirs/export/unset/source/alias 提示）
                │      └─ 进程内模拟：更新 currentDir / 目录栈 / 环境覆盖表（注入后续子进程）
                │
语义路径：LLM 生成命令 ──▶ 安全过滤链（模型自审→黑名单→风险分级→内网拦截）
                │                      │
                ▼                 需确认？──▶ 二次确认
           命令执行器（ProcessBuilder 独立子进程，交互命令 inheritIO）
                │
                ▼
           审计日志落盘
```

## ⚠️ 限制与约束

### 1. 命令执行模型（进程隔离）

每条命令都在**独立子进程**中执行（Unix 用 `/bin/sh -c`，Windows 用 `cmd.exe /c`）。这是安全设计：命令崩溃、死循环、超时只影响子进程，主程序不受影响。

由此带来的直接推论：**在子进程内修改 shell 自身状态的操作（`cd`/`export`/`alias` 等）默认不会影响后续命令**。为此，上表列出的会话状态命令由 REPL 层模拟处理；**不在清单内的状态类命令**（如 `trap`、`set -o`、`stty`、函数定义 `foo() {...}` 等）按普通命令在子进程执行，不持久、也不提示。

### 2. `source` 为浅解析

`source <file>` 只提取脚本中的 `export KEY=VAL` / `KEY=VAL` 行注入会话环境；脚本中的其他逻辑（条件/循环/alias/函数/管道）**不会执行**。执行后会提示实际加载的变量数量。

### 3. 语义模式下输入先经 LLM

语义模式下直接输入的命令（包括状态命令）**先由 LLM 理解**，LLM 返回的命令才被执行；LLM 判定不可行（IMPOSSIBLE）时不会执行。需要确定性行为时，用 `!` 前缀强制直通。

### 4. 不支持环境变量引用

配置文件中**不支持 `${VAR}` 环境变量引用**（v4 决策）：`apiKey` 等敏感配置仅支持 `config.yaml` 明文填写。请勿在公开仓库提交真实密钥。

### 5. Windows 支持现状

- 执行层跨平台（`cmd.exe /c`），目录类状态命令（`cd`/`pushd` 等）在 Windows 下正常工作；
- **状态命令语法为 Unix 向**：`export`/`unset`/`source` 不适用于 cmd；Windows 的 `set`/`call` 语法**未被 REPL 层模拟**——在 Windows 上 `set FOO=bar` 设置的环境变量不会持久（与 Unix 上修复前的 `export` 行为相同）。

### 6. 审计范围

子进程执行的命令写入审计日志；**REPL 层直接处理的状态命令（`cd`/`export` 等）不写审计**；多轮上下文为内存数据，同样不落盘。

### 7. 退出码约定

| 退出码 | 含义 |
| --- | --- |
| 0 | 正常退出（`/exit` 或 Ctrl+D） |
| 1 | 配置错误（文件缺失/不可读/解析失败/校验不通过） |
| 2 | 命令行参数错误（如传入了 v4 已移除的 `--mode`） |

### 8. 交互式命令与超时

- `ssh`/`vim`/`top` 等清单内交互命令走 `inheritIO` 直连终端，**不设超时**（由用户自行退出）；
- 清单外命令如果本身需要 TTY 交互，可能行为异常（stdin 非终端）；
- 命令超时后**递归销毁整棵进程树**（含 `nohup`/`&` 启动的后台任务），不残留孤儿进程；仅刻意脱离进程树的守护进程（daemon 化）不在清理范围。

### 9. 多轮上下文与会话生命周期

多轮上下文仅保存在**当前会话内存**中：退出即清空，不跨会话持久化，`/clear` 可手动清空。入史前仅对密钥类信息（`sk-`、`token=`/`password=` 等）打码；内网 IP、用户名等其他敏感信息不在脱敏范围。

## 🙋 常见问题

**Q：没有 API Key 能用吗？**
A：可以。未配置 LLM 参数时启动会警告并自动降级为直通模式；也可以直接配置 `shell.initialMode: direct` 启动，或用 `!` 前缀逐条走直通，完全不调用 LLM。

**Q：`export FOO=bar` 之后为什么后续命令能看到？**
A：`export`/`unset` 由 REPL 层维护一张会话环境表，每次执行命令时注入子进程，因此跨命令持久。这是 smartcli 对"子进程不共享 shell 状态"这一限制的补偿实现。

**Q：`alias ll='ls -l'` 为什么下次命令就用不了？**
A：`alias`/`function` 属于 shell 命名空间状态，无法在 REPL 层完整模拟，当前策略是**提示后放行到子进程执行**（子进程内有效，退出即失效）。需要时每次重新定义，或用 `!` 前缀确认原样执行。

**Q：启动时出现 `WARNING: Final field ... has been mutated reflectively`？**
A：这是 JDK 24+ 对 Gson 反射行为的告警，用 `bin/smartcli` 脚本启动会自动处理。

**Q：`ssh`/`vim` 这类交互命令能用吗？**
A：可以。交互类命令（ssh/telnet/vim/top 等）自动走 `inheritIO` 直连终端。

**Q：模型生成的危险命令会被执行吗？**
A：三重防护——模型侧返回 `UNSAFE` 拒答；黑名单与风险分类器拦截 `rm -rf /` 等高危命令；危险操作强制人工确认。

**Q：多轮上下文会保存什么？会不会泄露密钥？**
A：保存最近 10 轮的用户输入、命令与执行结果摘要（含直通 `!` 命令）。入史前 `sk-` 密钥与 `token=`/`password=` 等凭据自动打码（`sk-****` / `token=****`），`/context` 展示与发送给模型的都是打码后的值；历史仅存在当前会话内存，退出即清空。
