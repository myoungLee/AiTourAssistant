<!-- @author myoung -->

# 项目协作约束

## 默认语言

- 本项目中的解释、计划、总结、评审意见和文档默认使用简体中文。
- 用户明确要求其他语言时，按用户要求执行。
- 代码标识符、类名、方法名、配置键、数据库字段名和 API 路径保持英文。

## 沟通风格

- 先给结论，再给必要细节。
- 涉及实现时说明关键取舍、影响范围和验证方式。
- 不用空泛描述，不夸大完成情况。

## 文档要求

- 项目设计、实施计划、接口说明和变更总结默认写中文。
- Markdown 文档中的技术名词可以保留英文原文。
- 涉及 API、数据库、配置和命令时使用代码块或行内代码标记。

## 工程约束

- 优先遵循现有项目结构和命名风格。
- 修改代码前先理解相关模块边界。
- 不随意引入新依赖、中间件或架构层，除非用户确认。
- 后端开发、测试和运行统一使用 JDK 21，整体基于 Spring Boot 开发，使用内嵌 Tomcat 作为 Web 服务器。
- 后端项目结构按照 `controller`、`service`、`service.impl`、`domain`、`mapper`、`client`、`config`、`common` 组织：
  - `controller`：接收请求、参数校验、返回结果。
  - `service`：编排业务流程的接口定义，Controller 和其他上层模块优先依赖接口。
  - `service.impl`：`service` 接口的具体实现类，实现类命名统一使用 `XxxServiceImpl`。
  - `domain`：核心业务模型和业务规则。
  - `mapper`：访问数据库。
  - `client`：调用第三方服务，例如天气、地图、大模型和 MCP 工具适配。
  - `config`：配置类。
  - `common`：通用工具、异常、统一响应对象、DTO、Entity、VO。
- 数据库连接池统一使用 Druid，除非用户明确确认，不切换为其他连接池。
- Controller 层只负责接收请求参数、参数校验和返回结果；普通 REST 接口统一返回 `Result<T>`。
- Controller 方法优先接收独立请求参数，不直接接收完整 Request DTO；如需传入 Service，可在 Controller 内用参数组装 DTO。
- Controller 参数命名必须和 DTO、Entity、VO 字段保持一致，避免前后端字段语义分裂。
- SSE 等流式接口可以按协议返回 `SseEmitter`，不强行包裹普通 JSON `Result`。
- 大模型接入统一使用 Spring AI 官方 `ChatClient` / OpenAI Starter，配置使用 `spring.ai.openai.*`；除模型 Key 从环境变量读取外，模型地址、模型名、推理等级等写入配置文件。
- 不手写 `java.net.http.HttpClient` 或其他底层 HTTP 客户端直连大模型接口，除非用户后续明确要求绕开 Spring AI。
- 不提交密钥、Token、数据库密码或其他敏感信息。
- 测试、构建、运行结果必须基于实际命令输出，不凭推测声明通过。
- 新增或修改代码文件时，文件顶部必须添加作者注释：`@author myoung`。
- 每个类和每个方法都要添加说明职责的注释，关键代码、关键流程、复杂逻辑或重要取舍必须添加有效注释；避免只复述语法的无效注释。

## 阶段交付流程

- 后续开发按 `docs/superpowers/plans/2026-05-15-ai-tour-assistant-current-architecture-plan.md` 中的阶段顺序推进。
- `docs/superpowers/plans/2026-05-14-*.md` 是历史拆分计划，仅作背景参考，文件中的旧代码片段和旧配置不再作为执行依据。
- 每个阶段完成后必须运行对应测试、构建或冒烟验证。
- 提交前必须检查 `git diff` 和 `git status`，确认 `.superpowers/`、日志、密钥、临时文件和本地 AI 产物没有进入暂存区。
- 每个阶段提交前做一次简短 review，说明本阶段修改内容、风险和未完成项。
- 阶段产物及时提交并推送到 `origin/main`。
- 后续提交说明和提交消息默认使用简体中文，除非用户明确要求英文。
- 推送完成后用简短中文说明推送内容，例如：`已推送：基础工程骨架、Docker Compose、后端健康检查、前端基础页`。
- 不强推覆盖远程历史；如果远程有新提交，先拉取并合并，再推送。
