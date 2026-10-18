# 模式索引

> 这张表是 `dojo learn` 的数据源，也是 L4 尾巴的题库。
> 内容规范：**模板必须是模式骨架，不能是某道题的解法**——不出现题面变量名（`temperatures`、`nums2` 之类）。
> 字段格式：`## id · 中文名` + 若干 `- 字段：值`。链接只收 oi-wiki.org / cp-algorithms.com / leetcode.cn。

## two-pointers · 双指针
- 本质：让两个下标各自单调移动，把 O(n²) 的枚举压成 O(n)
- 信号：有序数组里找一对 / 原地去重或移动元素 / 从两端向中间收缩不会丢解
- 前置：complexity
- 子技能：对撞指针 / 快慢指针 / 原地写指针
- 模板：resources/snippets/two-pointers.java
- 前置题：125 验证回文串
- 巩固题：15, 11
- 链接：https://oi-wiki.org/basic/two-pointers/ | https://cp-algorithms.com/
- 易错：移动哪一侧的判断写反；跳过重复元素时漏掉边界；快慢指针的终止条件

## sliding-window · 滑动窗口
- 本质：维护一个满足条件的区间，右端扩张、左端收缩，两端都不回头
- 信号：求「最长/最短」的连续子数组或子串 / 条件随区间扩大而单调 / 暴力枚举所有区间会 O(n²)
- 前置：two-pointers, hash-map
- 子技能：定长窗口 / 变长窗口 / 计数窗口
- 模板：resources/snippets/sliding-window.java
- 前置题：643 子数组最大平均数 I
- 巩固题：3, 76
- 链接：https://oi-wiki.org/misc/two-pointer/ | https://cp-algorithms.com/
- 易错：收缩用 while 还是 if；窗口内计数何时加减；答案在扩张后更新还是收缩后更新

## binary-search · 二分查找
- 本质：在单调的判定上每次砍掉一半，把「找答案」变成「判真假」
- 信号：有序或可排序 / 答案有单调性（越大越容易满足）/ 要求 O(log n) 或「最小化最大值」
- 前置：complexity
- 子技能：闭区间模板 / 左边界与右边界 / 二分答案
- 模板：resources/snippets/binary-search.java
- 前置题：704 二分查找
- 巩固题：34, 33
- 链接：https://oi-wiki.org/basic/binary/ | https://cp-algorithms.com/num_methods/binary_search.html
- 易错：mid 用 l + (r - l) / 2 防溢出；循环不变量没想清就写；返回 l 还是 r

## monotonic-stack · 单调栈
- 本质：维护一个「还没被后面元素打败」的候选序列
- 信号：要求「下一个更大/更小」 / 需要 O(n) 找左右边界 / 暴力枚举区间会退化
- 前置：stack, complexity
- 子技能：下一个更大元素 / 循环数组 / 柱状图面积
- 模板：resources/snippets/monotonic-stack.java
- 前置题：496 下一个更大元素 I
- 巩固题：739, 84
- 链接：https://oi-wiki.org/ds/monotonous-stack/ | https://cp-algorithms.com/data_structures/stack_queue_modification.html
- 易错：弹栈条件写反；栈里存下标还是存值；相等元素是否弹出

## monotonic-queue · 单调队列
- 本质：用一个两端操作的队列维护窗口内的最值，队首永远是答案
- 信号：滑动窗口的最值 / 需要 O(n) 而不是 O(n log n) / 窗口定长或不定长
- 前置：monotonic-stack
- 子技能：定长窗口最值 / 不定长窗口最值
- 前置题：239 滑动窗口最大值
- 巩固题：862, 1696
- 链接：https://oi-wiki.org/ds/monotonous-queue/ | https://cp-algorithms.com/data_structures/stack_queue_modification.html
- 易错：队首过期判断用下标；入队前先弹尾部；空队列的处理

## prefix-sum · 前缀和与差分
- 本质：把区间求和变成两个前缀相减，把区间加变成两个差分点
- 信号：多次区间查询 / 区间加后单点查 / 子数组和等于 k 的计数
- 前置：hash-map
- 子技能：一维前缀和 / 二维前缀和 / 差分数组
- 前置题：303 区域和检索 - 数组不可变
- 巩固题：560, 304
- 链接：https://oi-wiki.org/basic/prefix-sum/ | https://cp-algorithms.com/
- 易错：前缀数组多开一位；差分还原的顺序；负数与 long 溢出

## hash-map · 哈希表
- 本质：用空间换时间，把「找另一个东西」从遍历变成 O(1) 查询
- 信号：需要快速判断存在 / 计数 / 找补数或配对 / 去重
- 前置：complexity
- 子技能：计数 / 补数配对 / 分组（键的设计）
- 模板：resources/snippets/hash-map.java
- 前置题：217 存在重复元素
- 巩固题：49, 128
- 链接：https://oi-wiki.org/ds/hash/ | https://cp-algorithms.com/
- 易错：先查再放还是先放再查（同一元素不能用两次）；键选错导致分组错；扩容与碰撞的复杂度假设

