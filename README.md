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

- **🚀 零配置首次启动**：当前目录无配置文件时自动生成带注释的默认配置并以直通模式立即可用；`/setup` 交互向导引导配置大模型（支持多模型、可反复追加），保存仅写回 `llm` 节点
- **🔀 多模型接入**：`config.yaml` 中用 `llm.profiles` 声明多个模型并指定 `llm.defaultProfile` 缺省激活；`/model` 查看列表、`/model switch <id>` 运行中切换、`/model check [id]` 健康检查；存量单模型配置（`llm` 顶层字段）零迁移兼容，自动合成隐式 Profile `(inline)`
- **🎨 启动界面（Splash）**：进入 REPL 前渲染品牌横幅、环境概览、模型列表与安全策略线框界面；非 TTY 自动降级为纯文本，可用 `shell.splash.enabled: false` 关闭
- **🧠 语义模式（Smart）**：自然语言 → LLM 生成 shell 命令 → 确认 → 执行，支持取消（Ctrl+C）、超时/异常自动降级直通
- **💬 多轮对话上下文**：保留最近 10 轮对话与命令执行结果（含直通 `!` 命令），模型可引用历史输出完成后续操作；`/context` 查看、`/clear` 清空，退出会话即清空
- **🌐 环境感知（环境指纹）**：启动时采集本机 OS / Shell / 已装工具及版本，注入 LLM 使生成命令只用已安装工具、按版本选语法；`/profile` 查看、`/profile refresh` 重新采集，可一键关闭（`llm.profile.enabled=false`）
- **⚡ 直通模式（Direct）**：`!` 前缀或 `/mode direct` 切换，命令原样执行，与原生终端无异
- **🛡️ 三层安全防线**：模型自审（UNSAFE）→ 黑名单/风险分级过滤 → 危险命令二次确认；默认拦截内网地址扫描类请求
- **📋 全量审计日志**：每条命令的来源（用户/LLM）、原文、执行结果、耗时，JSON 格式落盘
- **🖥️ 真终端体验**：JLine REPL、Tab 补全（路径 / 内置命令 / 系统命令名 / 子命令 / 选项）、命令历史（↑↓ 翻阅）、等待动画（可关闭）
- **🔌 交互式命令支持**：`ssh`/`vim`/`top` 等交互命令与 `scp`/`sftp`/`rsync` 文件传输命令直连终端（inheritIO）：交互命令可正常分配伪终端；传输命令进度条实时渲染、不受默认超时约束、密码认证可交互输入
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
git clone https://github.com/pragmatic-lee/pragmatic-ai-shell.git
cd pragmatic-ai-shell
mvn clean package -DskipTests
```

产物：`target/pragmatic-ai-shell-1.0-SNAPSHOT.jar`

### 3. 配置文件（首次启动自动生成）

- **不指定 `--config` 时**：按「当前目录 `config.yaml`（存在才用）→ `~/.smartcli/config.yaml`」依次查找；
  两处均无时，程序会在 **`~/.smartcli` 下自动生成带注释的默认配置**并继续启动，无需手工准备文件。
  默认配置未填 `apiKey`，因此会以**直通模式**启动，并提示输入 `/setup` 引导配置大模型。
  DMG/App 形态不传启动参数，配置自然落在 `~/.smartcli`（与审计日志 `~/.smartcli/audit.log` 同目录）。
- **显式 `--config <path>` 指定了路径但文件不存在**：仍按原策略报错退出（退出码 1），不会擅自创建。
- 文件存在但不可读 / 解析失败：报错退出（不静默覆盖你的手工配置）。

想手工准备时可基于模板创建并替换密钥：

```bash
cp config.example.yaml config.yaml
```

编辑 `config.yaml`，至少填写 LLM 密钥（语义模式需要）。**单模型写法**（直接写 `llm` 顶层字段）：

```yaml
llm:
  provider: deepseek                    # deepseek | openai | ollama
  baseUrl: https://api.deepseek.com/v1  # 你的服务地址
  model: deepseek-chat                  # 模型名
  apiKey: sk-替换成你的密钥              # ollama 本地模型免填
```

**多模型写法**（声明 `defaultProfile` + `profiles` 列表，启用后顶层单模型字段被忽略）：

```yaml
llm:
  defaultProfile: deep
  profiles:
    - id: deep
      provider: deepseek
      baseUrl: https://api.deepseek.com/v1
      model: deepseek-chat
      apiKey: sk-替换成你的密钥
    - id: local
      provider: ollama
      baseUrl: http://localhost:11434
      model: qwen3:8b
