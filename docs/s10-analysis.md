# S10 团队协议分析报告

## 一、S10 原版设计方案概述

### 1.1 设计目标
S10 引入 **Request-Response 协议模式**，解决 s09 中缺少结构化协调的问题：
- **优雅关机**：通过握手协议避免直接杀线程导致的数据不一致
- **计划审批**：高风险变更需要先提交审批，避免队友立即执行危险操作

### 1.2 核心机制

#### 协议模式
```
Request-Response Pattern:
1. 发起方生成唯一 request_id
2. 通过消息总线发送请求
3. 响应方引用相同 request_id 回复
4. 发起方根据响应更新状态
```

#### 两种协议

**Shutdown Protocol（关机协议）**
```
Lead                    Teammate
  |                        |
  |--shutdown_request----->|
  | {req_id: "abc"}        |
  |                        |
  |<--shutdown_response----|
  | {req_id: "abc",        |
  |  approve: true}        |
```

**Plan Approval Protocol（计划审批协议）**
```
Teammate                Lead
  |                        |
  |--plan_request--------->|
  | {req_id: "xyz",        |
  |  plan: "..."}          |
  |                        |
  |<--plan_response--------|
  | {req_id: "xyz",        |
  |  approve: true}        |
```

#### 状态机（FSM）
```
[pending] --approve--> [approved]
[pending] --reject---> [rejected]
```

#### 请求跟踪器
```python
shutdown_requests = {
    req_id: {
        target: "teammate_name",
        status: "pending|approved|rejected"
    }
}

plan_requests = {
    req_id: {
        from: "teammate_name",
        plan: "plan_description",
        status: "pending|approved|rejected"
    }
}
```

### 1.3 新增工具
| 工具名 | 描述 | 使用者 |
|--------|------|--------|
| shutdown_request | 发起关机请求 | Lead |
| shutdown_response | 响应关机请求 | Teammate |
| plan_request | 提交计划审批 | Teammate |
| plan_response | 审批计划 | Lead |

---

## 二、当前 Demo 实现分析

### 2.1 架构概览

```
┌─────────────────────────────────────────────────────────────┐
│                        AgentLoop                            │
│  - 主循环控制                                                │
│  - 上下文压缩（三层）                                         │
│  - Lead inbox 自动检查                                       │
└────────────────┬────────────────────────────────────────────┘
                 │
                 ▼
┌─────────────────────────────────────────────────────────────┐
│                      ToolRegistry                           │
│  - 基础工具（bash, file, todo, task, background）           │
│  - 父端专属工具（spawn_teammate, list_teammates, broadcast）│
│  - 团队工具（send_message, read_inbox）                     │
└────────────────┬────────────────────────────────────────────┘
                 │
                 ▼
┌─────────────────────────────────────────────────────────────┐
│                    TeammateManager                          │
│  - spawn(): 创建/启动队友                                    │
│  - teammateLoop(): 队友主循环                                │
│  - 状态管理：IDLE, WORKING, SHUTDOWN                         │
│  - ExecutorService 线程池                                   │
└────────────────┬────────────────────────────────────────────┘
                 │
                 ▼
┌─────────────────────────────────────────────────────────────┐
│                      MessageBus                             │
│  - send(): 发送消息到 JSONL 收件箱                           │
│  - readInbox(): 读取并清空收件箱                             │
│  - broadcast(): 广播消息                                     │
└────────────────┬────────────────────────────────────────────┘
                 │
                 ▼
┌─────────────────────────────────────────────────────────────┐
│                  文件系统持久化                              │
│  .team/config.json    - 团队配置                            │
│  .team/inbox/*.jsonl  - 消息收件箱                          │
└─────────────────────────────────────────────────────────────┘
```

### 2.2 核心组件详解

#### 2.2.1 TeammateManager
**职责**：
- 队友生命周期管理（spawn, shutdown）
- 队友主循环执行
- 状态持久化

**关键方法**：
```java
public String spawn(String name, String role, String prompt)
- 检查队友是否已存在
- 更新 TeamConfig 状态为 WORKING
- 提交 teammateLoop 到线程池
- 返回成功消息

private void teammateLoop(String name, String role, String prompt)
- 构建系统提示词
- 循环调用 LLM（最多 50 轮）
- 自动检查 inbox
- 执行工具调用
- 完成后更新状态为 IDLE
```

**状态管理**：
```java
enum SessionStatus {
    IDLE,     // 空闲
    WORKING   // 工作中
}

// TeamConfig 中的状态
"IDLE" | "WORKING" | "SHUTDOWN"
```

