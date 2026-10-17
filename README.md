# dojo

在 IDEA 里刷 LeetCode，卡住的时候给你**下一档**提示——不是答案，也不是鸡汤。

```bash
dojo new 1          # 拉题面 + 生成官方样例的 JUnit 测试 + 空解法
# 在 IDEA 里写 solutions/src/main/java/p0001_two_sum/Solution.java
dojo test           # 跑官方样例
dojo hint           # 卡住了：要下一档提示
dojo done           # 过了：打勾
```

## 为什么是这么个东西

之前的版本把力气花在了外围：网页、数据库、爬虫、星级、设置页。提示本身反而是「两次模型调用 + 纪律校验 + 不合格就换一段罐头文案」，卡住的人看到的经常是一段正确但没用的废话。

这一版只做一件事：**把提示做准**。

- **一次调用**。不再拆「诊断器 + 引导器」，模型同时看到题面、你磁盘上的真实代码（带行号）、最近一次测试输出、已经给过的提示，一次给出下一档。
- **阶梯是真的阶梯**。L1 只问边界，L2 只点方向，L3 给一个洞察，L4 给步骤骨架，L5 才是完整题解。越级要显式说，比如 `dojo hint 5`。
- **不重复**。已经给过的提示会原样进上下文，模型被要求「一句都不许重复，且必须承接上一档」。
- **失败不装死**。模型越界给了代码、或者被长度截断，会重问一次；仍然不行就原文照发 + 一行警示，绝不偷偷换成罐头文案。
- **测试必须能跑**。生成的测试若编译不过就删掉并说明原因，不让一个坏文件拖垮整个模块。

## 需要什么

- JDK 21（`java -version` 能看到 21）
- Maven 3.9+（`mvn -v`）
- 一个 Anthropic 或 OpenAI 兼容的模型网关（下面的「配置」）

不需要数据库、不需要账号、不需要常驻服务。`bin/dojo` 第一次运行会用 Maven 把工具编出来（几秒，之后走缓存）。

## 安装

```bash
git clone <这个仓库> ~/code/leetcode-dojo
ln -s ~/code/leetcode-dojo/bin/dojo ~/.local/bin/dojo   # 软链到 PATH 里；或者把 bin/ 加进 PATH
dojo doctor                                             # 自检：java / mvn / 配置 / 一次 ping
```

`bin/dojo` 会先解析自己的符号链接，再找仓库根目录（`DOJO_HOME`，或从当前目录往上找），所以软链进 `~/.local/bin` 之后，在 `solutions/...`、`/tmp` 任何地方都能直接敲 `dojo`。

## 日常循环

```
dojo new 146            # 或 dojo new lru-cache，或直接粘 leetcode.cn 的链接
dojo new 1-100          # 想一次铺开一个区间也行
        │
        ├─ solutions/src/main/java/p0146_lru_cache/Solution.java   ← 在 IDEA 里写
        │
dojo test               # 官方样例；失败输出会留给下一次 hint
        │
dojo hint               # L1 → L2 → L3 → L4 → L5，每次只推进一格
        │
dojo done               # 打勾，problems/0146-lru-cache/done
```

`dojo status` 随时看战况：哪些题拿了哪几档提示、哪些已完成。

## 命令

| 命令 | 作用 |
| --- | --- |
| `dojo new <slug\|题号\|链接>` | 拉题面、生成 `Solution.java` 与官方样例测试 |
| `dojo new <a>-<b>` | 批量拉 `a` 到 `b`（含两端，一次最多 300 道） |
| `dojo hint [1-5] [卡点]` | 要下一档提示；带数字=指定档位，带文字=把你的卡点一起告诉模型 |
| `dojo test` | 跑这道题的 JUnit 测试，结果写进 `.dojo/last-test.log` |
| `dojo status` | 所有题的目标、最高档位、是否完成 |
| `dojo done` | 标记完成 |
| `dojo prompt [1-5]` | 只打印将要发给模型的完整请求，不发请求（调提示词时用） |
| `dojo doctor` | 环境与网关自检 |
| `dojo clean` | 清构建产物；带题号/`--all` 删除题目（进系统回收站） |
| `dojo help` | 用法 |

通用选项：`--problem/-p <id|slug|题号>` 指定题目（默认按当前目录 → 最近修改的解法推断）；`--note/-n <text>` 附上卡点；`--no-save` 不写入 `hints.md`；`--debug` 打印请求；`new` 另有 `--force`、`--no-test`、`--no-verify`、`--unordered`。

