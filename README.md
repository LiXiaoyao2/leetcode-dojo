<div align="center">

# dojo

**卡住的时候，给你下一档提示 —— 不是答案，也不是鸡汤。**

在 IDEA 里刷 LeetCode：题面、代码、测试、提示，都在一个 Maven 工程里。

[![Java](https://img.shields.io/badge/Java-21-ED8B00?logo=openjdk&logoColor=white)](https://openjdk.org/projects/jdk/21/)
[![Maven](https://img.shields.io/badge/Maven-3.9%2B-C71A36?logo=apachemaven&logoColor=white)](https://maven.apache.org/)
[![JUnit](https://img.shields.io/badge/JUnit-5.10-25A162?logo=junit5&logoColor=white)](https://junit.org/junit5/)
[![Tests](https://img.shields.io/badge/tests-106%20passing-2ea44f)](https://github.com/LiXiaoyao2/leetcode-dojo)
[![Runtime deps](https://img.shields.io/badge/runtime%20deps-0-6f42c1)](https://github.com/LiXiaoyao2/leetcode-dojo)
[![License](https://img.shields.io/badge/license-MIT-blue)](LICENSE)

</div>

---

```
$ dojo new 146
→ 从 leetcode.cn 拉取 146. LRU 缓存 …
  146  LRU 缓存  ·  中等  ·  设计 / 哈希表 / 链表 / 双向链表
✓ 已就绪：0146-lru-cache
  新建  problems/0146-lru-cache/problem.md
  新建  solutions/src/main/java/p0146_lru_cache/Solution.java
  新建  solutions/src/test/java/p0146_lru_cache/LRUCacheTest.java

$ dojo test
→ mvn test -Dtest=LRUCacheTest …
✗ 没通过 · 0.8 s
  LRUCacheTest.officialExample1:23 » 期望 [null, null, 1, null, -1]，实际 [null, null, -1, null, -1]

$ dojo hint
→ 请求 L1 · 澄清（deepseek-v4.1-flash）…

L1 · 澄清

你说 put(1, 1) 之后 get(1) 拿到了 -1。先别管淘汰策略——
你的 put 是在 get 之前就动了链表，还是在之后？
容量为 1 时，put 一个不存在的 key，中间会不会先发生一次淘汰？

12.3 s · deepseek-v4.1-flash

$ dojo hint 5
→ 请求 L5 · 题解 …
（完整 Java + 正确性 + 复杂度 + 一个变体）
```

## 目录

- [为什么会有这个东西](#为什么会有这个东西)
- [它和别的刷题插件有什么不一样](#它和别的刷题插件有什么不一样)
- [五档阶梯](#五档阶梯)
- [一次提示，模型到底看到了什么](#一次提示模型到底看到了什么)
- [安装](#安装)
- [60 秒上手](#60-秒上手)
- [命令](#命令)
- [官方样例是怎么变成 JUnit 的](#官方样例是怎么变成-junit-的)
- [评测：提示真的更好吗](#评测提示真的更好吗)
- [架构](#架构)
- [配置](#配置)
- [设计取舍：刻意不做的事](#设计取舍刻意不做的事)
- [FAQ](#faq)
- [已知限制](#已知限制)

## 为什么会有这个东西

上一版把力气花在了外围：Web 页面、数据库、爬虫、星级、分区 hash、设置页。提示本身反而是「两次模型调用 + 纪律校验 + 不合格就换一段罐头文案」——卡住的人拿到的经常是一段正确但没用的废话。

这一版只有一条主线：**把提示做准。** 其余全部让路。

- **一次调用。** 模型同时看到题面、你磁盘上的真实代码（带行号）、最近一次测试输出、已经给过的提示，一次给出下一档。没有「诊断器 → 引导器」的二次转述损耗。
- **阶梯是真的阶梯。** L1 只问边界，L2 只指方向，L3 给一个洞察，L4 给步骤骨架，L5 才是完整题解。想要答案就明说 `dojo hint 5`，但默认永远只推进一格。
- **不重复。** 已给的提示原样进上下文，模型被要求「一句都不许重复，且必须承接上一档」。
- **失败不装死。** 越界给了代码、或者被长度截断，会重问一次；仍然不行就原文照发 + 一行警示。**绝不**偷偷换成罐头文案。
- **测试必须能跑。** 生成器自己保证「要么给一份能编译的测试，要么明确说为什么给不了」；坏文件会被移出模块，不会一道题拖垮所有题。

## 它和别的刷题插件有什么不一样

|  | 常见做法 | dojo |
| --- | --- | --- |
| 提示形态 | 一问一答，直接给思路或代码 | 五档阶梯，每次只给下一档，越级要显式要 |
| 上下文 | 题面 + 你在网页里粘的代码 | 题面 + **磁盘上的真实文件**（带行号）+ 最近一次 JUnit 输出 |
| 重复提问 | 每次重新生成，内容漂移 | 已给提示进上下文，禁止复述，必须承接 |
| 失败处理 | 生成失败就报错/给模板话术 | 重问 → 照发 + 警示（保留真实信息） |
| 写题环境 | 网页 / 插件面板 / 独立编辑器 | 你自己的 IDEA + 一个普通 Maven 模块 |
| 数据 | 云端账号、数据库 | 纯文件：`problem.md`、`hints.md`、`done` |

## 五档阶梯

| 档 | 名字 | 给什么 | 不给什么 | 篇幅 |
| :--: | --- | --- | --- | --- |
| **L1** | 澄清 | 1–2 个能一句话回答的问题：边界、返回约定、你的代码与题面的偏差 | 任何方向暗示 | ≤120 字 |
| **L2** | 方向 | 点名模式（双指针 / 单调栈 / 并查集…），解释它为什么适配这组输入的形状 | 具体步骤 | ≤150 字 |
| **L3** | 洞察 | 打破僵局的那**一个**观察，可点名变量与行号 | 步骤清单 | ≤220 字 |
| **L4** | 骨架 | 3–6 步 + 复杂度 + 最易错点；伪代码可以 | 可编译的 Java | ≤350 字 |
| **L5** | 题解 | 完整 Java + 思路 + 正确性 + 复杂度 + 一个变体 | —— | 不限 |

档位状态不存数据库：数 `problems/<id>/hints.md` 里有多少个 `## L3 · 洞察 · 2026-10-18 01:12` 小节，就知道你走到哪了。删掉最后一节就等于退回上一档，手改也认。

### 卡住也分种类

阶梯是给「思路没到那一步」用的。另一些时刻，你卡的不是思路，是别的东西——这时再走阶梯反而浪费一档：

| 你的情况 | dojo 自动切换 | 给什么 |
| --- | --- | --- |
| 编译不过 | **编译修复** | 直接点出哪一行、错在哪、最小改法。不写进 `hints.md`，不消耗档位 |
| 抛异常 | **定位** | 从栈帧落到你的代码行，解释为什么抛、该检查什么 |
| 断言挂了 | **反例** | 先摆失败输入 / 期望 / 实际，再问「从哪一步开始偏」。这一档算 L(n)，因为它确实给了信息 |

判断依据是最近一次测试输出（`dojo test` 或 hint 自动跑的那次）。`dojo hint` 自己看，不用你声明。

另外它会拦「连点」：代码一个字没动、也没有新的测试结果时，`dojo hint` 会拒绝推进档位——先按上一档改一次，或 `--force` 强行要。

> 规则全部写在 `tool/src/main/java/dojo/Ladder.java` 的 `SYSTEM` 里。想换教练的语气、篇幅、严格程度，改那一段，`dojo prompt 3` 立刻能看效果，不用重新编译整个工具。

## 一次提示，模型到底看到了什么

```
【题目】            题面（去掉图片噪声，保留样例与约束，最多 9000 字符）
【我当前的代码】     磁盘上的真实文件 + 行号（中间截断，保留头尾）
【上次之后你改了什么】 与上一次要提示时的代码快照做 diff（- 旧行 / + 新行）
【最近一次测试输出】  上一次测试的摘要（Maven 噪声已过滤；代码改过会先自动重跑）
【已经给过的提示】    L1..Ln 原文（每条截断到 900 字符）
【这一档】           L3 · 洞察 —— 该档的指令与禁令
【我自己的卡点】      可选：dojo hint "我卡在边界处理"
```

外加一段 `SYSTEM`：中文、直给、无开场白、不复述题面、不灌鸡汤、禁止重复、引用行号必须与标注一致、测试全绿时把这一档用在更优解而不是让你重跑。

最后一行 diff 是它不把同一句话说第二遍的关键：上一档之后你动了哪几行，它看得见，所以能先判「你改对了没有」，再决定这一档讲什么。快照在 `.dojo/snapshots/`，不进 git，只存最后一次要提示时的代码；不想要这个行为可以 `dojo hint --no-run` 跳过自动测试，`--no-save` 则不写档位也不更新快照。

**为什么要给你看这些？** 因为 `dojo prompt 3` 会把上面整段原样打印出来，不发请求。提示词不是黑盒，调它不需要读工具源码。

## 安装

需要：**JDK 21**、**Maven 3.9+**、一个 Anthropic / OpenAI 兼容的模型网关。不需要数据库、账号或常驻服务。

```bash
git clone https://github.com/LiXiaoyao2/leetcode-dojo.git ~/code/leetcode-dojo
ln -sfn ~/code/leetcode-dojo/bin/dojo ~/.local/bin/dojo   # 或把 bin/ 加进 PATH
dojo doctor                                               # java / mvn / PATH / 网关 ping
```

`bin/dojo` 会先解析自己的符号链接，再定位仓库根（`DOJO_HOME`，或从当前目录往上找），最后按需用 Maven 把工具编成单个 jar（首次几秒，之后走缓存）。所以软链进 `~/.local/bin` 之后，在 `solutions/...` 或任意目录都能直接敲 `dojo`。

`doctor` 的输出长这样：

```
java    21.0.12.1  (Oracle Corporation)
mvn     3.9.16
仓库    /home/lin/code/leetcode-dojo
dojo    /home/lin/.local/bin/dojo（指向本仓库）

配置来源（按优先级，取第一个有 base URL 的）
  · …（四个来源逐个列出，第一个有值的就是它）
使用    ~/.claude/settings.json
base    https://your-gateway.example.com/anthropic
端点    https://your-gateway.example.com/anthropic/v1/messages
模型    deepseek-v4.1-flash
协议    anthropic
token   …a1b2（115 位）

→ ping …
✓ ping 成功 · 1.8 s · OK
```

## 60 秒上手

```bash
dojo new 1              # 也认 lru-cache、1、leetcode.cn 的完整链接
dojo new 1-100          # 想一次铺开一个区间也行（最多 300 道）
```

生成三样东西：

```
problems/0001-two-sum/problem.md                         题面（含样例，可直接读）
solutions/src/main/java/p0001_two_sum/Solution.java      空解法（方法体是 TODO throw，模块恒可编译）
solutions/src/test/java/p0001_two_sum/TwoSumTest.java    官方样例的 JUnit 5 测试
```

然后在 IDEA 里只改一个文件，卡住时：

```bash
dojo test      # 跑官方样例（不跑也行：hint 发现代码比上次测试新，会先替你跑一遍）
dojo hint      # L1 → L2 → L3 → L4 → L5，每次只推进一格
dojo done      # 打勾：problems/0001-two-sum/done
```

`dojo status` 随时看战况：每道题拿了哪几档、哪些已完成。

### 不想切出 IDEA

把 `dojo hint --open` 绑成 External Tool + 快捷键后，光标停在 `Solution.java` 里按一次键：提示直接进 Run 窗口，并同时打开 `problems/<id>/last-hint.md`。逐步配置见 [docs/idea.md](docs/idea.md)。

```text
Ctrl+Alt+Shift+H  →  dojo hint --open
Ctrl+Alt+Shift+T  →  dojo test
```

## 命令

| 命令 | 作用 |
| --- | --- |
| `dojo new <slug\|题号\|链接>` | 拉题面 + 解法模板 + 官方样例测试 |
| `dojo new <a>-<b>` | 批量拉 `a` 到 `b`（含两端，一次最多 300 道） |
| `dojo hint [1-5] [卡点]` | 要下一档提示；代码比上次测试新会先自动跑一遍测试；同时带上「上次之后你改了什么」的 diff；`--open` 额外写并打开 `last-hint.md` |
| `dojo test` | 跑这道题的 JUnit 测试，摘要写入 `.dojo/last-test.log` |
| `dojo stress` | 随机对拍：模型写一份暴力解并先过官方样例，再用几百组随机输入和你的解比结果 |
| `dojo status` | 所有题的目标、最高档位、是否完成 |
| `dojo done` | 标记完成 |
| `dojo prompt [1-5]` | 只打印将要发给模型的完整请求，不发请求 |
| `dojo doctor` | 环境、PATH、配置来源、网关 ping |
| `dojo clean` | 清构建产物；带题号 / `--all` 把题目移进系统回收站 |
| `dojo eval` | 开发用：跑提示质量评测（20 题 × 3 架构 × 5 档） |
| `dojo help` | 用法 |

通用选项：`--problem/-p <id|slug|题号>` 指定题目（默认按当前目录 → 最近修改的解法推断）；`--note/-n <text>` 附卡点；`--no-save` 不写 `hints.md`；`--debug` 打印请求；`new` 另有 `--force`、`--no-test`、`--no-verify`、`--unordered`。

**批量拉题**：逐题拉、间隔 150 ms、已存在跳过、单题失败不中断；全部写完后**统一**跑一次 `mvn test-compile`（而不是 100 次 Maven），编不过的生成测试精确删除并报题号。

**清理**：`dojo clean` 删 `target/` 与 `last-test.log`；`dojo clean 146` 只把这一题的题面/解法/测试移进系统回收站（`mavis-trash` / `gio trash` / `trash-put`，本机有什么用什么），删错了从文件管理器拖回来即可。

## 随机对拍：样例过了，不代表对了

官方样例是别人的用例，专挑你没想到的输入才有用。`dojo stress` 干的就是这件事：

```bash
dojo stress                # 300 组随机输入，和暴力解比结果
dojo stress --cases 1000   # 多想一点
dojo stress --seed 42      # 换一批种子
dojo stress --refresh      # 之前的暴力解不放心，让模型重写
```

流程里有两道闸：

1. **暴力解必须先过官方样例**。模型写的 `Brute` 一旦跑不绿官方测试，直接丢弃——不能拿一个错的对照来判你的解错。
2. **失败信息带 seed**。`seed=18 root=[-1, null, 7, -1] expected=false actual=true` 这样的输出，把 `dojo stress --cases 1 --seed 18` 敲一遍就能原地复现。

输入在测试运行期按种子生成，不是把几百组字面量塞进文件，所以生成器只按签名挑类型：`int[]` 随机长度，`target` 从数组里取两个元素相加，链表用 `ListNodes.of`，树用 `TreeNodes.of`，字符网格随机 0/1。

> 已知边界：返回 `void`（原地修改）、设计题、`List<...>` 返回值的题还不支持；答案本身有多种合法形态的题（比如 two-sum 的多个答案对）会尽量把输入限制成唯一解，仍可能偶发假警报——看到不一致先确认两边是不是都成立。

## 官方样例是怎么变成 JUnit 的

`new` 时用题目的 `metaData` + `jsonExampleTestcases` 做**确定性**转换，不经过模型：

- 类型覆盖 `integer`、`integer[]`、`integer[][]`、`string`、`string[]`、`string[][]`、`character`、`double`、`boolean`、`long`、`list<...>`、`ListNode`、`TreeNode`、`void`（断言被修改的入参）
- 期望输出**按输入值配对**，不按序号：接口常常只保留 3 个用例而题面写了 5 个，硬对齐就会把错的期望值写进测试
- 设计题按操作序列生成 `obj.method(...)`，`void` 操作只调用不断言
- 题面写「顺序不重要 / 任意顺序」时自动按无序比较（`int[]` 排序、`List<List<...>>` 归一化），也可手动 `--unordered`
- 模板里被注释掉的类型定义（克隆图的 `Node`、随机链表的 `Node`）会还原成真代码；`ListNode` / `TreeNode` 复用 `kit` 里的版本
- **签名先对账**：模板签名与 `metaData` 参数个数不符、或出现生成器不认识的类型（`Node`、`ListNode[]`），直接不生成，并说明「这类题先手写测试」——宁可少一个测试文件，也不写一个编译不过的
- 单例转不动就跳过这一例并注释原因；全都转不动就跳过整个文件并打印原因

生成完立刻 `mvn test-compile` 校验：测试自己编不过 → 移出模块；解法编不过 → 保留测试并提示先修解法；挡编译的是**别的题** → 点名 + 给 `dojo clean <题号>`。

## 评测：提示真的更好吗

「感觉提示还行」不算证据。仓库里有 `eval/`，跑同一道题、同一段卡住代码、同一份真实失败输出，
对比三种架构：**现在这套阶梯**、**朴素单次调用**、**先诊断再引导**（旧项目那套）。

| 看什么 | 怎么算 |
| --- | --- |
| 泄底率 | L1–L4 里出现可编译 Java 的比例 |
| 重复率 | 和更早档位字符 3-gram 相似度 ≥ 0.35 |
| 超长率 | 超过该档字数上限 20% |
| L5 跑绿 | 把第 5 档给的 Java 抠出来，跑同一份官方样例测试 |
| 人工/判官分 | 逐档 1–5 分，判官看不到架构名 |

第一轮结果（20 题 × 3 架构，2026-10）：

- **篇幅纪律**：超长率 阶梯 **16%**、朴素单次 77%、两次调用 68%——阶梯把「一开口就写小作文」按住了。
- **成本**：阶梯 6590 tokens/题、9.6 s；两次调用 10380、16.3 s（多 58% tokens、慢 70%）。
- **泄底与 L5**：三套都是泄底 0%、L5 跑绿 20/20——这两项已经到天花板，区分不出来。
- **主观分**：4.29 / 4.27 / 4.15；判官对同一答案重测就能差 2 分，这点差距不算结论。

诚实的说法：在「每档要求已经写清楚」的前提下，单次调用已经很强；阶梯赢在篇幅和「不会越档」。
完整表在 [`eval/RESULTS.md`](eval/RESULTS.md)，方法、坑与局限在 [`eval/README.md`](eval/README.md)，
原始数据 [`eval/raw.json`](eval/raw.json)。想自己复现：

```bash
bin/dojo eval --cases 3   # 3 题试水
bin/dojo eval             # 全量
```

## 架构

```
dojo（单 jar · Java 21 · 零运行时依赖）
├── Main ── Naming / Repo / Ui（命令分发、题目定位、终端输出）
├── new ─── LeetCode GraphQL → Problem → Scaffold → TestGen
│                          └── Html → Markdown（题面里的公式/图片/代码块）
├── hint ── Ladder → Ai → Http → 你的网关
│             └── hints.md（档位就是历史，可手改）
├── test ── Mvn → solutions 模块（JUnit 5）
│             └── last-test.log（下一次 hint 的输入）
└── clean ─ 系统回收站（题目）/ target（构建产物）
```

```
tool/          命令行工具（17 个类，约 4.1k 行；57 个单元测试）
solutions/     普通 Maven 模块：你的解法 + 生成的测试 + kit 工具类
problems/      每题的题面 / 提示记录 / 完成标记（默认 gitignore）
```

**为什么是 Maven？** 因为 `dojo test` 要跑的就是 JUnit，而 JUnit 需要一个能编译的工程。代价是首次拉依赖；`~/.m2` 有缓存时是秒级。收益是：IDEA 直接按 Maven 导入，测试能点绿箭头，`dojo test` 与 IDE 跑的是同一套东西。

## 配置

按优先级取第一个有 base URL 的来源：

| 优先级 | 位置 | 适合 |
| :--: | --- | --- |
| 1 | 环境变量 `DOJO_BASE_URL` / `DOJO_API_KEY`\|`DOJO_AUTH_TOKEN` / `DOJO_MODEL` / `DOJO_PROVIDER` | CI、临时切换 |
| 2 | 仓库内 `.dojo/config`（已 gitignore） | 这一份仓库专用 |
| 3 | `~/.config/dojo/config` | 全局默认 |
| 4 | `~/.claude/settings.json` 的 `env` 块 | 已经配好 Claude Code，直接复用 |

```properties
base_url=https://your-gateway.example.com/anthropic
api_key=sk-...
model=deepseek-v4.1-flash
provider=anthropic
```

- `provider=anthropic` 走 `/v1/messages`（同时发 `x-api-key` 与 `Authorization: Bearer`）；`provider=openai` 走 `/v1/chat/completions`
- base URL 以 `/v1` 或 `/messages` 结尾都能识别，不会拼出双 `/v1`
- 走代理：显式 `DOJO_PROXY=http://127.0.0.1:7890`；不隐式读 `HTTPS_PROXY`，免得 `doctor` 说通了而 `test` 莫名超时
- 超时：`DOJO_TIMEOUT_SEC`，默认 180 秒

## 设计取舍：刻意不做的事

| 不做 | 为什么 |
| --- | --- |
| Web 页面 / 桌面面板 | 题面、代码、测试输出都在磁盘上，命令行是最近的路 |
| 数据库 | `hints.md` + `done` 就是全部状态；纯文本可 grep、可手改、可 git |
| 星级 / 打卡 / 排行榜 | 和「卡住时能不能往前走」无关 |
| 内置题库镜像 | `new` 现拉，题面永远是最新的 |
| Web 端提交判题 | IDEA 里的 JUnit 已经是更快、更细的反馈 |
| 流式输出 | 提示本来就短；一次拿全，重试逻辑才简单 |

## FAQ

**提示会不会直接把答案说了？**
L1–L4 明确禁止出现可编译的 Java，越界会被重问一次；仍然越界就原文给你并标一行警示。想直接看答案就 `dojo hint 5`。

**行号说错了怎么办？**
请求里带的是文件真实行号，但模型偶尔会数错。`SYSTEM` 已要求「拿不准就别写行号」；真错了就当它在说「那段代码」——重点是它指出的因果关系对不对。

**生成的测试把自己搞崩了怎么办？**
带标记的生成测试编译失败时，`dojo test` 会把它移进系统回收站并自动重跑一次。根因也在生成端收紧了：签名对不上就不生成，提示你手写。`dojo new <题> --force` 可重新生成。

**官方样例全绿了，还要 stress 吗？**
值得跑一次。样例覆盖的是题面给的几个输入；stress 找的是「你的算法在某个边界上偏了一步」这类错误——比如滑动窗口在重复字符时整体清空、BST 只比较父子。跑一次的成本是一次模型调用加几秒 Maven。

**能同时开几道题？**
能。题面、解法、提示互不干扰；唯一耦合是共享一个 Maven 模块，所以别把编译不过的文件留在里面——`new` 生成的空方法体是 `throw new UnsupportedOperationException("TODO: 还没写")`，保证没动笔时模块也是绿的。

**你怎么知道提示是好的？**
`dojo eval` 会跑 20 题 × 3 架构 × 5 档，客观指标（泄底/重复/超长/L5 能不能跑绿）加判官逐档评分，结果在 `eval/RESULTS.md`。判官也是模型，所以主表以客观指标为准。

**想换模型？**
改 `model=` 一行，`dojo doctor` 确认 ping 通即可。推理型模型在 L5 上偶尔会接近 180 秒，必要时调 `DOJO_TIMEOUT_SEC`。

**`dojo: 找不到命令`？**
`bin/dojo` 不在 `PATH` 上。`dojo doctor` 会检查并打印该敲的 `ln -s`；若报 `pom.xml 不存在`，那是最早的启动器没解析符号链接，更新仓库即可。

## 已知限制

- 只支持 **Java**。拉题时接口没给 Java 模板（SQL / Shell 题）会直接报错跳过，不会把 SQL 写进 `Solution.java`。
- 只从 **leetcode.cn** 拉题（英文站字段不同，未适配）。
- 一道题编译不过会挡住整个模块的 `dojo test`；报错会点出是哪道题，并给出 `dojo clean <题号>`。
- 生成器按常见签名写，遇到 `Node` 形状的入参、`ListNode[]`、多返回值会跳过而不是猜。
- `dojo stress` 只覆盖「返回值可比较」的题；设计题、返回 `void`、返回 `List<...>` 的题会明说暂不支持，不会硬凑。
- 提示质量取决于网关背后的模型；同一个 `SYSTEM`，不同模型的「教练感」差别很大。
- 评测集只有 20 题、每档一次采样，只看趋势；判官与被测模型同源时可能偏爱同一种文风。

<div align="center">

---

**卡住不是失败，卡住是提示该升级了。**

MIT License · [LICENSE](LICENSE)

</div>