## stack · 栈与括号
- 本质：后进先出，专门处理「最近一个还没闭合/还没处理的东西」
- 信号：括号匹配 / 嵌套结构 / 表达式求值 / 需要撤销上一步
- 前置：complexity
- 子技能：括号匹配 / 表达式求值 / 用栈模拟递归
- 前置题：20 有效的括号
- 巩固题：155, 394
- 链接：https://oi-wiki.org/ds/stack/ | https://cp-algorithms.com/
- 易错：空栈时弹栈；多余右括号提前返回；嵌套深度与递归栈溢出

## linked-list · 链表指针
- 本质：用几个指针在节点间改指向，别去复制值
- 信号：要求 O(1) 空间 / 删除或插入节点 / 找环、找中点、倒数第 k 个
- 前置：two-pointers
- 子技能：哑结点 / 快慢指针 / 原地反转
- 前置题：206 反转链表
- 巩固题：19, 141
- 链接：https://oi-wiki.org/ds/linked-list/ | https://cp-algorithms.com/
- 易错：改指向的顺序（先存 next）；头结点被删；环的入口推导

## tree-traversal · 树遍历
- 本质：递归定义的结构，用「对左右子树做同样的事」把问题拆小
- 信号：二叉树 / 需要自底向上汇总 / 层序或按层处理
- 前置：stack, complexity
- 子技能：前中后序 / 层序 / 自底向上返回信息
- 模板：resources/snippets/tree-traversal.java
- 前置题：144 二叉树的前序遍历
- 巩固题：102, 94
- 链接：https://oi-wiki.org/graph/tree-basic/ | https://cp-algorithms.com/graph/depth-first-search.html
- 易错：null 作为递归出口；返回值语义没定义清；层序里 queue.size() 固定当层长度

## bst · 二叉搜索树
- 本质：中序有序，每个节点都带着一个「允许的取值区间」
- 信号：有序性判断 / 第 k 小 / 查找与插入 / 区间约束
- 前置：tree-traversal
- 子技能：区间校验 / 中序性质 / 增删改
- 前置题：98 验证二叉搜索树
- 巩固题：230, 701
- 链接：https://oi-wiki.org/ds/bst/ | https://cp-algorithms.com/
- 易错：只比较父子（要传上下界）；相等值是否允许；删除有两个孩子的节点

## heap · 堆与贪心
- 本质：只维护「当前最该被取出的那个」，其余顺序不重要
- 信号：第 k 大/小 / 动态取最值 / 合并多条有序流 / 局部最优可推出全局
- 前置：complexity
- 子技能：Top-K / 多路归并 / 贪心 + 堆
- 前置题：215 数组中的第K个最大元素
- 巩固题：347, 23
- 链接：https://oi-wiki.org/ds/binary-heap/ | https://cp-algorithms.com/
- 易错：大顶堆还是小顶堆；Java 里 PriorityQueue 默认小顶堆；贪心需要证明

## backtracking · 回溯
- 本质：在决策树上做深度优先搜索，进入时做选择、退出时撤销
- 信号：要求列出所有方案 / 组合、排列、子集 / 需要剪枝
- 前置：tree-traversal
- 子技能：子集 / 排列 / 组合与剪枝
- 模板：resources/snippets/backtracking.java
- 前置题：46 全排列
- 巩固题：78, 39
- 链接：https://oi-wiki.org/search/backtracking/ | https://cp-algorithms.com/
- 易错：撤销不干净；used 数组与起始下标二选一；去重需要先排序

## graph-traversal · 图遍历
- 本质：从一个点出发把连通块走完，BFS 求最短步数、DFS 求连通与结构
- 信号：网格或邻接表 / 连通块计数 / 无权图最短路
- 前置：stack, complexity
- 子技能：网格 DFS / BFS 最短路 / 标记访问
- 前置题：200 岛屿数量
- 巩固题：133, 695
- 链接：https://oi-wiki.org/graph/dfs/ | https://cp-algorithms.com/graph/breadth-first-search.html
- 易错：入队时就标记（不是出队时）；边界判断；递归深度导致栈溢出

## union-find · 并查集
- 本质：只关心「是否同一集合」，用路径压缩把查询摊还到近 O(1)
- 信号：动态合并集合 / 判断连通性 / 冗余边 / 按关系分组
- 前置：graph-traversal
- 子技能：路径压缩 / 按秩合并 / 带权并查集
- 前置题：547 省份数量
- 巩固题：684, 990
- 链接：https://oi-wiki.org/ds/dsu/ | https://cp-algorithms.com/data_structures/disjoint_set_union.html
- 易错：初始化自己是自己的父亲；合并前先 find；路径压缩写成递归爆栈

## topo-sort · 拓扑排序
- 本质：把「必须先做 A 才能做 B」的偏序关系排成一个线性顺序
- 信号：依赖关系 / 课程表 / 有向无环图 / 判断是否成环
- 前置：graph-traversal
- 子技能：入度法（BFS）/ DFS 三色标记 / 字典序最小
- 前置题：207 课程表
- 巩固题：210, 802
- 链接：https://oi-wiki.org/graph/topo/ | https://cp-algorithms.com/graph/topological-sort.html
- 易错：入度为 0 的点没全入队；有环时结果数量不足；重复边导致入度算多