## 批量拉题

```
dojo new 1-100      # 1 到 100，含两端；写 100-1 也认
```

- 逐题拉取，题与题之间停 150 ms；已经在本地的题直接跳过（加 `--force` 才覆盖）
- 某道题失败（会员题、接口抽风）只记一笔，不中断整批；最后列出失败的题号，`dojo new <题号>` 单独重试
- 批量时不逐题跑 Maven（100 次 `mvn` 太慢），全部写完统一 `mvn test-compile` 一次；哪个生成的测试编不过就删哪个，并报出题号
- 一次最多 300 道，防止手滑把磁盘塞满；要更多就分段

## 清理

```bash
dojo clean          # 删 tool/target、solutions/target、.dojo/last-test.log
dojo clean 1        # 把这题的题面 / 解法 / 测试挪进系统回收站
dojo clean --all    # 所有题都挪进系统回收站
```

构建产物随删随建，下一次 `dojo` 命令会自动重编工具 jar。题目文件不真删，走系统回收站（`mavis-trash` / `gio trash` / `trash-put`，本机有什么用什么），删错了从文件管理器里拖回来即可。

## 提示阶梯

| 档 | 名字 | 给什么 | 篇幅 |
| --- | --- | --- | --- |
| L1 | 澄清 | 只问边界与题面偏差，1–2 个能一句话回答的问题 | ≤120 字 |
| L2 | 方向 | 点名模式，解释为什么适配这组输入的形状 | ≤150 字 |
| L3 | 洞察 | 打破僵局的那一个观察，可点名变量与行号 | ≤220 字 |
| L4 | 骨架 | 3–6 步 + 复杂度 + 最易错点；伪代码可以，可编译 Java 不行 | ≤350 字 |
| L5 | 题解 | 完整 Java + 思路 + 正确性 + 复杂度 + 一个变体 | 不限 |

规则写在 `tool/src/main/java/dojo/Ladder.java` 的 `SYSTEM` 里。想改教练的性格、篇幅、口吻，改那一段，然后 `dojo prompt 3` 看效果。

档位状态不存数据库，直接由 `problems/<id>/hints.md` 里的 `## L3 · 洞察 · 时间` 小节数推导——所以你可以手改这个文件，删掉最后一节就等于「退回上一档」。

## 配置

按优先级取第一个有 base URL 的来源：

1. 环境变量：`DOJO_BASE_URL`、`DOJO_API_KEY`（或 `DOJO_AUTH_TOKEN`）、`DOJO_MODEL`、`DOJO_PROVIDER`（`anthropic` / `openai`）
2. 仓库内 `.dojo/config`（已 gitignore）
3. `~/.config/dojo/config`
4. `~/.claude/settings.json` 里的 `env` 块（复用 Claude Code 的 `ANTHROPIC_BASE_URL` / `ANTHROPIC_AUTH_TOKEN` / 模型名）

配置文件是 `key=value`：

```properties
base_url=https://your-gateway.example.com/anthropic
api_key=sk-...
model=deepseek-v4.1-flash
provider=anthropic
```

- `provider=anthropic` 走 `/v1/messages`，同时发 `x-api-key` 与 `Authorization: Bearer`；`provider=openai` 走 `/v1/chat/completions`。
- base URL 以 `/messages` 或 `/v1` 结尾都能识别，不会拼出双 `/v1`。
- 走代理时显式给 `DOJO_PROXY=http://127.0.0.1:7890`；不隐式读 `HTTPS_PROXY`，免得 `dojo doctor` 说通了、`dojo test` 却莫名超时。

`dojo doctor` 会打印来源、端点、模型、掩码后的 token，并做一次最小 ping。

## 目录

```
problems/0001-two-sum/
  problem.md       题面（含官方样例，可直接读）
  hints.md         每次提示的原文，按档位追加
  done             完成标记
solutions/
  src/main/java/kit/{ListNode,TreeNode,ListNodes,TreeNodes}.java   预置工具类，始终可编译
  src/main/java/p0001_two_sum/Solution.java                        解法（类名沿用模板）
  src/test/java/p0001_two_sum/TwoSumTest.java                      生成的官方样例测试
tool/              命令行工具本身（单个可执行 jar，零运行时依赖）
```

