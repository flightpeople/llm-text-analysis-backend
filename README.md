# LLM 智能文本分析后端服务

基于 Spring Boot 3 + 智谱 GLM 的智能文本分析后端，提供文本摘要、情感分析、翻译能力，内置重试、熔断、缓存、线程池等生产级机制。

## 功能特性

- 文本摘要 / 情感分析 / 多语言翻译（对接 OpenAI 兼容接口，智谱 GLM、DeepSeek 均可接入）
- 失败自动重试（spring-retry，指数退避，最多3次）
- 熔断保护（连续失败快速失败，冷却后半开试探恢复）
- 本地缓存（Caffeine，热点结果复用，同 key 并发加载只执行一次，防击穿）
- 自定义线程池（批量分析并发执行，CallerRuns 背压）
- 统一响应格式与全局异常处理
- JUnit5 + Mockito 单元测试（正常 / 异常 / 并发 / 熔断全场景）

## 技术栈

Spring Boot 3.2 | Java 17 | spring-retry | Apache HttpClient5 | Caffeine | JUnit5 | Mockito

## 架构

```
Controller（统一响应 / 参数校验）
    ↓
Service（业务编排：预处理 → 缓存 → 熔断 → LLM 调用）
    ↓
LlmClient（HTTP 封装：超时控制 / 重试 / 限流处理）
    ↓
智谱 GLM API（OpenAI 兼容）
```

## 快速开始

**无需安装 Maven**——项目自带 Maven Wrapper，首次运行会自动下载 Maven 3.9.9。

0. 设置 JAVA_HOME（JDK 17+，不设置 mvnw 会报错）：
   ```
   set JAVA_HOME=C:\Program Files\Java\jdk-21.0.12.1
   ```
1. 获取 API Key：智谱开放平台（open.bigmodel.cn）注册，创建 API Key（glm-4-flash 免费）
2. 配置环境变量（**不要把 key 写进任何文件或提交到 Git**）：
   - Windows CMD：`set LLM_API_KEY=你的key`
   - Windows PowerShell：`$env:LLM_API_KEY="你的key"`
3. 运行（在项目目录执行）：
   ```
   mvnw.cmd spring-boot:run
   ```
4. 验证：

```bash
curl -X POST http://localhost:8080/api/analysis/summarize \
  -H "Content-Type: application/json" \
  -d '{"text":"Spring Boot 是由 Pivotal 团队提供的全新框架，其设计目的是用来简化新 Spring 应用的初始搭建以及开发过程。"}'
```

**Windows 一键验证**：保持服务窗口打开，双击项目里的 `verify.bat`，会自动发送测试请求并显示 JSON 结果。

> 注意：服务运行期间启动窗口必须保持打开（最后一行显示 `Started Application`），关闭窗口即停止服务。PowerShell 中 `curl` 是 `Invoke-WebRequest` 的别名，参数语法与真 curl 不同，直接用 `verify.bat` 或 `Invoke-RestMethod`。

## API 一览

| 方法 | 路径 | 说明 |
|-----|------|------|
| POST | /api/analysis/summarize | 文本摘要 |
| POST | /api/analysis/emotion | 情感分析 |
| POST | /api/analysis/translate | 翻译（text + targetLang） |
| POST | /api/analysis/batch | 批量分析（texts + type） |

## 测试

```bash
mvn test
```

覆盖范围：
- 服务层：正常流程、缓存命中、业务异常、熔断打开、50线程并发防击穿、批量分析、参数校验
- 熔断器：状态机全路径（CLOSED → OPEN → HALF_OPEN → CLOSED / 重新打开）
- 控制器层：MockMvc 验证统一响应格式与参数校验

**已验证**：JDK 21 + Maven 3.9.9（经 Maven Wrapper）环境下 `mvn test` 全量通过（17 个用例，0 失败）；应用实际启动并完成接口冒烟测试（正常请求走通完整调用链、假 key 返回结构化 503、空文本返回 400 参数校验）。

## 面试讲解要点

- **重试为什么用 spring-retry 而不是 while 循环**：声明式、指数退避、可配置、@Recover 兜底
- **spring-retry 的坑（冒烟测试实测发现）**：对**不可重试的异常同样会触发恢复流程**——@Recover 只定义了 IOException 的恢复方法时，业务异常（如 401）会导致 "Cannot locate recovery method"。修复方式：为每种可能逃逸的异常类型都提供 @Recover
- **熔断器状态机**：CLOSED→OPEN→HALF_OPEN，为什么半开只放行试探请求（防止恢复瞬间被流量打垮）
- **缓存防击穿**：Caffeine 同 key 并发加载只执行一次 mapping function（有单测证明）
- **线程池参数**：核心4 / 最大8 / 队列200 / CallerRuns 背压，为什么不用默认线程池
- **错误码设计**：200 / 400 / 429 / 503 / 504 语义区分，统一响应格式
