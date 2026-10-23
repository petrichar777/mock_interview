# 题目导入模板（Markdown）

说明：
- 文件编码：UTF-8
- 文件后缀：`.md`
- 每道题之间必须用单独一行 `---` 分隔
- 必填字段：`moduleId`、`type`、`difficulty`、`stem`、`options`（判断题可仅提供 `answer: T/F`）
- `moduleId`：模块 ID，可在管理后台「题库管理 → 添加题目」页面下拉框中查看，格式为「方向 / 分类 / 模块名」
- `type` 支持：`1/2/3` 或 `单选/多选/判断` 或 `SINGLE/MULTI/JUDGE`
- `difficulty` 支持：`1/2/3` 或 `简单/中等/困难`
- 去重更新规则：`moduleId + stem + type` 命中则更新，不重复新增

---

moduleId: 8
type: 单选
difficulty: 中等
stem: JVM 中哪一种垃圾回收器以低停顿为目标并支持并发标记？
analysis: G1 通过分区化内存管理和并发标记阶段，兼顾吞吐与停顿时间控制。
tags: JVM,GC,G1
status: 0
answer: A
options:
- A. G1（正确）
- B. Serial GC
- C. Parallel GC
- D. Epsilon GC

---

moduleId: 8
type: 多选
difficulty: 困难
stem: 在 Spring 事务中，哪些传播行为会开启新事务？
analysis: REQUIRES_NEW 会挂起当前事务并开启新事务；NESTED 在支持保存点场景下创建嵌套事务语义。
tags: Spring,事务
status: 0
answer: B,C
options:
- A. REQUIRED
- B. REQUIRES_NEW（正确）
- C. NESTED（正确）
- D. SUPPORTS

---

moduleId: 9
type: 判断
difficulty: 简单
stem: Redis 的 String 类型底层只能保存字符串，不能保存整数。
analysis: Redis String 可以保存字符串、整数和浮点数，整数在内部会以整数编码优化存储。
tags: Redis,数据结构
status: 0
answer: F
options:
- T. 正确
- F. 错误（正确）

---

## 可选写法（判断题极简）

当 `answer` 为 `T/F` 时，可不写 options，系统会自动补齐：

moduleId: 9
type: 判断
difficulty: 1
stem: MySQL InnoDB 支持事务。
analysis: InnoDB 是事务型存储引擎，支持 ACID。
tags: MySQL,事务
status: 0
answer: T

---