```

> 未配置 LLM 参数时，程序会警告并自动以直通模式启动（纯直通使用无需 LLM）。
> ⚠️ **安全提示**：请勿将含真实密钥的 `config.yaml` 提交到公开仓库，建议加入 `.gitignore`。

### 3.1 用 `/setup` 引导配置大模型（推荐）

不用手改 YAML，在 REPL 中输入 `/setup` 即可交互式配置，支持**多个模型**并指定默认项，也可随时再次运行来追加 / 设默认 / 删除：

```
▶ > /setup
── LLM 配置向导 ──────────────────────────────
当前模型（* 为默认）：
  [1] * deepseek/deepseek-chat  baseUrl=https://api.deepseek.com/v1  apiKey=sk-****456
  1) 新增模型   2) 设为默认   3) 删除模型   4) 保存并退出   5) 放弃退出
```

- 按提示选择 provider（`deepseek` / `openai` / `ollama`），`baseUrl` 与 `model` 会给出推荐默认值，回车即可采用；
- `ollama` 为本地模型，**跳过 apiKey**；其余 provider 的 apiKey **输入不回显**；
- 保存后仅写回 `config.yaml` 的 **`llm` 节点**，且统一以多 Profile 写法（`defaultProfile` + `profiles`）输出，`shell` / `execution` / `safety` / `logging` 等手工配置原样保留，并自动备份为 `config.yaml.bak`；
- 保存后执行 `/mode smart` 启用语义模式，或重启生效（`/model` 可查看与切换模型）。

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
| `--config <路径>` | 指定配置文件（未指定时优先当前目录 `config.yaml`，否则取/生成于 `~/.smartcli/config.yaml`） |

> v4 起启动参数仅保留 `--config`：启动模式与只读开关等初始化项全部由 `config.yaml` 配置。

## 📦 构建 Native 可执行文件（GraalVM）

smartcli 支持用 GraalVM 的 `native-image` 编译为**无需 JDK 的原生二进制**（macOS / Linux / Windows），启动快、内存占用低。

### 前置条件（重要）

- **必须使用 GraalVM 21+**，不能用 Corretto / 系统 JDK。本项目依赖 JLine 3.26.3、langchain4j、Jackson、picocli 等，其自带的 native-image 元数据需要较新的 GraalVM；**GraalVM 17.0.7 会因不识别 `UnlockExperimentalVMOptions` 而构建失败**。
- GraalVM 21+ 的 `native-image` **已捆绑在发行包内，无需 `gu install`**。
- 源码字节码级别仍为 17（`pom.xml` 中 `maven.compiler.source/target=17`），由新版 GraalVM 编译成 native image 完全兼容（低字节码 + 高编译器，安全）。

### 切换工具链（以 jenv 为例）

```bash
jenv shell graalvm64-21.0.2          # 或 jenv local graalvm64-21.0.2
export JAVA_HOME="$HOME/.jenv/versions/graalvm64-21.0.2"
export PATH="$JAVA_HOME/bin:$PATH"
native-image --version               # 应输出版本信息，确认可用
```

> 未用 jenv 时，直接 `export JAVA_HOME=<GraalVM21 解压目录>/Contents/Home` 并加入 `PATH` 即可。

### 构建

```bash
mvn package -Pnative -DskipTests     # 跳过测试加速；如需测试去掉 -DskipTests
# 内存不足时：MAVEN_OPTS="-Xmx4g" mvn package -Pnative -DskipTests
```

- 构建由 `pom.xml` 中的 `native` profile 控制（`native-maven-plugin` 0.10.3）。
- `native` profile 下会自动**跳过 `maven-shade-plugin`**，避免与 native image 争夺 `package` 阶段。
- 产物：`target/pragmatic-ai-shell`（macOS 上为 Mach-O arm64 原生可执行文件）。

### 运行

```bash
./target/pragmatic-ai-shell --help    # 验证可用
./target/pragmatic-ai-shell --config config.yaml
```

> 用法与 `java -jar` 完全一致：启动参数、`config.yaml` 字段结构均不变，只是无需 JDK。

### 随 binary 分发的启动脚本（推荐，免 PATH）

项目提供 `bin/pragmatic-ai-shell.sh`，**基于脚本自身所在目录**查找二进制与配置，可把脚本与编译产物一起拷贝到任意目录/机器直接使用，无需加入 `PATH`：

```
某目录/
├── pragmatic-ai-shell        # 编译好的二进制（target/pragmatic-ai-shell）
├── pragmatic-ai-shell.sh     # bin/pragmatic-ai-shell.sh
└── config.yaml               # 可选，放同目录则自动加载
```

```bash
sh pragmatic-ai-shell.sh                 # 自动用同目录 config.yaml
./pragmatic-ai-shell.sh --config x.yaml  # 或显式指定
./pragmatic-ai-shell.sh --help
```

脚本逻辑：二进制与 config 默认都在脚本同目录；未传 `--config` 且同目录有 `config.yaml` 时自动带上；二进制缺失会给出提示。跨平台分发注意：二进制是 macOS arm64，换平台（Linux/Windows）需对应 GraalVM 重新构建。

### 已知坑（Native 专属）

- **picocli `AutoHelpMixin` 反射缺失**：picocli 4.7.6 未自带 native-image 元数据，`--help` 在 native 下会抛 `InitializationException`。已在 `src/main/resources/META-INF/native-image/reflect-config.json` 中注册 `SmartCliApplication` 与 `picocli.CommandLine$AutoHelpMixin` / `$HelpCommand` 修复。
- **SnakeYAML 配置反序列化反射缺失**：native 下 `AppConfig` 及 `config.model` 下 9 个配置类需可被反射实例化，否则报 `NoSuchMethodException: <init>()`。已在上述 `reflect-config.json` 中全部注册（构造器 / 字段 / 方法）。
- **Jackson 审计序列化反射缺失**：`AuditEntry`（record）未注册时，native 下审计日志会写成空对象 `{}`；已在 `reflect-config.json` 中注册。
- **JLine / Jackson / logback 反射与资源**：插件会自动收集大部分可达元数据，复杂动态路径若报错，在该目录下补充 `proxy-config.json` / `resource-config.json` 即可。
- **构建较慢且吃内存**：首次构建约 1–3 分钟，建议 `MAVEN_OPTS="-Xmx4g"`。

### 构建 Linux 版本（交叉构建，macOS 本机即可）

GraalVM `native-image` 不支持跨操作系统编译，macOS 上无法直接产出 Linux 二进制。项目提供基于 Docker 的一键脚本，在 Linux amd64 容器内完成构建（Apple Silicon 上走 QEMU 模拟）：

```bash
./bin/build-linux-amd64.sh
```

- **前置**：本机已安装 Docker（需支持 `--platform`，buildx 默认即可）；无需本机安装 GraalVM（容器内自带）。
- **产物**：`dist/pragmatic-ai-shell-linux-amd64`（ELF x86-64，glibc 动态链接，适配 Ubuntu / Debian / CentOS 等主流发行版；**不适配 Alpine**，musl 场景另行处理）。
- **耗时**：首次约 15–40 分钟（拉取基础镜像 + QEMU 模拟），改动代码后重建走层缓存会显著加快；建议给 Docker 分配 ≥ 8GB 内存。
- **验证**：
  ```bash
  file dist/pragmatic-ai-shell-linux-amd64   # 应显示 ELF 64-bit LSB executable, x86-64
  docker run --rm --platform linux/amd64 \
    -v "$(pwd)/dist":/app \
    ubuntu:22.04 /app/pragmatic-ai-shell-linux-amd64 --help
  ```

构建链路由 `Dockerfile.native`（多阶段：GraalVM 21 glibc 镜像构建 → `scratch` 导出二进制）与 `bin/build-linux-amd64.sh` 组成；脚本内所有 `docker build/create` 均显式指定 `--platform linux/amd64`，避免 Apple Silicon 默认产出 arm64 产物。

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
| `/model` | 查看多模型列表（✓ 标记激活项）；`/model switch <id>` 运行中切换、`/model check [id]` 健康检查 |
| `/context` | 查看当前多轮上下文（脱敏展示；与 ↑↓ 翻阅的命令历史不同，它是发给模型的历史） |
| `/clear` | 清空多轮上下文，后续对话不再引用此前轮次 |
| `/profile` | 查看当前环境指纹（OS / Shell / 已装工具列表）；`/profile refresh` 强制重新采集 |
| `/setup` | 引导配置大模型（可多次运行，支持新增 / 设为默认 / 删除，保存仅写回 `llm` 节点） |
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

### Tab 补全能力

在任意输入位置按 `Tab` 触发补全，由内置 `CompleterRouter` 按当前词位置分派：

| 场景 | 示例 | 补全结果 |
| --- | --- | --- |
| 内置命令（首词以 `/` 开头） | `/set` | `/setup` 等全名 |
| 内置命令参数（第 2 词） | `/mode `、`/model ` | `smart`、`direct` / `switch`、`check` |
| 系统命令名（首词普通前缀） | `gi` | 扫描 `PATH` 得 `git`（缓存加速） |
| 子命令（首词在规格表，第 2 词） | `git ` | `commit`、`push` 等 |
| 选项（词以 `-` 开头） | `docker run --` | `--rm`、`--tty` 等 |
| 文件路径 | `./src/`、绝对路径等 | 目录/文件候选（还原 `..`/`.` 导航与隐藏文件规则） |

> 规格表覆盖 `git`/`docker`/`kubectl`/`npm`/`mvn`/`systemctl`/`brew`；其余命令自动回退到路径补全。变量补全（`$VAR`）为二期规划。

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
  splash:
    enabled: true           # 启动界面总开关；非 TTY 自动降级为纯文本
llm:
  # 支持两种写法（二选一）：
  # A. 单模型（向后兼容）：直接写以下顶层字段，自动合成 id=(inline) 的隐式 Profile
  provider: deepseek        # deepseek / openai / ollama
  baseUrl: https://api.deepseek.com/v1
  model: deepseek-chat      # 模型名
  apiKey: sk-xxx            # API 密钥（语义模式必填，缺失自动降级直通；ollama 免填）
  temperature: 0.0          # 采样温度 [0, 2]
  timeoutSeconds: 60        # LLM 调用超时（秒）
  showProgress: true        # 等待动画开关
  # B. 多模型：声明 defaultProfile + profiles（启用后以上顶层单模型字段被忽略）
  # defaultProfile: deep    # 缺省激活的 Profile id
  # profiles:               # 每项含 id/provider/baseUrl/model/temperature/apiKey/timeoutSeconds
  #   - id: deep
  #     provider: deepseek
  #     baseUrl: https://api.deepseek.com/v1
  #     model: deepseek-chat
  #     apiKey: sk-xxx
  context:                  # 多轮对话上下文（全局共享，不下沉到 Profile）
    enabled: true           # 默认开启；false 时每次调用独立（v2 无上下文行为）
    maxTurns: 10            # 保留最近轮数（≥ 1）
    maxResultChars: 2000    # 单轮命令结果摘要上限字符数（≥ 100），超出保留尾部；同时作为命令输出捕获上限
  profile:                  # 环境指纹（环境感知）
    enabled: true           # 默认开启；false 时不采集、不注入环境信息
    toolWhitelist: []       # 留空使用内置默认探测清单
    toolProbeTimeoutMs: 200 # 单工具探测超时（毫秒）
execution:
  defaultTimeoutSeconds: 60 # 命令执行超时
  workDir: .                # 默认工作目录（~ 展开，相对进程启动目录）
  readOnly: false           # 只读模式
safety:
  strictMode: false         # 严格模式：所有命令都需确认
  confirmDestructive: true  # 危险命令二次确认
  blockPrivateAddresses: true # 拦截内网地址探测
  sudoPolicy: confirm       # sudo 命令：confirm=提权确认后放行（默认）| reject=拒绝 | allow=放行
nlu:
  executionJudgment: false  # LLM 是否做执行判定（见下方说明，默认宽松）
  toolConstraint: reference # strict=只用已探测工具 | reference=工具清单仅参考（默认）
logging:
  auditEnabled: true        # 审计日志开关
  auditPath: ~/.smartcli/audit.log  # 审计路径（~ 展开，相对进程启动目录）
```