#### 2.2.2 MessageBus
**职责**：
- 基于文件系统的消息传递
- 收件箱管理

**存储格式**：
```
.team/inbox/
  ├── lead.jsonl      # Lead 的收件箱
  ├── alice.jsonl     # Alice 的收件箱
  └── bob.jsonl       # Bob 的收件箱
```

**消息格式**：
```json
{
  "type": "MESSAGE",
  "from": "alice",
  "content": "Task completed",
  "timestamp": 1714622000000
}
```

#### 2.2.3 Message
**支持的消息类型**：
```java
public static final Set<String> VALID_TYPES = Set.of(
    "MESSAGE",                    // 普通消息
    "BROADCAST",                  // 广播消息
    "SHUTDOWN_REQUEST",           // 关机请求（预留）
    "SHUTDOWN_RESPONSE",          // 关机响应（预留）
    "PLAN_APPROVAL_RESPONSE"      // 计划审批响应（预留）
);
```

#### 2.2.4 TeamConfig
**持久化数据**：
```json
{
  "teamName": "default",
  "members": [
    {
      "name": "alice",
      "role": "backend-dev",
      "status": "WORKING",
      "spawnedAt": "2026-05-02T05:00:00Z"
    }
  ]
}
```

### 2.3 工具接口分析

#### 已实现的团队工具

| 工具名 | 角色 | 功能 | 输入参数 |
|--------|------|------|----------|
| spawn_teammate | Lead | 创建队友 | name, role, prompt |
| send_message | Both | 发送消息 | to, content, msg_type |
| read_inbox | Both | 读取收件箱 | 无 |
| list_teammates | Lead | 列出队友 | 无 |
| broadcast | Lead | 广播消息 | content |

#### spawn_teammate 详细接口
```java
输入：
{
  "name": "alice",           // 必填，唯一标识
  "role": "backend-dev",     // 必填，角色描述
  "prompt": "Implement API"  // 必填，初始任务
}

输出：
"Spawned 'alice' (role: backend-dev)"
或
"Error: 'alice' is currently WORKING"
```

#### send_message 详细接口
```java
输入：
{
  "to": "alice",             // 必填，接收者
  "content": "Hello",        // 必填，消息内容
  "msg_type": "MESSAGE"      // 可选，默认 MESSAGE
}

输出：
"Sent MESSAGE to alice"
```

### 2.4 数据流分析

#### Spawn 流程
```
用户输入
  ↓
AgentLoop 调用 spawn_teammate
  ↓
SpawnTeammateTool.execute()
  ↓
TeammateManager.spawn()
  ├─ 检查 TeamConfig
  ├─ 更新状态为 WORKING
  ├─ 保存 config.json
  └─ 提交 teammateLoop 到线程池
      ↓
  teammateLoop 开始执行
      ├─ 构建系统提示词
      ├─ 循环调用 LLM
      ├─ 检查 inbox
      ├─ 执行工具
      └─ 完成后更新为 IDLE
```

#### 消息传递流程
```
Alice 调用 send_message
  ↓
SendMessageTool.execute()
  ↓
MessageBus.send()
  ↓
写入 .team/inbox/lead.jsonl
  ↓
Lead 的 AgentLoop 自动检查 inbox
  ↓
MessageBus.readInbox("lead")
  ├─ 读取所有消息
  ├─ 清空文件
  └─ 返回消息列表
      ↓
  消息注入到 chatHistory
      ↓
  LLM 处理消息
```

---

## 三、功能对比分析

### 3.1 已实现功能（S09 级别）

| 功能 | 状态 | 说明 |
|------|------|------|
| ✅ 队友创建 | 完整 | spawn_teammate 工具 |
| ✅ 队友列表 | 完整 | list_teammates 工具 |
| ✅ 消息发送 | 完整 | send_message 工具 |
| ✅ 收件箱读取 | 完整 | read_inbox 工具 |
| ✅ 广播消息 | 完整 | broadcast 工具 |
| ✅ 状态持久化 | 完整 | TeamConfig + JSONL |
| ✅ 线程管理 | 完整 | ExecutorService |
| ✅ Lead 自动检查 inbox | 完整 | AgentLoop 集成 |

### 3.2 缺失功能（S10 级别）

