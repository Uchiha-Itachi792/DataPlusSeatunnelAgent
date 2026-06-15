# 项目待办与路线图

> 最后更新：2026-06-14  
> 本文档汇总个人/团队待办，按 **P0 → P4** 优先级排序。各章为独立专题；详细设计见对应章节。

---

# 一、待办总览（按优先级）

## P0 — 核心体验与稳定性（优先动手）

| # | 方向 | 说明 | 详情 |
|---|------|------|------|
| 1 | Intent 扩展 · 阶段 1 | 《纯查数/SQL》《完整数据分析》；prompt + DTO + Dispatcher + `IS_ONLY_NL2SQL` flag | [二、Intent 扩展](#二intent-意图分类扩展计划) |
| 2 | MultiTurn 持久化 | `conversation_turn` 表；sessionId 作稳定 `threadId`；重启/刷新可恢复多轮 | [三、MultiTurn 持久化](#三multiturncontextmanager-持久化) |
| 3 | 全局异常与错误透出 | Controller 统一 Advice；NL2SQL/Graph 接口返回可读错误（非裸 500） | [四、其他待办 · 工程质量](#411-工程质量) |
| 4 | Plan 校验时机优化 | 校验下沉至 Planner 生成后，避免 PlanExecutor 每步重复校验 | [四、其他待办 · Graph 工作流](#405-graph-工作流) |
| 5 | 核心 Node 单测补齐 | 按 `docs/superpowers/nodes-dependency-analysis.md` 补 Mock 单测/集成测 | [2.6 测试计划](#26-测试计划)、[4.12 评测与文档](#412-评测与文档) |

## P1 — 能力与质量

| # | 方向 | 说明 |
|---|------|------|
| 1 | Intent 扩展 · 阶段 2 | `KnowledgeQaNode`、`MetaHelpNode`；知识问答 / 系统帮助专用路径 |
| 2 | nl2sql 图级短路 | 纯 SQL 意图跳过部分 Schema / Feasibility（不单靠 flag；阶段 1 仅自动等价 `nl2sqlOnly`） |
| 3 | API Key 鉴权 | `X-API-Key` 拦截校验（`ADVANCED_FEATURES.md` 已说明尚未实现） |
| 4 | 向量库持久化 | pgvector / ES 等替代默认内存 `SimpleVectorStore` |
| 5 | SQL 执行安全 | 只读连接、表白名单、行数/超时上限 |
| 6 | NL2SQL 评测闭环 | SqlGenerate / Execute / SemanticConsistency 基准数据集与回归 |
| 7 | 语义模型维护 | 批量导入、与表字段对齐（`SemanticModel`、`product_semantic_model.json`） |
| 8 | Connector 层缓存 | `AccessorFactory`、`DdlFactory`、`DBConnectionPoolFactory` 缓存 |

## P2 — 生产化与体验

| # | 方向 | 说明 |
|---|------|------|
| 1 | Intent 扩展 · 阶段 3 | 澄清回复、操作指令；Human Feedback 续跑状态机 |
| 2 | 混合检索 Fusion | 补全 `WeightedAverageStrategy` 等未实现融合策略 |
| 3 | 向量 metadata 维护 | 按 metadata 批量删除/更新（知识库清理；SimpleVectorStore 现有限制） |
| 4 | TextSplitter 可配 | 前端选择切割策略（`TextSplitterFactory` TODO） |
| 5 | Schema 自动同步 | 业务库表结构变更后 Agent 元数据刷新 |
| 6 | Prompt 治理 | 版本对比、A/B、自动优化（`UserPromptService` / PromptConfig） |
| 7 | 可观测性 | Langfuse 默认集成；日志记录 classification、node 耗时 |
| 8 | Graph 运行时 | 并发限流、背压、流式中断/超时与资源回收 |
| 9 | 管理台分页 | Agent、数据源、业务知识、语义模型等列表分页与搜索（前后端） |
| 10 | 前端体验 | 节点级进度条；SSE 错误/中断态；意图分类展示 |

## P3 — 数据工程与深度能力

| # | 方向 | 说明 |
|---|------|------|
| 1 | Intent 扩展 · 阶段 4 | SeaTunnel / 数据同步任务 Node；数据质量/探查轻量路径 |
| 2 | 通用 CLI Executor | SeaTunnel 等外部 CLI 抽象（配置进 `spring.ai.alibaba.data-agent.*`） |
| 3 | Python 执行生产化 | Docker 资源配额、依赖镜像、网络隔离、失败降级 |
| 4 | 多数据库方言 | MySQL 以外 Connector / DDL 扩展 |
| 5 | 成本与可用性 | Token/调用成本统计；模型 failover |
| 6 | MCP 扩展 | 新 Tool；`McpServerConfig` 合并入 `DataAgentConfiguration` |
| 7 | 离线/内网部署 | 报告模板 marked/echarts CDN 可配置 |
| 8 | OSS 文件读取 | `OssFileStorageServiceImpl` 支持重复读 |

## P4 — 企业级与长期

| # | 方向 | 说明 |
|---|------|------|
| 1 | Intent 扩展 · 阶段 5 | 《越权/敏感查询》；Agent / 数据源 / 表级权限模型 |
| 2 | 多 Agent 路由 | 「非本 Agent 范围」引导切换 |
| 3 | 备份与迁移 | Agent、知识库、向量索引 |
| 4 | 开放集成 | OpenAPI/SDK；Webhook；异步长任务（不占 SSE） |
| 5 | 产品能力 | 报告导出、会话分享、结果缓存；Agent 模板/一键克隆 |
| 6 | 技术债务 | `@Deprecated` API 清理；DTO/枚举规范化（如 `Agent.status`） |
| 7 | 国际化与部署 | 前端 i18n；Docker Compose 全栈、健康检查、优雅停机 |
| 8 | Graph 高级运行时 | Plan Repair 上限；checkpoint 断点续跑（除 Human Feedback 外） |
| 9 | 数据源运维 | 连接池监控；结构化日志 + traceId 贯穿全链路 |

---

# 二、Intent 意图分类扩展计划

> **状态**：计划中  
> **关联模块**：`IntentRecognitionNode`、`IntentRecognitionDispatcher`、`intent-recognition.txt`  
> **全局优先级**：P0（阶段 1）→ P1（阶段 2）→ P2（阶段 3）→ P3（阶段 4）→ P4（阶段 5）

## 2.1 目的与意义

### 背景

当前 `IntentRecognitionNode` 仅做二分类：

- 《闲聊或无关指令》→ 直接结束图（`END`）
- 《可能的数据分析请求》→ 进入完整数据分析流水线

该设计作为**轻量级前置过滤器**，用较低成本挡掉明显无效请求。但随着业务扩展（纯 SQL、知识问答、SeaTunnel 数据工程、多轮追问等），「一律走完整分析链」会带来：

- **算力浪费**：简单查数也跑 Evidence → Schema → Planner → Report
- **体验不佳**：知识类问题、系统帮助类问题没有专门路径
- **能力割裂**：`nl2sqlOnly` 等能力依赖前端传参，无法由 LLM 自动识别意图

### 目标

在 Intent 层增加**有限数量**（建议 3～5 类）的粗粒度分类，实现：

1. **早分流**：在昂贵节点（Schema 召回、Planner、Python 执行）之前决定走哪条路径
2. **能力对齐**：与项目已有能力（`nl2sqlOnly`、Evidence RAG、Feasibility、PlanExecutor）形成互补，而非重复
3. **可扩展**：为 SeaTunnel / 数据同步等后续节点预留入口

### 与现有分层的关系

```
IntentRecognition（本计划扩展层）  ← 粗分类、低成本、早分流
    ↓
EvidenceRecall / QueryEnhance / Schema …
    ↓
FeasibilityAssessment              ← 细判断：能否答、是否需澄清（已有，不宜上移）
    ↓
Planner → SQL / Python / Report
```

**原则**：Intent 负责「走哪条路」；Feasibility 负责「这条路能不能走通、要不要反问用户」。

## 2.2 扩展方向

### 方向 A：按路径复杂度分（优先推荐）

| 分类 | 含义 | 预期路由 |
|------|------|----------|
| 《纯查数/SQL》 | 单表简单查询，只要 SQL，不要报告/Python | 等价或替代前端 `nl2sqlOnly`，缩短链路 |
| 《完整数据分析》 | 多步分析、对比、图表、报告 | 走现有全流程 |
| 《业务知识问答》 | 问指标定义、术语（如「PV 是什么」） | EvidenceRecall + 专用 QA 节点，跳过 Schema/Planner |
| 《元问题/系统帮助》 | 「你能做什么」「有哪些表」 | 模板回复或读 Agent 配置，直接 END |

### 方向 B：按用户动作分（产品体验向）

| 分类 | 含义 | 预期路由 |
|------|------|----------|
| 《多轮追问》 | 「再按地区拆」「刚才那个改一下」 | 仍走数据分析，强化 `multi_turn` 上下文 |
| 《澄清回复》 | 用户在回答 Feasibility 上一轮反问 | 按 `threadId` 续跑，避免全量重跑 Intent |
| 《操作指令》 | 停止生成、清空会话、导出报告等 | 调 Service 层 API，不进入 Graph |

### 方向 C：按安全/合规分（企业场景）

| 分类 | 含义 | 预期路由 |
|------|------|----------|
| 《越权/敏感查询》 | 无权限表、个人隐私字段等 | END + 固定合规提示 |
| 《非本 Agent 范围》 | 与当前 Agent 业务域明显无关 | END 或引导切换 Agent |

### 方向 D：数据工程扩展（SeaTunnel 方向）

| 分类 | 含义 | 预期路由 |
|------|------|----------|
| 《数据同步任务》 | 同步表、提交 SeaTunnel 作业 | 专用 Node + Executor |
| 《数据质量/探查》 | 行数、空值率、采样预览 | SchemaRecall + 轻量 SQL |

### 不建议在 Intent 层重复实现的

| 能力 | 已有位置 | 说明 |
|------|----------|------|
| 能否用 Schema 回答 | `FeasibilityAssessmentNode` | Intent 阶段信息不足 |
| 是否需要澄清反问 | `FeasibilityAssessmentNode` | 同上 |
| SQL / Python / 报告编排 | `PlannerNode` + `PlanExecutorNode` | 执行计划层 |
| 纯参数控制 nl2sql | `GraphRequest.nl2sqlOnly` | 保留为 override |

### 实施阶段（与第一章总览对齐）

1. **P0 · 阶段 1**：《纯查数/SQL》《完整数据分析》— flag + 路由；图级短路见 P1  
2. **P1 · 阶段 2**：《业务知识问答》《元问题/系统帮助》  
3. **P2 · 阶段 3**：《澄清回复》《操作指令》— 依赖 [三、MultiTurn 持久化](#三multiturncontextmanager-持久化)  
4. **P3 · 阶段 4**：《数据同步任务》等 — SeaTunnel 集成  
5. **P4 · 阶段 5**：《越权/敏感查询》— 见 [4.2 安全与合规](#402-安全与合规)

## 2.3 实现概要

### Prompt 与输出契约

1. 修改 `prompts/intent-recognition.txt`：分类标准、few-shot、互斥优先级  
2. 扩展 `IntentRecognitionOutputDTO`：新枚举值；可选 `confidence`、`reason`

### 路由分发（Dispatcher）

扩展 `IntentRecognitionDispatcher`，路由示意：

```
《闲聊或无关指令》     → END
《元问题/系统帮助》    → MetaHelpNode（新）→ END
《业务知识问答》       → EvidenceRecall → KnowledgeQaNode（新）→ END
《纯查数/SQL》         → IS_ONLY_NL2SQL → EvidenceRecall → …
《完整数据分析》       → EvidenceRecall → …（默认链）
《越权/敏感查询》      → END + 固定错误响应
《数据同步任务》       → SeaTunnelTaskNode（新）→ END
```

### 图结构（StateGraph）

1. `DataAgentConfiguration.nl2sqlGraph()`：新分支、新 Node、KeyStrategy 注册  
2. 缩短链路：  
   - **阶段 1（P0）**：仅 `IS_ONLY_NL2SQL`；Planner 固定 plan；跳过 Report  
   - **阶段 1.5（P1）**：评估跳过 Feasibility / 部分 SchemaRecall  

### 会话与多轮

- 《澄清回复》：state 或 `MultiTurnContextManager` 记录「等待澄清」  
- 《多轮追问》：继续 `MULTI_TURN_CONTEXT` 注入  
- 持久化依赖：[三、MultiTurn 持久化](#三multiturncontextmanager-持久化)

### 前端与 API

- 保留 `nl2sqlOnly` 作为 override；可选返回 `classification`  
- `AgentRun.vue`：元问题、知识问答、合规拒绝等 UI  

### 实施 Checklist

- [ ] **阶段 1**：prompt + DTO + Dispatcher（不新增 Node）  
- [ ] **阶段 2**：`KnowledgeQaNode`、`MetaHelpNode`  
- [ ] **阶段 3**：澄清回复与会话状态机  
- [ ] **阶段 4**：SeaTunnel / 数据工程 Node  
- [ ] **阶段 5**：权限/合规与 Agent 权限模型  

## 2.4 风险与注意事项

1. **分类过多**：每阶段只加 1～2 类，用指标验证后再扩  
2. **与 Feasibility 职责重叠**：「能不能答」仍放 Feasibility  
3. **前后端兼容**：未知分类需默认分支（等同《可能的数据分析请求》）  
4. **多轮历史**：知识问答是否入 history 需单独定义；重启丢失见 [第三章](#三multiturncontextmanager-持久化)

## 2.5 验收标准

- [ ] P0 意图路由准确率 ≥ 约定阈值（如 90%）  
- [ ] P1 起：纯 SQL 图级短路后节点数/耗时明显低于完整分析（P0 仅验证 Planner/Report 跳过）  
- [ ] 《闲聊》仍在 Intent 层拦截  
- [ ] `nl2sqlOnly=true` 行为不退化  
- [ ] `docs/ARCHITECTURE.md` 同步 Intent 分支说明  
- [ ] [2.6 测试计划](#26-测试计划) P0 用例全部通过  

## 2.6 测试计划

### 2.6.1 测试目标

1. **路由正确**：每种 `classification` 进入预期下一节点或 `END`  
2. **行为不退化**：闲聊拦截、数据分析进链与改造前一致  
3. **边界可靠**：null/空 classification、未知分类、非法 JSON 有明确兜底  
4. **路径可验证**：《纯查数/SQL》少跑关键 Node（集成测试断言节点名）  
5. **可回归**：Prompt / Dispatcher 变更后用固定数据集验证  

### 2.6.2 测试分层

| 层级 | 范围 | 是否调真实 LLM | 主要文件/方式 |
|------|------|----------------|---------------|
| **单元测试** | Node、Dispatcher、Prompt 渲染、DTO 解析 | 否（Mock `LlmService`） | 见 2.6.3 |
| **Dispatcher 测试** | classification → 下一节点 | 否 | `IntentRecognitionDispatcherTest` |
| **工作流集成测试** | 从 Intent 到首个分叉后的节点序列 | 可选 Mock 或 Testcontainers | `*WorkflowIntegrationTest` |
| **GraphService 测试** | `nl2sqlOnly` override 与 Intent 自动识别优先级 | Mock `CompiledGraph` | `GraphServiceImplTest` |
| **Prompt 评测（离线）** | 固定语料 + 真实/录制 LLM 输出 | 是（CI 可选 nightly） | `docs/eval/intent-classification/`（待建） |
| **端到端（手工/自动化）** | 前端 SSE + 后端全链路 | 是 | Postman / 前端 E2E |

### 2.6.3 需新增或扩展的测试类

#### 已有（需扩展）

| 测试类 | 现有覆盖 | 扩展内容 |
|--------|----------|----------|
| `IntentRecognitionDispatcherTest` | 数据分析 → EvidenceRecall；闲聊 → END；null/空 → END | 每个新 classification 一条路由用例；未知分类默认分支 |
| `IntentRecognitionNodeTest` | prompt 含 `multi_turn`；LLM Mock 流式返回 | 各分类下 prompt 占位符渲染；新 DTO 字段解析 |
| `PromptHelperTest` | `buildIntentRecognitionPrompt` 基础场景 | 新分类示例语料在 prompt 中可见 |
| `GraphServiceImplTest` | 流式启动、stop | `nl2sqlOnly=true` 时忽略 LLM 分类；冲突时优先级 |

#### 待新建（按阶段）

| 测试类 | 阶段 | 说明 |
|--------|------|------|
| `KnowledgeQaNodeTest` | 阶段 2 | Mock 向量检索 + LLM，断言不写 `PLANNER_NODE_OUTPUT` |
| `MetaHelpNodeTest` | 阶段 2 | 断言返回 Agent 描述类固定结构 |
| `IntentClassificationIntegrationTest` | 阶段 1 | Mock 各 classification 的 LLM JSON，断言图前 3 个节点序列 |
| `Nl2SqlShortPathIntegrationTest` | 阶段 1 | 《纯查数/SQL》路径不进入 `ReportGeneratorNode` |
| `ClarificationResumeIntegrationTest` | 阶段 3 | 澄清回复不重复跑 Intent（或跳过 Evidence） |

### 2.6.4 用例矩阵（按意图分类）

| 用户输入示例（摘要） | 期望 classification | 期望下一跳 | 不应进入的节点（抽查） |
|---------------------|----------------------|------------|------------------------|
| 「你好」「谢谢」 | 《闲聊或无关指令》 | END | EvidenceRecall、Planner |
| 「查一下所有用户姓名」（单表） | 《纯查数/SQL》 | EvidenceRecall + `IS_ONLY_NL2SQL=true` | ReportGenerator |
| 「对比各地区销售额并出报告」 | 《完整数据分析》 | EvidenceRecall（全流程） | — |
| 「PV 是什么意思」 | 《业务知识问答》 | KnowledgeQaNode（新） | Planner、SqlExecute |
| 「你能查哪些数据」 | 《元问题/系统帮助》 | MetaHelpNode（新） | SchemaRecall |
| 上轮：查工资；本轮：「他们呢？」 | 《多轮追问》或 《完整数据分析》 | EvidenceRecall | END |
| 上轮 Feasibility 反问后：「指的是华东区」 | 《澄清回复》 | 续跑 / 跳过 Intent | 全量重跑 Schema（视设计） |
| 「把 A 表同步到 B 库」 | 《数据同步任务》 | SeaTunnelTaskNode（新） | Planner |
| 「查所有员工身份证号」（无权限） | 《越权/敏感查询》 | END + 合规提示 | SqlExecute |
| LLM 返回 null / 非法 JSON | — | END（兜底） | EvidenceRecall |
| LLM 返回未定义分类字符串 | — | 默认 → 完整分析或 END（设计定一） | — |

**多轮场景**需在用例中显式构造 `MULTI_TURN_CONTEXT` 非 `(无)` 的 state。

### 2.6.5 Prompt 与 LLM 相关测试

#### 单元层（不调用 LLM）

- `PromptHelper.buildIntentRecognitionPrompt(multiTurn, query)` 输出包含 `{multi_turn}`、`{latest_query}`、`{format}`  
- `JsonParseUtil` 能解析各分类的标准 JSON 样例  
- 非法 JSON、多余字段、分类字符串前后空格 → 行为符合预期  

#### 评测集（可调用 LLM）

建议在 `data-agent-management/src/test/resources/eval/intent-classification/` 维护：

```
cases.json          # [{ "query", "multi_turn", "expected_classification", "tags" }]
README.md           # 如何本地跑评测
```

- **规模**：P0 每类 ≥ 10 条；边界/多轮 ≥ 5 条  
- **指标**：Top-1 准确率、闲聊误杀率、纯 SQL 误判为完整分析率  
- **CI 策略**：MR 跑 Mock 单测；nightly 或手动跑 LLM 评测  

### 2.6.6 集成测试断言点

1. **节点序列**：收集 `NodeOutput.node()` 名称列表，与期望前缀比对  
2. **State 标志**：《纯查数/SQL》后 `IS_ONLY_NL2SQL == true`  
3. **早停**：《闲聊》流中不出现 `PlannerNode`、`SqlExecuteNode`  
4. **耗时/步数**（可选）：短路径节点数 < 完整路径  

### 2.6.7 与 `nl2sqlOnly` 参数的优先级测试

| 场景 | 请求参数 | LLM 分类 | 期望行为 |
|------|----------|----------|----------|
| 参数优先 | `nl2sqlOnly=true` | 《完整数据分析》 | 仍走 SQL 短链 |
| 参数优先 | `nl2sqlOnly=false` | 《纯查数/SQL》 | 走 SQL 短链 |
| 一致 | `nl2sqlOnly=true` | 《纯查数/SQL》 | SQL 短链 |
| 冲突（需产品定） | `nl2sqlOnly=false` | 《纯查数/SQL》 | 以 LLM 或参数为准，文档写清 |

### 2.6.8 前端与 API 测试（可选）

- SSE 响应是否携带 `classification`  
- 《元问题》《合规拒绝》的 `textType` / 固定文案展示  
- 「停止生成」走 `stopStreamProcessing`，不触发 Intent  

### 2.6.9 测试实施 Checklist

**阶段 1（P0：纯 SQL + 完整分析）**

- [ ] 扩展 `IntentRecognitionDispatcherTest`  
- [ ] 扩展 `IntentRecognitionNodeTest`  
- [ ] 新增 `IntentClassificationIntegrationTest`  
- [ ] `GraphServiceImplTest`：`nl2sqlOnly` 与 Intent 优先级  
- [ ] 建立 `eval/intent-classification/cases.json` 初版（≥ 20 条）  

**阶段 2（P1：知识问答 + 系统帮助）**

- [ ] `KnowledgeQaNodeTest`、`MetaHelpNodeTest`  
- [ ] 集成：知识问答题不进 Planner  
- [ ] 评测集补充 P1 语料  

**阶段 3（P2：澄清回复 + 操作指令）**

- [ ] `ClarificationResumeIntegrationTest`  
- [ ] 操作指令不进入 Graph 的 Controller/Service 测试  

**阶段 4～5**

- [ ] SeaTunnel、合规类 Dispatcher + 集成用例  
- [ ] 权限相关用例依赖 Agent/数据源权限 Mock  

### 2.6.10 通过标准

- P0 评测集准确率 ≥ 90%（或项目约定阈值）  
- 全部 Intent 相关单测、Dispatcher 测试绿灯  
- 集成测试覆盖「每类至少 1 条 happy path + 1 条边界」  
- 改造前后闲聊拦截用例零回归失败  

---

# 三、MultiTurnContextManager 持久化

> **状态**：计划中  
> **关联模块**：`MultiTurnContextManager`、`GraphServiceImpl`、`AgentRun.vue`、`chat_session`  
> **全局优先级**：**P0**（多轮追问、澄清回复的前置能力）

## 3.1 背景与问题

`MultiTurnContextManager` 用内存 `ConcurrentHashMap` 维护对话轮次，代码已有 `// todo：考虑持久化存储`。

- `GraphServiceImpl` 每轮调用 `buildContext(threadId)`；history 为空则返回 `(无)`  
- **重启后**同一 `threadId` 上下文丢失  
- **`chat_message`** 仅存 UI 消息，不含 Planner 计划文本  
- **threadId 不稳定**：页面刷新后前端为 `null`，后端生成新 UUID  

## 3.2 目标

1. **DB 持久化**：`finishTurn` 写库；`buildContext` 未命中内存时懒加载  
2. **稳定 threadId**：首轮即将 `chat_session.id` 作为 `threadId`  
3. **重启可恢复**：受 `maxturnhistory` 限制恢复最近 N 轮  

## 3.3 实现方案

### 后端

1. **表** `conversation_turn`：`thread_id`、`user_question`、`plan`、`turn_order`、`create_time`  
2. **`MultiTurnContextManager`**：内存 + DB 双写；淘汰/sync `restartLastTurn`  
3. **`GraphServiceImpl`**：逻辑不变，持久化封装在 Manager 内  
4. **Schema**：更新 `schema.sql`、`h2/schema-h2.sql`、Mapper / Entity  

### 前端

- `AgentRun.vue`：`threadId: sessionState.lastRequest?.threadId \|\| currentSession.value.id`  
- `sessionStateManager.ts`：sessionId 即 threadId，无需 localStorage  

### 配置

- 沿用 `maxturnhistory`、`maxplanlength`  
- 可选 `multi-turn.persist-enabled`（默认 true）  

## 3.4 验收标准

- [ ] 重启后端后第 3 轮 `buildContext` 含前两轮「问 + 计划」  
- [ ] 刷新页面后多轮上下文可恢复  
- [ ] 超过 `maxturnhistory` 时 DB 与内存一致淘汰  
- [ ] Human Feedback `restartLastTurn` 后 DB 与内存一致  
- [ ] `MultiTurnContextManagerTest` 补充持久化单测  

## 3.5 实施 Checklist

- [ ] `conversation_turn` 表及 MyBatis Mapper  
- [ ] `MultiTurnContextManager` DB 读写与懒加载  
- [ ] 前端 `sessionId` 作默认 `threadId`  
- [ ] 更新 `docs/ARCHITECTURE.md`  
- [ ] 单测 / 集成测  

## 3.6 风险与注意事项

1. **threadId 语义变更**：确认与 Human Feedback、`RunnableConfig.threadId` 无冲突  
2. **旧会话迁移**：升级后从空历史开始，可接受  
3. **plan 为空**：`finishTurn` 不写 history（与现逻辑一致）  
4. **知识问答 Intent**：是否入 history 待 [2.4](#24-风险与注意事项) 单独定义  

---

# 四、其他待办方向（按主题索引）

> 条目已纳入 [第一章总览](#一待办总览按优先级) 排序；本节按模块归类，仅列方向。

## 4.1 生产化与运维

- [ ] API Key 鉴权拦截器（`X-API-Key`）  
- [ ] 向量库持久化（pgvector / ES / Hybrid）  
- [ ] Langfuse / 指标：node 耗时、classification、SQL 重试  
- [ ] 配置分环境：生产关闭 `spring.sql.init` 自动灌数  
- [ ] 离线/内网：报告 CDN（marked、echarts）可配置  
- [ ] Docker Compose 全栈、健康检查、优雅停机  
- [ ] 结构化日志 + traceId  

## 4.2 安全与合规

- [ ] SQL 沙箱：只读连接、表白名单、行数/超时  
- [ ] Agent / 数据源 / 表级权限  
- [ ] 敏感字段脱敏与审计日志  

## 4.3 数据连接与性能

- [ ] Connector / DDL / 连接池 Factory 缓存  
- [ ] 数据源健康检查与友好错误  
- [ ] Schema 召回、向量检索性能优化  
- [ ] 连接池监控与泄漏排查  

## 4.4 知识库与 RAG

- [ ] TextSplitter 策略前端可配  
- [ ] 混合检索 Fusion 补全（`WeightedAverageStrategy`）  
- [ ] 向量按 metadata 批量删除/更新  
- [ ] 知识库增量更新、重建索引  
- [ ] 大文件 Embedding 批处理失败重试  

## 4.5 Graph 工作流

- [ ] nl2sql 图级短路  
- [ ] Plan 校验时机下沉（`PlanExecutorNode` TODO）  
- [ ] Human Feedback / 澄清续跑状态机  
- [ ] 流式中断、取消、超时  
- [ ] 并发限流与背压  
- [ ] Plan Repair 上限；checkpoint 续跑  

## 4.6 数据工程（SeaTunnel 等）

- [ ] 通用 CLI Executor 抽象  
- [ ] 作业状态查询与结果回传 Node  
- [ ] 与 Intent《数据同步任务》打通（见 [2.2 方向 D](#方向-d数据工程扩展seatunnel-方向)）  

## 4.7 NL2SQL 与语义层

- [ ] SQL 评测闭环（`maxSqlRetryCount`、`sqlScoreThreshold`）  
- [ ] 多数据库方言扩展  
- [ ] Schema 变更后元数据自动刷新  
- [ ] 语义模型批量导入与维护  
- [ ] 预设问题、Agent 模板 / 一键克隆  

## 4.8 Python 分析链

- [ ] Code Executor 新模式  
- [ ] Docker 沙箱：资源配额、镜像、网络隔离  
- [ ] 失败降级与可信度标注  

## 4.9 API 与集成

- [ ] MCP 工具扩展；`McpServerConfig` 合并  
- [ ] OpenAPI / SDK  
- [ ] Webhook / 异步长任务  

## 4.10 前端与体验

- [ ] 列表分页与搜索  
- [ ] 节点级进度条  
- [ ] SSE 错误/中断态  
- [ ] 意图 classification 展示  
- [ ] 前端 i18n  

## 4.11 工程质量

- [ ] 全局异常处理与错误码（`DatasourceController` 等）  
- [ ] NL2SQL 异常信息返回用户（`Nl2SqlServiceImpl`）  
- [ ] Mapper 返回值校验或 AOP  
- [ ] DTO 规范化  
- [ ] `@Deprecated` API 清理  
- [ ] `Agent.status` 改枚举  

## 4.12 评测与文档

- [ ] Intent 离线评测集（见 [2.6](#26-测试计划)）  
- [ ] NL2SQL / Planner / Feasibility eval 目录  
- [ ] 按 `nodes-dependency-analysis.md` 补 Node 单测  
- [ ] CI：MR Mock；nightly LLM  

## 4.13 数据与运维

- [ ] Agent / 知识库 / 向量索引备份与迁移  
- [ ] OSS 文件重复读修复  

## 4.14 产品能力

- [ ] 多 Agent 路由与切换  
- [ ] 报告导出、会话分享、结果缓存  