> 路径字段（`workDir`/`auditPath`）支持 `~` 展开，相对路径以**进程启动目录**为基准。
> 配置中的未知字段会在启动时告警并忽略；`version` 与程序支持版本不一致时告警。

#### LLM 职责边界（`nlu.*`）

语义模式下 LLM 默认身兼两职：**翻译**（自然语言 → 命令）与**审查**（判断能否/是否该执行）。
零配置默认采用**宽松模式**——LLM 只做翻译，不做可行性/安全审查，命令交由你确认后执行。
这避免了一个常见误伤：例如 `nginx` 不在工具探测清单时，严格模式会让模型返回 `IMPOSSIBLE`，
**命令根本不生成**，你连"它想执行什么"都看不到。

| 配置 | 默认 | 说明 |
|------|------|------|
| `executionJudgment` | `false` | `false`（宽松，默认）：**模型只翻译，不判定能否执行**，命令一律展示给你确认。<br>`true`（严格）：模型可因"有风险/不可行"拒绝输出命令 |
| `toolConstraint` | `reference` | `reference`（宽松，默认）：工具清单仅参考，不禁止未列出/未安装的工具。<br>`strict`：只能使用环境信息中已安装的工具 |
| `sudoPolicy` | `confirm` | `confirm`（默认）：提权命令（`systemctl` 等）确认后放行。<br>`reject`：拒绝所有 sudo 命令。<br>`allow`：sudo 命令直接放行 |