| 功能 | 状态 | 影响 |
|------|------|------|
| ❌ Shutdown Protocol | 未实现 | 无法优雅关机 |
| ❌ Plan Approval Protocol | 未实现 | 无法审批高风险操作 |
| ❌ Request-Response 跟踪器 | 未实现 | 无法关联请求和响应 |
| ❌ FSM 状态机 | 未实现 | 无法管理协议状态 |
| ❌ shutdown_request 工具 | 未实现 | Lead 无法请求关机 |
| ❌ shutdown_response 工具 | 未实现 | Teammate 无法响应关机 |
| ❌ plan_request 工具 | 未实现 | Teammate 无法提交计划 |
| ❌ plan_response 工具 | 未实现 | Lead 无法审批计划 |

### 3.3 差距总结

**当前实现级别**：**S09（基础团队通信）**

**目标级别**：**S10（结构化协议）**

**核心差距**：
1. **协议层缺失**：没有 Request-Response 协议实现
2. **状态机缺失**：没有 pending → approved/rejected 的 FSM
3. **跟踪器缺失**：没有 shutdown_requests 和 plan_requests
4. **工具缺失**：缺少 4 个协议工具

**已有优势**：
1. ✅ 消息类型已预留（Message.VALID_TYPES）
2. ✅ 基础通信机制完善
3. ✅ 状态持久化机制完善
4. ✅ 线程管理机制完善

---

## 四、架构优缺点分析

### 4.1 优点

#### 1. 清晰的分层架构
```
AgentLoop (控制层)
    ↓
ToolRegistry (工具层)
    ↓
TeammateManager (管理层)
    ↓
MessageBus (通信层)
    ↓
文件系统 (持久化层)
```

#### 2. 良好的扩展性
- 工具注册机制：易于添加新工具
- 角色区分：AgentRole.PARENT vs TEAMMATE
- 消息类型预留：已为 S10 协议预留类型

#### 3. 可靠的持久化
- TeamConfig：JSON 格式，易于调试
- MessageBus：JSONL 格式，支持追加写入
- 状态恢复：重启后可恢复团队状态

#### 4. 线程安全
- ConcurrentHashMap：sessions 并发安全
- ExecutorService：线程池管理
- 文件锁：避免并发写入冲突（隐式）

### 4.2 缺点

#### 1. 缺少协议层抽象
**问题**：没有统一的协议处理框架
**影响**：
- 每个协议需要重复实现跟踪器
- 状态机逻辑分散
- 难以扩展新协议

**建议**：引入 ProtocolManager
```java
class ProtocolManager {
    Map<String, RequestTracker> trackers;
    
    String initiateRequest(String protocol, Map<String, Object> params);
    void handleResponse(String requestId, boolean approve, String reason);
    RequestStatus getStatus(String requestId);
}
```

#### 2. 队友关机不优雅
**问题**：直接依赖线程池的 shutdownNow()
**影响**：
- 队友可能在执行工具时被中断
- 文件写入可能不完整
- 状态可能不一致

**建议**：实现 Shutdown Protocol

#### 3. 缺少错误恢复机制
**问题**：队友异常退出后无法自动恢复
**影响**：
- 状态可能停留在 WORKING
- 需要手动清理 config.json

**建议**：
- 添加心跳检测
- 异常时自动更新状态
- 支持队友重启

#### 4. 消息顺序无保证
**问题**：并发写入 JSONL 可能乱序
**影响**：
- 消息可能不按发送顺序到达
- 协议握手可能失败

**建议**：
- 添加消息序列号
- 使用文件锁或消息队列

#### 5. 缺少监控和日志
**问题**：难以追踪队友行为
**影响**：
- 调试困难
- 无法审计操作

**建议**：
- 集成 MonitorLogger
- 记录所有工具调用
- 记录协议握手过程

#### 6. 协议缺少超时机制
**问题**：请求可能永远停留在 pending 状态
**影响**：
- 队友崩溃或卡死时，Lead 无法知道何时放弃等待
- 内存中的 pending 请求无限累积

**建议**：
```java
class ProtocolManager {
    private final ScheduledExecutorService scheduler;
    
    public String initiateRequest(String type, String target,
                                   Map<String, Object> data, 
                                   Duration timeout) {
        String requestId = generateId();
        Request req = new Request(requestId, type, target, "pending", data);
        requests.put(requestId, req);
        
        // 超时自动标记为 timeout 状态
        scheduler.schedule(() -> {
            if ("pending".equals(req.getStatus())) {
                req.setStatus("timeout");
                req.setReason("No response within " + timeout);
            }
        }, timeout.toMillis(), TimeUnit.MILLISECONDS);
        
        return requestId;
    }
}
```

