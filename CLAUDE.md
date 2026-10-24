# leetcode-dojo

一个 LeetCode 教练 CLI：给你 L1–L5 五档递进提示，帮你自己把题想出来，而不是直接抄题解。

## 结构

- `tool/` — CLI 本体（33 个类，Maven 模块，产出 `tool/target/dojo.jar`）
- `solutions/` — 你的解法 + 官方样例测试（Maven 模块，和 tool 共享一个 reactor）
- `problems/<题号-slug>/` — 题面 `problem.md`、提示记录 `hints.md`
- `bin/dojo.cmd` — Windows 启动器（Unix 是 `bin/dojo`）
- `eval/` — 提示质量评测用例

## 命令

```
dojo doctor        # 自检：java / mvn / 网关 / 仓库位置
dojo new <题号>    # 从 leetcode.cn 拉题，生成解法骨架 + 样例测试
dojo test          # 跑当前题的单测（日志写进 .dojo/last-test.log）
dojo hint          # 要下一档提示（L1→L5）
dojo fade          # 把 L5 挖空 3–5 行让你补
dojo status        # 看进度
dojo done          # 标记完成
dojo clean <题号>  # 删掉某题（进回收站）
dojo learn --check # 检查文档里的外链
```

## Windows 适配（重要）

上游代码是按 Unix 写的，本仓库在 Windows 上做了适配。**改代码时注意这几条**：

- **平台差异集中在 `tool/src/main/java/dojo/Platform.java`**，不要在别的类里散落 `System.getProperty("os.name")` 判断
- **`mvn` 在 Windows 上必须用 `mvn.cmd`** —— `ProcessBuilder("mvn")` 会报 `CreateProcess error=2`。用 `Platform.mvnCommand()`
- **Maven 在 Windows 上把路径打印成 `/d:/code/...`**（盘符前多一个斜杠，且路径含冒号）。解析 Maven 报错里的 `.java` 路径一律走 `Mvn.javaFilesIn()`，别自己写正则
- **Maven 按 GBK 写中文错误**（中文 Windows codepage 936）。读 Maven 日志一律用 `Platform.readText()`，别用 `Files.readString(log, UTF_8)`
- **没有 trash-cli**：`dojo clean` 走 `Clean.writeWindowsTrashScript()`，用 VisualBasic 的 `SendToRecycleBin`
- **PATH 查找要按 PATHEXT 排序**（`.exe` → `.cmd` → `.bat` → 无扩展名），否则 `dojo doctor` 会认错 dojo 位置
- **控制台编码**：`bin/dojo.cmd` 里 `chcp 65001` + `-Dstdout.encoding=UTF-8`，否则中文输出乱码
- 改完 `tool/` 下的 Java 代码，`bin/dojo.cmd` 会自动检测并重新编译，不用手动 `mvn package`

## 构建与测试

```powershell
mvn -B -ntp test           # 全量：tool 123 个测试 + solutions 的样例测试
mvn -B -ntp -pl tool test  # 只跑工具的测试（改了 tool/ 用这个）
```

`tool/target/dojo.jar` 是单 jar 分发物，零运行时依赖。

**全量 `mvn test` 红是正常的**：solutions 里每道新题的解法都是 `throw new UnsupportedOperationException("TODO: 还没写")` 的桩，对应的样例测试自然全红。这是「你还没写」的诚实反馈，不是构建坏了。判断工具本身是否健康，看 `tool` 模块那 123 个测试：

```powershell
mvn -B -ntp -pl tool test   # 必须是 BUILD SUCCESS
```

单题测试用 `dojo test`（只跑那一题，不受别的题拖累）。

## 写代码时的坑

- **新题的解法骨架是 `throw new UnsupportedOperationException("TODO: 还没写")`** —— 这样模块在没动笔时也能编译通过。`Main.hint()` 靠这个字符串判断「模板还没动过」
- **一道题编译不过会挡住整个 solutions 模块的 `dojo test`**。报错信息里会点名是哪道题挡的，并给出 `dojo clean <题号>`
- 生成的样例测试编译不过时会被自动移出模块（进回收站），不会拖累别的题
- `solutions/src/main/java/kit/` 是共用的工具类（ListNode、TreeNode 等），改它会影响所有题