**注意**：即使宽松模式下，模型仍保留一个拒绝出口——**仅当请求无法转换为任何 shell 命令时**
（如"帮我写首诗"）才返回失败。命令可能失败、服务可能不存在、权限可能不足，这些**都不再是拒绝的理由**，
因为判定权已归你。

**安全底线不因宽松默认而削弱**：
- 语义模式下每条命令都先打印 `➜ 建议执行: <命令>` 并要求确认（输入 `n` 跳过，回车执行）；
- `SafetyFilterChain`（高危黑名单 / 敏感地址 / 破坏性确认）作为硬控制**始终生效**；
- `sudoPolicy` 即使设为 `allow`，`sudo rm -rf /` 等命令仍被高危黑名单拦截。

若你偏好更严格的默认（LLM 先自行把关），可显式配置：

```yaml
nlu:
  executionJudgment: true
  toolConstraint: strict
safety:
  sudoPolicy: reject
```

### 审计日志示例（`~/.smartcli/audit.log`）

```json
{"ts":"2026-08-30T03:00:00.123456Z","source":"LLM","input":"找出占用8080端口的进程","command":"lsof -i :8080","exitCode":0,"durationMs":120,"model":"deepseek/deepseek-chat"}
```

> `model` 为可选字段：仅语义来源（LLM）命令记录当前激活模型（`provider/model`），直通/状态命令不输出；`source` 取值 `LLM` / `USER`。