#### 7. 协议状态未持久化
**问题**：`shutdown_requests` 和 `plan_requests` 只在内存中
**影响**：
- Lead 重启后丢失所有 pending 请求
- 无法审计协议历史
- 调试困难

**建议**：持久化到文件系统
```
.team/
  protocols/
    shutdown_requests.jsonl    # 追加写入
    plan_requests.jsonl
```

每次状态变更时追加一行：
```json
{"request_id":"abc123","type":"shutdown","target":"alice","status":"pending","timestamp":1714622000000}
{"request_id":"abc123","type":"shutdown","target":"alice","status":"approved","timestamp":1714622030000}
```

#### 8. 缺少协议取消机制
**问题**：Lead 无法撤回已发送的请求
**影响**：
- Lead 改变主意后无法取消
- 队友可能响应已过期的请求

**建议**：添加 CancelRequestTool
```java
class CancelRequestTool implements AgentTool {
    public String execute(Map<String, Object> input) {
        String requestId = (String) input.get("request_id");
        Request req = protocolManager.getRequest(requestId);
        if (!"pending".equals(req.getStatus())) {
            return "Cannot cancel: already " + req.getStatus();
        }
        
        req.setStatus("cancelled");
        messageBus.send("lead", req.getTarget(), 
                       "Request cancelled", 
                       "REQUEST_CANCELLED",
                       Map.of("request_id", requestId));
        return "Cancelled request " + requestId;
    }
}
```

#### 9. 缺少协议状态查询工具
**问题**：Lead 无法查看当前有哪些 pending 请求
**影响**：
- 长时间运行的会话中容易忘记 pending 请求
- 无法追踪请求年龄（age）

**建议**：添加 ListPendingRequestsTool
```java
class ListPendingRequestsTool implements AgentTool {
    public String execute(Map<String, Object> input) {
        List<Request> pending = protocolManager.getPendingRequests();
        StringBuilder sb = new StringBuilder("Pending requests:\n");
        for (Request req : pending) {
            long ageSeconds = (System.currentTimeMillis() - req.getTimestamp()) / 1000;
            sb.append(String.format("- %s: %s to %s (age: %ds)\n",
                req.getId(), req.getType(), req.getTarget(), ageSeconds));
        }
        return sb.toString();
    }
}
```

#### 10. 消息类型未版本化
**问题**：未来协议升级可能破坏兼容性
**影响**：
- S11 引入新字段时，老版本队友可能崩溃
- 无法平滑升级

**建议**：协议版本化
```java
class Message {
    private String protocolVersion = "1.0";  // 新增字段
    
    public static final Set<String> VALID_TYPES = Set.of(
        "MESSAGE",
        "BROADCAST",
        "SHUTDOWN_REQUEST:v1",      // 带版本号
        "SHUTDOWN_RESPONSE:v1",
        "PLAN_REQUEST:v1",
        "PLAN_RESPONSE:v1"
    );
}
```

---

## 五、升级到 S10 的实施建议

### 5.1 实施路线图

**调整建议**：先实现 Plan Approval 再实现 Shutdown，降低风险。

