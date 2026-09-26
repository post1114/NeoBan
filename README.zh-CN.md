# NeoBan

[English](README.md) | [简体中文](README.zh-CN.md)

支持 **Paper 1.8.8** 与 **Paper 26.3** 双版本的 Minecraft 封禁 / 禁言 / 踢出插件，内置申诉系统。同一套代码构建出两个独立的 jar，分别对应一个服务端版本。

被封禁的玩家加入后会被传送至专属的**申诉世界**（插件自动生成的虚空世界，或你指定的现有世界）并被完全限制，只能使用白名单命令——其中最重要的是 `/appeal <理由>`。

## 功能特性

- 永久 / 临时**封禁**（`/ban`、`/tempban`）与**禁言**（`/mute`、`/tempmute`）
- **IP 封禁**（`/ipban`、`/iptempban`、`/ipunban`）：被封 IP 在登录时直接拒绝，并显示原因与时长
- **自动 IP 封禁**：同一 IP 上达到 N 个（默认 7）被封玩家时，自动封禁该 IP 一段时间（默认 `2d`）
- **踢出**玩家（`/kick`）
- **申诉系统**：玩家填写理由提交申诉，管理员通过 `/appeal list|view|accept|deny` 审核
- 被封禁玩家加入后被限制在可配置的**申诉世界**，原位置自动保存、解封后恢复
- 申诉世界内全面限制：禁止破坏/放置方块、交互、打开背包、丢弃物品、受到或造成伤害、聊天，除白名单外的命令，以及离开该世界
- 视野同步：申诉世界内的玩家与正常玩家互相不可见
- 被禁言玩家无法聊天，但仍可提交申诉（不会被传送）
- 申诉**次数上限**与**冷却时间**可配置，按单次处罚或玩家终身计数
- 具有 `neoban.appeal.admin` 权限的管理员上线时自动收到待处理申诉（离线期间的申诉不会遗漏）
- 申诉通过/驳回时会通知玩家本人（即使决定时玩家不在线）
- 时长格式如 `7d`、`1d12h`、`45m`、`30s`，支持组合
- 存储后端：**YAML**（默认，零外部依赖）或 **MySQL/MariaDB**；首次成功连接 MySQL 时会自动导入现有 YAML 数据
- 所有消息均在 `config.yml` 中，可完全自定义（默认英文）
- UUID 迁移：以玩家名记录的处罚会在玩家首次加入时自动迁移为 UUID

## 运行要求

| 服务端 | Jar | Java |
|---|---|---|
| Paper 1.8.8 | `NeoBan-1.8.8.jar` | Java 8 |
| Paper 26.3 | `NeoBan-26.3.jar` | Java 25+（Minecraft 26.1 及以上在 Java 25 以下无法启动） |

## 安装

1. 构建（见下文）或下载与服务端版本匹配的 jar。
2. 放入服务端的 `plugins/` 目录。
3. 启动服务器——首次运行会生成 `plugins/NeoBan/config.yml`。

## 从源码构建

构建分为两遍：旧版 jar 必须以 Spigot 1.8.8（Java 8）编译，现代版 jar 以 Paper 26.3（Java 25）编译。

环境要求：Maven 3.9+、JDK 8、JDK 25。

```powershell
# 第一遍 —— neoban-common + 旧版 jar  （JDK 8）
$env:JAVA_HOME = 'C:\path\to\jdk8'
mvn clean install -pl neoban-common,neoban-v1_8_8

# 第二遍 —— 兼容性检查 + 现代版 jar  （JDK 25）
$env:JAVA_HOME = 'C:\path\to\jdk25'
mvn clean package -pl neoban-compat-check,neoban-v26_3
```

Linux/macOS 请改用 `export JAVA_HOME=...`。务必使用 `clean` 构建。

产物：

- `neoban-v1_8_8/target/NeoBan-1.8.8.jar`
- `neoban-v26_3/target/NeoBan-26.3.jar`

模块结构：

| 模块 | 用途 |
|---|---|
| `neoban-common` | 共享逻辑（以 `-source/-target 8` 编译） |
| `neoban-v1_8_8` | Paper 1.8.8 适配层、旧版聊天/拾取监听器、虚空世界生成器 |
| `neoban-v26_3` | Paper 26.3 适配层、Adventure 聊天监听器、Tab 补全过滤、虚空世界生成器 |
| `neoban-compat-check` | 用 Paper 26.3 编译 common 源码，防止误用仅新版本可用的 API |

## 命令