## shortest-path · 最短路
- 本质：按「边权是否非负、是否单源」选算法，别一上来就 Dijkstra
- 信号：带权图 / 最小代价 / 非负边权或 0-1 边权
- 前置：graph-traversal, heap
- 子技能：Dijkstra / 0-1 BFS / Bellman-Ford 与负环
- 前置题：743 网络延迟时间
- 巩固题：787, 1514
- 链接：https://oi-wiki.org/graph/shortest-path/ | https://cp-algorithms.com/graph/dijkstra.html
- 易错：有负权还用 Dijkstra；dist 初始化成 Integer.MAX_VALUE 后做加法溢出；松弛后忘记入队

## trie · 字典树
- 本质：用「公共前缀」共享路径，把字符串查找变成逐字符下降
- 信号：大量字符串的前缀查询 / 自动补全 / 单词拆分 / 异或最大值
- 前置：tree-traversal
- 子技能：插入与查找 / 前缀计数 / 01-Trie
- 前置题：208 实现 Trie (前缀树)
- 巩固题：211, 648
- 链接：https://oi-wiki.org/string/trie/
- 易错：isEnd 与「有子节点」混淆；删除时不清标记；数组实现的开销估算

## dp-linear · 线性 DP
- 本质：定义「以 i 结尾/前 i 个」的状态，让每一步只依赖前面有限个状态
- 信号：求最优值或方案数 / 决策有顺序 / 状态能按位置推进
- 前置：complexity
- 子技能：状态定义 / 转移与边界 / 空间压缩
- 模板：resources/snippets/dp-linear.java
- 前置题：70 爬楼梯
- 巩固题：198, 300
- 链接：https://oi-wiki.org/dp/basic/ | https://cp-algorithms.com/dynamic_programming/intro-to-dp.html
- 易错：状态定义含糊导致转移写不出；初始化漏掉空集；滚动数组的更新顺序

## dp-knapsack · 背包
- 本质：每个物品「选或不选」，用容量维度把指数枚举压成多项式
- 信号：有容量/预算限制 / 每个东西只能选一次或无限次 / 求最大价值或方案数
- 前置：dp-linear
- 子技能：0-1 背包 / 完全背包 / 分组与多重背包
- 前置题：416 分割等和子集
- 巩固题：322, 494
- 链接：https://oi-wiki.org/dp/knapsack/ | https://cp-algorithms.com/dynamic_programming/knapsack.html
- 易错：0-1 背包倒序遍历容量；完全背包正序；求方案数时的初始化（dp[0]=1）

## dp-interval · 区间 DP
- 本质：先算小区间，再合并成大区间，转移里枚举分割点
- 信号：合并石子/消去游戏 / 回文子序列 / 区间上的最优划分
- 前置：dp-linear
- 子技能：区间长度枚举 / 分割点枚举 / 环形区间
- 前置题：516 最长回文子序列
- 巩固题：312, 1039
- 链接：https://oi-wiki.org/dp/interval/ | https://cp-algorithms.com/dynamic_programming/intro-to-dp.html
- 易错：枚举顺序必须按区间长度；边界空区间；环形要断环成链

## dp-bitmask · 状压 DP
- 本质：把「哪些元素已经选过」压成一个整数的二进制位
- 信号：n ≤ 20 左右 / 需要记录集合状态 / 排列与覆盖问题
- 前置：dp-linear, bit-manipulation
- 子技能：集合状态 / 子集枚举 / 旅行商式转移
- 前置题：526 优美的排列
- 巩固题：464, 847
- 链接：https://oi-wiki.org/dp/state/ | https://cp-algorithms.com/dynamic_programming/profile-dynamics.html
- 易错：1 << n 溢出；枚举子集写法；状态数与内存估算

## bit-manipulation · 位运算
- 本质：把一个整数的每一位当成一个独立的布尔量
- 信号：只出现一次/两次 / 子集枚举 / 不用额外空间 / 状态压缩
- 前置：complexity
- 子技能：异或消消乐 / lowbit / 掩码与移位
- 前置题：136 只出现一次的数字
- 巩固题：191, 338
- 链接：https://oi-wiki.org/math/bit/ | https://cp-algorithms.com/algebra/bit-manipulation.html
- 易错：Java 的 >>> 与 >>；负数补码；1 << 31 的符号

## math · 数学与数论
- 本质：先看能不能用公式、约数、质数或快速幂把枚举降一个量级
- 信号：计数/取模 / 质数、公约数 / 大指数幂 / 组合数
- 前置：complexity
- 子技能：质数与筛 / 快速幂与取模 / 组合计数
- 前置题：204 计数质数
- 巩固题：50, 172
- 链接：https://oi-wiki.org/math/ | https://cp-algorithms.com/algebra/binary-exp.html
- 易错：取模后再除（要用逆元）；筛法边界；long 溢出