#### Phase 1: 协议基础设施（1-2 天）
1. 创建 ProtocolManager（支持超时、持久化）
2. 实现 RequestTracker
3. 实现 FSM 状态机
4. 添加 request_id 生成器
5. 实现协议持久化（.team/protocols/*.jsonl）
6. 集成 MonitorLogger

#### Phase 2: Plan Approval Protocol（1-2 天）
**优先级更高**：风险更低，不涉及线程生命周期
1. 实现 PlanRequestTool
2. 实现 PlanResponseTool
3. 实现 PlanResponseTool（审批工具）
4. 实现 ListPendingRequestsTool（状态查询）
5. 实现 CancelRequestTool（取消机制）
6. 添加测试用例

#### Phase 3: Shutdown Protocol（2-3 天）
**优先级较低**：需要修改线程管理逻辑
1. 实现 ShutdownRequestTool
2. 实现 ShutdownResponseTool
3. 修改 TeammateManager 支持优雅关机
4. 处理队友在工具执行中被中断的情况
5. 添加测试用例

#### Phase 4: 增强功能（1 天）
1. 协议版本化（Message 添加 protocolVersion 字段）
2. 超时与重试机制
3. 协议取消机制
4. 状态查询工具

#### Phase 5: 集成和测试（1 天）
1. 集成到 AgentLoop
2. 端到端测试
3. 混沌测试与性能测试
4. 文档更新

### 5.2 关键代码示例

#### ProtocolManager（增强版）
```java
public class ProtocolManager {
    private final Map<String, Request> requests = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private final Path protocolDir = Paths.get(".team/protocols");
    
    public ProtocolManager() throws IOException {
        Files.createDirectories(protocolDir);
        loadPersistedRequests();  // 启动时恢复持久化的请求
    }
    
    public String initiateRequest(String type, String target, 
                                   Map<String, Object> data, 
                                   Duration timeout) {
        String requestId = UUID.randomUUID().toString().substring(0, 8);
        Request req = new Request(requestId, type, target, "pending", data);
        requests.put(requestId, req);
        
        // 持久化到文件
        persistRequest(req);
        
        // 超时自动标记为 timeout 状态
        scheduler.schedule(() -> {
            if ("pending".equals(req.getStatus())) {
                req.setStatus("timeout");
                req.setReason("No response within " + timeout);
                persistRequest(req);  // 更新持久化状态
            }
        }, timeout.toMillis(), TimeUnit.MILLISECONDS);
        
        return requestId;
    }
    
    public void handleResponse(String requestId, boolean approve, String reason) {
        Request req = requests.get(requestId);
        if (req != null) {
            req.setStatus(approve ? "approved" : "rejected");
            req.setReason(reason);
            persistRequest(req);  // 持久化状态变更
        }
    }
    
    public Request getRequest(String requestId) {
        return requests.get(requestId);
    }
    
    public List<Request> getPendingRequests() {
        return requests.values().stream()
            .filter(r -> "pending".equals(r.getStatus()))
            .collect(Collectors.toList());
    }
    
    private void persistRequest(Request req) {
        Path file = protocolDir.resolve(req.getType() + "_requests.jsonl");
        try (BufferedWriter writer = Files.newBufferedWriter(file, 
                StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
            writer.write(new Gson().toJson(req));
            writer.newLine();
        } catch (IOException e) {
            // 记录错误但不中断流程
            System.err.println("Failed to persist request: " + e.getMessage());
        }
    }
    
    private void loadPersistedRequests() throws IOException {
        // 从 .team/protocols/*.jsonl 恢复 pending 状态的请求
        Files.list(protocolDir)
            .filter(p -> p.toString().endsWith(".jsonl"))
            .forEach(this::loadRequestsFromFile);
    }
    
    @Data
    public static class Request {
        private String id;
        private String type;
        private String target;
        private String status;  // pending, approved, rejected, timeout, cancelled
        private Map<String, Object> data;
        private String reason;
        private long timestamp;
        
        public Request(String id, String type, String target, 
                      String status, Map<String, Object> data) {
            this.id = id;
            this.type = type;
            this.target = target;
            this.status = status;
            this.data = data;
            this.timestamp = System.currentTimeMillis();
        }
    }
}
```

#### ShutdownRequestTool（增强版）
```java
public class ShutdownRequestTool implements AgentTool {
    private final TeammateManager manager;
    private final ProtocolManager protocolManager;
    
    @Override
    public String execute(Map<String, Object> input) {
        String target = (String) input.get("teammate");
        
        // 带超时的请求（默认30秒）
        String requestId = protocolManager.initiateRequest(
            "shutdown", 
            target, 
            Map.of(),
            Duration.ofSeconds(30)
        );
        
        manager.getMessageBus().send(
            "lead", 
            target, 
            "Please shut down gracefully.",
            "SHUTDOWN_REQUEST:v1",  // 带版本号
            Map.of("request_id", requestId, "protocol_version", "1.0")
        );
        
        // 集成 MonitorLogger
        SessionStats.current().recordProtocol(
            "shutdown_request", 
            requestId, 
            Map.of("target", target, "status", "pending")
        );
        
        return "Shutdown request " + requestId + " sent (status: pending)";
    }
}
```

#### ShutdownResponseTool
```java
public class ShutdownResponseTool implements AgentTool {
    private final ProtocolManager protocolManager;
    private final MessageBus messageBus;
    private final String senderName;
    
    @Override
    public String execute(Map<String, Object> input) {
        String requestId = (String) input.get("request_id");
        boolean approve = (boolean) input.getOrDefault("approve", false);
        String reason = (String) input.getOrDefault("reason", "");
        
        protocolManager.handleResponse(requestId, approve, reason);
        
        messageBus.send(
            senderName,
            "lead",
            reason,
            "SHUTDOWN_RESPONSE",
            Map.of("request_id", requestId, "approve", approve)
        );
        
        if (approve) {
            // 触发优雅关机
            return "SHUTDOWN_APPROVED:" + requestId;
        } else {
            return "Shutdown request rejected: " + reason;
        }
    }
}
```

### 5.3 测试策略

#### 单元测试
```java
@Test
void testShutdownProtocol() {
    // 1. Lead 发起关机请求
    String result = shutdownRequestTool.execute(Map.of("teammate", "alice"));
    assertThat(result).contains("pending");
    
    // 2. Alice 收到请求
    List<Message> inbox = messageBus.readInbox("alice");
    assertThat(inbox).hasSize(1);
    assertThat(inbox.get(0).getType()).isEqualTo("SHUTDOWN_REQUEST");
    
    // 3. Alice 批准关机
    String requestId = extractRequestId(inbox.get(0));
    shutdownResponseTool.execute(Map.of(
        "request_id", requestId,
        "approve", true
    ));
    
    // 4. Lead 收到响应
    inbox = messageBus.readInbox("lead");
    assertThat(inbox).hasSize(1);
    assertThat(inbox.get(0).getType()).isEqualTo("SHUTDOWN_RESPONSE");
    
    // 5. 验证状态
    Request req = protocolManager.getRequest(requestId);
    assertThat(req.getStatus()).isEqualTo("approved");
}
```

#### 集成测试
```java
@Test
void testEndToEndShutdown() {
    // 1. Spawn teammate
    teammateManager.spawn("alice", "tester", "Run tests");
    
    // 2. Request shutdown
    String requestId = initiateShutdown("alice");
    
    // 3. Wait for response (with timeout)
    Request req = waitForResponse(requestId, Duration.ofSeconds(10));
    
    // 4. Verify graceful shutdown
    assertThat(req.getStatus()).isEqualTo("approved");
    assertThat(getTeammateStatus("alice")).isEqualTo("SHUTDOWN");
}
```

---

## 六、总结

### 6.1 当前状态
- **实现级别**：S09（基础团队通信）
- **核心功能**：队友创建、消息传递、状态管理
- **架构质量**：良好的分层设计，易于扩展

### 6.2 升级到 S10 的价值
1. **优雅关机**：避免数据不一致和资源泄漏
2. **风险控制**：高风险操作需要审批
3. **可追溯性**：所有协议握手有记录
4. **可扩展性**：协议框架可复用

### 6.3 实施建议
1. **优先级**：先实现 Plan Approval Protocol（风险更低，不涉及线程生命周期）
2. **渐进式**：分阶段实施，每个阶段独立测试
3. **向后兼容**：保持现有 S09 功能不受影响
4. **文档同步**：及时更新 API 文档和使用示例

### 6.4 风险评估
- **低风险**：协议层是新增功能，不影响现有代码
- **中风险**：TeammateManager 需要修改关机逻辑
- **测试覆盖**：需要充分的单元测试和集成测试

---

## 附录：关键类图

```
┌─────────────────────────────────────────────────────────────┐
│                      ProtocolManager                        │
├─────────────────────────────────────────────────────────────┤
│ - requests: Map<String, Request>                            │
├─────────────────────────────────────────────────────────────┤
│ + initiateRequest(type, target, data): String               │
│ + handleResponse(requestId, approve, reason): void          │
│ + getRequest(requestId): Request                            │
└─────────────────────────────────────────────────────────────┘
                           │
                           │ uses
                           ▼
┌─────────────────────────────────────────────────────────────┐
│                    TeammateManager                          │
├─────────────────────────────────────────────────────────────┤
│ - protocolManager: ProtocolManager                          │
│ - messageBus: MessageBus                                    │
│ - sessions: Map<String, TeammateSession>                    │
├─────────────────────────────────────────────────────────────┤
│ + spawn(name, role, prompt): String                         │
│ + requestShutdown(name): String                             │
│ + handleShutdownResponse(requestId, approve): void          │
└─────────────────────────────────────────────────────────────┘
                           │
                           │ uses
                           ▼
┌─────────────────────────────────────────────────────────────┐
│                      MessageBus                             │
├─────────────────────────────────────────────────────────────┤
│ + send(from, to, content, type, metadata): String           │
│ + readInbox(name): List<Message>                            │
└─────────────────────────────────────────────────────────────┘
```
