# 在 IDEA 里不切窗口要提示

目标：光标停在 `Solution.java` 里，按一个键，提示直接出现在 IDEA 的 Run 窗口，并同时在编辑器里打开一份 `last-hint.md`。

命令本身不用改，`dojo hint` 在任何目录都能找到仓库——IDEA 的 External Tool 把工作目录设成当前文件所在目录即可。

## 1. 加两个 External Tool

`Ctrl+Alt+S` → **Tools → External Tools → +**

### dojo hint

| 字段 | 值 |
| --- | --- |
| Name | `dojo hint` |
| Program | `~/.local/bin/dojo`（或 `$ProjectFileDir$/bin/dojo`） |
| Arguments | `hint --open` |
| Working directory | `$FileDir$` |

### dojo test

同样加一个，只是 Arguments 换成 `test`、Name 换成 `dojo test`。

> 不想让它自动打开文件：Arguments 写成 `hint`，或在使用时把环境变量 `DOJO_OPEN=0`。

## 2. 绑快捷键

`Ctrl+Alt+S` → **Keymap** → 搜索 `dojo hint` → 右键 **Add Keyboard Shortcut**：

- `dojo hint` → `Ctrl+Alt+Shift+H`
- `dojo test` → `Ctrl+Alt+Shift+T`

如果和已有快捷键冲突，IDEA 会弹冲突提示，换一个空着的组合即可（这两个默认没被占用）。

## 3. 用

1. 打开 `solutions/src/main/java/p0001_two_sum/Solution.java`，光标放在文件里；
2. 按 `Ctrl+Alt+Shift+H`；
3. 底部 Run 窗口出现提示正文；同时 `problems/0001-two-sum/last-hint.md` 会在编辑器里打开（有 `idea` 命令时）。

`dojo hint` 会自己发现代码比上次测试新，先跑一遍测试再要提示；所以通常连 `dojo test` 都不用按。

## 常见问题

**按了没反应？** 看 Run 窗口的 `dojo hint` 输出。最常见是 `Program` 写成了 `dojo` 而不是绝对路径——IDEA 的 PATH 和终端不一样。

**提示没在编辑器里打开？** 说明 PATH 里没有 `idea` 命令（Toolbox 装在 `~/.local/share/JetBrains/Toolbox/scripts`）。两个选择：把该目录加进 PATH，或者直接双击 `problems/<id>/last-hint.md`。

**中文乱码？** External Tool 底下有 `Charset`，选 UTF-8。