| 命令 | 权限 | 说明 |
|---|---|---|
| `/ban <玩家> [理由]` | `neoban.ban` | 永久封禁 |
| `/tempban <玩家> <时长> [理由]` | `neoban.tempban` | 临时封禁，如 `7d`、`1d12h`、`45m` |
| `/unban <玩家>` | `neoban.unban` | 解除封禁 |
| `/mute <玩家> [理由]` | `neoban.mute` | 永久禁言 |
| `/tempmute <玩家> <时长> [理由]` | `neoban.tempmute` | 临时禁言 |
| `/unmute <玩家>` | `neoban.unmute` | 解除禁言 |
| `/kick <玩家> [理由]` | `neoban.kick` | 踢出玩家 |
| `/ipban <ip|玩家> [理由]` | `neoban.ipban` | 永久封禁 IP（登录时拒绝） |
| `/iptempban <ip|玩家> <时长> [理由]` | `neoban.iptempban` | 临时封禁 IP |
| `/ipunban <ip>` | `neoban.ipunban` | 解除 IP 封禁 |
| `/appeal` | — | 查看自己的申诉状态 |
| `/appeal <理由>` | — | 针对当前处罚提交申诉 |
| `/appeal list` | `neoban.appeal.admin` | 列出待处理申诉 |
| `/appeal view <id>` | `neoban.appeal.admin` | 查看申诉详情 |
| `/appeal accept <id> [备注]` | `neoban.appeal.admin` | 通过申诉（解除处罚） |
| `/appeal deny <id> [备注]` | `neoban.appeal.admin` | 驳回申诉 |
| `/neoban reload` | `neoban.admin` | 重载配置 |
| `/neoban info` | `neoban.admin` | 生效封禁 / 禁言 / 待处理申诉数量 |
| `/neoban resetappeals <玩家>` | `neoban.admin` | 重置玩家的申诉次数与冷却 |

## 权限

| 权限 | 默认 | 说明 |
|---|---|---|
| `neoban.ban` / `neoban.tempban` / `neoban.unban` | `op` | 封禁相关命令 |
| `neoban.mute` / `neoban.tempmute` / `neoban.unmute` | `op` | 禁言相关命令 |
| `neoban.kick` | `op` | 踢出 |
| `neoban.ipban` / `neoban.iptempban` / `neoban.ipunban` | `op` | IP 封禁相关命令 |
| `neoban.ipban.bypass` | `op` | 不会被 IP 封禁（含自动 IP 封禁）踢出，且自动封禁时豁免 |
| `neoban.appeal.admin` | `op` | 审核申诉 / 接收待处理申诉通知 |
| `neoban.admin` | `op` | `/neoban` 管理命令 |
| `neoban.bypass` | `op` | 免受申诉世界限制 |

## 申诉流程

1. 被封禁的玩家加入后被传送至申诉世界，原位置自动保存。
2. 玩家执行 `/appeal <理由>`——受可配置的次数上限与冷却限制。
3. 在线且拥有 `neoban.appeal.admin` 的管理员立即收到通知；离线的管理员会在下次上线时收到全部待处理申诉。
4. 管理员处理：`/appeal accept <id>` 解除封禁并通知玩家（玩家随后回到保存的位置）；`/appeal deny <id>` 保留处罚，并附备注通知玩家。

## 配置（节选）

```yaml
storage:
  type: YAML                # YAML（默认）| MYSQL
  mysql:
    host: localhost
    port: 3306
    database: neoban
    user: root
    password: ""
    table-prefix: neoban_

ipban:
  auto:
    enabled: true
    min-banned-players: 7   # 同一 IP 上达到多少个被封玩家后自动封禁该 IP
    duration: 2d            # 自动 IP 封禁的持续时长
    reason: "Automatic IP ban: too many banned players on this IP"

appeal:
  max-appeals: 5              # 每次处罚（或终身）允许的申诉次数
  cooldown-minutes: 60        # 两次申诉之间的冷却（分钟）
  count-scope: PER_PUNISHMENT # PER_PUNISHMENT（按处罚计）| LIFETIME（按终身计）
  allowed-commands:
    - "/appeal"
  world:
    type: VOID                # VOID（自动生成）| CUSTOM（使用现有世界）
    void-name: NeoBan_AppealVoid
    custom-name: appeal_world

messages:
  prefix: "&8[&cNeoBan&8] &7"
  ban-success: "&a已封禁 &e{player}&a，时长: &f{duration}&a，原因: &f{reason}"
  # ... 所有消息均可在此修改或翻译
```

## 存储

`storage.type` 选择存储后端：

- `YAML`（默认）：全部数据保存在 `plugins/NeoBan/` 下的 UTF-8 YAML 文件中。
- `MYSQL`：数据保存在 MySQL/MariaDB 表中。数据库为空时首次启动会自动导入现有 YAML 文件（仅一次，单事务）。修改 `storage.type` 需要重启服务器——`/neoban reload` 只重载设置与消息。
- MySQL 驱动已内置在插件中，无需额外 jar。

## 数据文件

使用 YAML 存储时，数据位于 `plugins/NeoBan/` 下的 UTF-8 YAML 文件：

| 文件 | 内容 |
|---|---|
| `config.yml` | 设置与消息 |
| `bans.yml` | 封禁记录 |
| `mutes.yml` | 禁言记录 |
| `appeals.yml` | 申诉与玩家计数器 |
| `locations.yml` | 被封禁玩家保存的位置 |
| `ipbans.yml` | IP 封禁记录 |
| `player-ips.yml` | 每个玩家最近已知的 IP（用于对离线玩家执行 IP 封禁） |

## 许可证

[GPL-3.0](LICENSE)