## 🏗️ 架构一览

```
用户输入 ──▶ 路由（/ 内置 │ ! 直通 │ 语义）
                │
                ├─ 会话状态命令分发器（cd/pwd/pushd/popd/dirs/export/unset/source/alias 提示）
                │      └─ 进程内模拟：更新 currentDir / 目录栈 / 环境覆盖表（注入后续子进程）
                │
语义路径：ModelRegistry（多 Profile 管理，懒加载）──▶ LLM 生成命令 ──▶ 安全过滤链（模型自审→黑名单→风险分级→内网拦截）
                │                      │
                ▼                 需确认？──▶ 二次确认
           命令执行器（ProcessBuilder 独立子进程，交互命令 inheritIO）
                │
                ▼
           审计日志落盘（单行 JSON，含来源/模型/退出码/耗时）
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
| 1 | 配置错误（`--config` 指定路径不存在 / 文件不可读 / 解析失败 / 校验不通过） |
| 2 | 命令行参数错误（如传入了 v4 已移除的 `--mode`） |

### 8. 交互式命令与超时

- `ssh`/`vim`/`top` 等清单内交互命令，以及 `scp`/`sftp`/`rsync` 文件传输命令，走 `inheritIO` 直连终端，**不设超时**（交互会话由用户自行退出；传输挂死时 Ctrl+C 中断）。
  文件传输命令依赖直连终端渲染实时进度条，且密码认证可交互输入；
- 清单外命令如果本身需要 TTY 交互，可能行为异常（stdin 非终端）；
- **读取 stdin 的命令立即结束**：`cat`、`sort`、`grep <pattern>`、`python3`（均不带文件参数）这类等待键盘输入的命令，
  启动时即收到 EOF 并**立即返回**（行为等同于在原生终端按 `Ctrl+D`），不会阻塞到超时；
  退出码保留命令自身语义（如 `grep` 无匹配返回 1）。命令内部的管道（`a | b`）不受影响；
- **输出捕获上限由配置驱动**：`llm.context.maxResultChars` 同时决定命令输出的捕获上限（超出保留尾部并加截断标记）；
  该项缺失或 ≤ 0 时回退为 2000；
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

**Q：配置了多个模型，运行中怎么切换？**
A：`/model` 查看列表（✓ 为当前激活项），`/model switch <id>` 即可运行中切换（无需重启），`/model check [id]` 可先做健康检查。新模型可随时用 `/setup` 追加（仅写回 `llm` 节点，自动备份原文件）。

**Q：`ssh`/`vim` 这类交互命令能用吗？**
A：可以。交互类命令（ssh/telnet/vim/top 等）自动走 `inheritIO` 直连终端。

**Q：模型生成的危险命令会被执行吗？**
A：三重防护——模型侧返回 `UNSAFE` 拒答；黑名单与风险分类器拦截 `rm -rf /` 等高危命令；危险操作强制人工确认。

**Q：多轮上下文会保存什么？会不会泄露密钥？**
A：保存最近 10 轮的用户输入、命令与执行结果摘要（含直通 `!` 命令）。入史前 `sk-` 密钥与 `token=`/`password=` 等凭据自动打码（`sk-****` / `token=****`），`/context` 展示与发送给模型的都是打码后的值；历史仅存在当前会话内存，退出即清空。