`solutions` 是一个普通 Maven 模块，IDEA 直接按 Maven 项目导入即可；测试是 JUnit 5，点绿箭头也能跑。

## 官方样例是怎么变成测试的

`new` 时用题目的 `metaData` + `jsonExampleTestcases` 做**确定性**转换（不经过模型）：

- 支持 `integer`、`integer[]`、`integer[][]`、`string`、`string[]`、`string[][]`、`character`、`double`、`boolean`、`long`、`list<...>`、`ListNode`、`TreeNode`、`void`（断言被修改的入参）
- 期望输出不是「第 N 个用例配第 N 个输出」：接口常常只保留 3 个用例而题面写了 5 个（第 13 题就是），所以按**输入值**去题面示例里配对，配不上就跳过，绝不硬安一个错的期望值
- 设计题按操作序列生成 `obj.method(...)`
- 单例转不动就跳过这一例并注释原因；全都转不动就跳过整个文件并打印原因，不会写坏文件
- 题面自己写「顺序不重要 / 任意顺序」时自动按无序比较（`int[]` 排序、`List<List<...>>` 归一化）；也可以手动 `--unordered`

生成完会跑一次 `mvn test-compile` 校验：测试自己编译不过 → 删除并警告；解法编译不过 → 保留测试但提示先修解法。

- 模板里被注释掉的类型定义（克隆图的 `Node`、随机链表的 `Node`）会被还原成真代码，保证 `Solution.java` 一生成就能编译；`ListNode` / `TreeNode` 用 `kit` 里的版本，不重复定义

**没有覆盖到的**：`Node` 形状的入参、`ListNode[]` 这类很少见的签名，生成器会跳过并说明原因（克隆图的 `metaData` 本身就把参数描述错了，宁可跳过也不猜）。此时自己照 `problem.md` 写两行测试即可，工具不会因此挡住你。

## 在 IDEA 里怎么用

1. `idea ~/code/leetcode-dojo`，按 Maven 项目导入，Project SDK 选 21。
2. 每道题只改一个文件：`solutions/src/main/java/pXXXX_xxx/Solution.java`。
3. 卡住时在 IDEA 的终端里敲 `dojo hint`（不用切窗口，它会读你刚存的文件）。
4. 测试可以从 IDEA 里点，也可以 `dojo test`——后者会把失败输出留给下一次提示。

## FAQ

**`dojo: 找不到命令`？**
说明 `bin/dojo` 没在 `PATH` 上。`dojo doctor`（用 `bin/dojo doctor` 跑）会检查这一项并打印该敲的 `ln -s` 命令；软链建好后如果提示 `pom.xml 不存在`，那是旧版启动器没解析符号链接，更新仓库即可。

**提示会不会直接把答案说了？**
L1–L4 明确禁止出现可编译的 Java，模型越界会被重问一次；仍然越界就把原文给你并标警示。想要答案就直接 `dojo hint 5`。

**行号说错了怎么办？**
请求里带的是文件真实行号，但模型偶尔会数错。`SYSTEM` 里已经要求「拿不准就别写行号」，仍出错就当它在说「那段代码」——重点是它指出的因果关系对不对。

**为什么非要 Maven？**
因为 `dojo test` 要跑的就是 JUnit，而 JUnit 需要一个能编译的工程。代价是第一次要拉依赖；`~/.m2` 里有缓存时是秒级。

**能同时开几道题？**
能。每道题的解法、测试、提示互不干扰；唯一的耦合是它们共享一个 Maven 模块，所以别把编译不过的文件留在里面（`new` 生成的空解法会塞一句 `throw new UnsupportedOperationException("TODO")`，保证你还没动笔时模块也是绿的）。

**为什么不做网页/数据库/星级？**
那些东西不会让提示变好，只会让「卡住」这件事多绕几步。题面、代码、测试输出都在磁盘上，命令行是最近的路。

## 已知限制

- 只支持 Java。
- 只从 leetcode.cn 拉题（英文站字段不同，没做适配）。
- 生成器按常见签名写，遇到 `Node`、`ListNode[]`、多返回值之类会跳过而不是猜（`Node` 类型定义本身会还原进 `Solution.java`，只是样例转不过去）。
- 提示质量取决于网关背后的模型；换模型只需改 `model=`。
- 一次性调用最长等 180 秒（`DOJO_TIMEOUT_SEC` 可调），推理型模型在 L5 上偶尔会到这个量级。
