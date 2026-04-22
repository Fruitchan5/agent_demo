---
name: software-testing
description: Comprehensive software testing guidance covering unit tests, integration tests, TDD, test strategies, and automation. Use when user asks about writing tests, test coverage, testing frameworks, or test-driven development.
tags: testing, junit, mockito, tdd, automation
---

# Software Testing Skill

你现在是一名软件测试专家，精通各类测试方法、框架和最佳实践。

## 测试金字塔原则

```
        /\
       /  \  E2E Tests (少量，慢，脆弱)
      /----\
     / Inte \  Integration Tests (适量，中速)
    /--------\
   /   Unit   \  Unit Tests (大量，快速，稳定)
  /------------\
```

**比例建议**: 70% 单元测试 + 20% 集成测试 + 10% E2E 测试

---

## 1. 单元测试（JUnit 5 + Mockito）

### 基本结构

```java
@Test
@DisplayName("应该在输入为空时抛出异常")
void shouldThrowExceptionWhenInputIsEmpty() {
    // Given (准备)
    UserService service = new UserService();
    
    // When (执行)
    Exception ex = assertThrows(IllegalArgumentException.class, 
        () -> service.createUser(""));
    
    // Then (断言)
    assertEquals("用户名不能为空", ex.getMessage());
}
```

### 常用断言

```java
// JUnit 5 断言
assertEquals(expected, actual);
assertNotNull(obj);
assertTrue(condition);
assertThrows(Exception.class, () -> code());
assertAll(
    () -> assertEquals(1, a),
    () -> assertEquals(2, b)
);

// AssertJ (更流畅)
assertThat(list)
    .hasSize(3)
    .contains("item1")
    .doesNotContainNull();
```

### Mockito 使用

```java
@ExtendWith(MockitoExtension.class)
class UserServiceTest {
    @Mock
    private UserRepository repo;
    
    @InjectMocks
    private UserService service;
    
    @Test
    void shouldSaveUser() {
        // 模拟依赖行为
        when(repo.save(any(User.class)))
            .thenReturn(new User(1L, "test"));
        
        User result = service.createUser("test");
        
        // 验证调用
        verify(repo, times(1)).save(any(User.class));
        assertThat(result.getId()).isEqualTo(1L);
    }
}
```

### 参数化测试

```java
@ParameterizedTest
@CsvSource({
    "1, 1, 2",
    "2, 3, 5",
    "-1, 1, 0"
})
void shouldAddNumbers(int a, int b, int expected) {
    assertEquals(expected, calculator.add(a, b));
}

@ParameterizedTest
@ValueSource(strings = {"", "  ", "\t", "\n"})
void shouldRejectBlankInput(String input) {
    assertThrows(IllegalArgumentException.class, 
        () -> validator.validate(input));
}
```

---

## 2. 集成测试（Spring Boot）

### 基本配置

```java
@SpringBootTest
@AutoConfigureMockMvc
class AgentApplicationIntegrationTest {
    
    @Autowired
    private MockMvc mockMvc;
    
    @Autowired
    private ToolRegistry registry;
    
    @Test
    void shouldLoadAllTools() {
        assertThat(registry.getAllTools())
            .hasSize(5)
            .extracting("name")
            .contains("bash", "read_file", "todo");
    }
}
```

### 数据库集成测试（TestContainers）

```java
@Testcontainers
@SpringBootTest
class DatabaseIntegrationTest {
    
    @Container
    static PostgreSQLContainer<?> postgres = 
        new PostgreSQLContainer<>("postgres:15");
    
    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
    }
    
    @Test
    void shouldPersistUser(@Autowired UserRepository repo) {
        User user = repo.save(new User("test"));
        assertThat(user.getId()).isNotNull();
    }
}
```

---

## 3. TDD 流程（测试驱动开发）

### 红-绿-重构循环

```
1. 🔴 Red   — 写一个失败的测试（定义期望行为）
2. 🟢 Green — 写最简单的代码让测试通过
3. 🔵 Refactor — 重构代码，保持测试通过
```

### 示例：实现 MemoryManager

```java
// 1. 先写测试（Red）
@Test
void shouldAddMemoryWithTimestamp() {
    MemoryManager manager = new MemoryManager(tempFile);
    manager.add("用户叫小明");
    
    String content = manager.read();
    assertThat(content).contains("用户叫小明");
    assertThat(content).containsPattern("\\[\\d{4}-\\d{2}-\\d{2}");
}

// 2. 实现最简代码（Green）
public String add(String content) {
    String entry = "[" + LocalDateTime.now() + "] " + content + "\n";
    Files.writeString(file, entry, APPEND);
    return "已记住";
}

// 3. 重构（Refactor）
// 提取时间格式化、异常处理等
```

---

## 4. 测试策略与技巧

### 边界值分析

```java
@ParameterizedTest
@ValueSource(ints = {-1, 0, 1, 99, 100, 101})
void shouldValidateAge(int age) {
    if (age < 0 || age > 100) {
        assertThrows(IllegalArgumentException.class, 
            () -> validator.validateAge(age));
    } else {
        assertDoesNotThrow(() -> validator.validateAge(age));
    }
}
```

### 等价类划分

```java
// 输入分类：空字符串、纯空格、正常字符串、超长字符串
@ParameterizedTest
@MethodSource("provideInputs")
void shouldHandleVariousInputs(String input, boolean valid) {
    if (valid) {
        assertDoesNotThrow(() -> service.process(input));
    } else {
        assertThrows(Exception.class, () -> service.process(input));
    }
}

static Stream<Arguments> provideInputs() {
    return Stream.of(
        Arguments.of("", false),           // 空
        Arguments.of("   ", false),        // 纯空格
        Arguments.of("valid", true),       // 正常
        Arguments.of("a".repeat(1001), false) // 超长
    );
}
```

### 测试覆盖率目标

- **核心业务逻辑**: 90%+
- **工具类/Utils**: 80%+
- **配置/启动类**: 50%+（主要测试关键路径）

```bash
# Maven 生成覆盖率报告
mvn clean test jacoco:report
# 查看 target/site/jacoco/index.html
```

---

## 5. Mock 与 Stub 策略

### 何时 Mock

- ✅ 外部依赖（数据库、HTTP、文件系统）
- ✅ 慢速操作（LLM API 调用）
- ✅ 不稳定的服务（第三方 API）

### 何时用真实对象

- ✅ 简单 POJO / Value Object
- ✅ 纯函数工具类
- ✅ 被测试的核心逻辑

```java
// ❌ 过度 Mock
@Mock private String name;  // String 不应该 mock

// ✅ 合理 Mock
@Mock private LlmClient llmClient;
@Mock private HttpClient httpClient;
```

---

## 6. 常见测试陷阱

### ❌ 测试实现细节

```java
// 坏例子：测试私有方法
@Test
void testPrivateMethod() throws Exception {
    Method method = MyClass.class.getDeclaredMethod("privateHelper");
    method.setAccessible(true);
    // ...
}

// 好例子：测试公共行为
@Test
void shouldReturnCorrectResult() {
    assertEquals(expected, service.publicMethod(input));
}
```

### ❌ 脆弱的断言

```java
// 坏例子：依赖具体时间
assertEquals("2026-04-21 11:30", result.getTimestamp());

// 好例子：验证格式或范围
assertThat(result.getTimestamp())
    .matches("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}");
```

### ❌ 测试间依赖

```java
// 坏例子：测试顺序依赖
@Test void test1() { state = "A"; }
@Test void test2() { assertEquals("A", state); } // 依赖 test1

// 好例子：每个测试独立
@BeforeEach void setUp() { state = "A"; }
```

---

## 7. 性能测试（JMH）

```java
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Thread)
public class ToolRegistryBenchmark {
    
    private ToolRegistry registry;
    
    @Setup
    public void setup() {
        registry = new ToolRegistry();
        // 注册 100 个工具
    }
    
    @Benchmark
    public AgentTool testGetTool() {
        return registry.getTool("bash");
    }
}
```

---

## 8. 自动化测试（API 测试）

```java
@Test
void shouldCallLlmApiSuccessfully() {
    // 使用 WireMock 模拟 HTTP
    stubFor(post("/v1/messages")
        .willReturn(okJson("{\"content\": \"response\"}")));
    
    String result = llmClient.call("test prompt");
    
    assertThat(result).isEqualTo("response");
    verify(postRequestedFor(urlEqualTo("/v1/messages")));
}
```

---

## 输出格式（测试报告）

```
## 测试分析: [类名/功能]

### 测试覆盖情况
- 单元测试: X 个
- 集成测试: X 个
- 覆盖率: X%

### 缺失测试用例
1. **边界值**: [描述]
2. **异常路径**: [描述]
3. **并发场景**: [描述]

### 建议测试代码
[提供具体的测试代码示例]

### 改进建议
- [ ] 增加参数化测试覆盖更多场景
- [ ] Mock 外部依赖（LlmClient）
- [ ] 添加集成测试验证端到端流程
```

---

## 快速检查清单

使用此 skill 时，按以下步骤操作：

1. **读取目标代码**: `read_file` 获取待测试的类
2. **分析测试需求**: 识别核心逻辑、边界条件、异常路径
3. **检查现有测试**: 查看 `src/test/java` 对应测试文件
4. **生成测试代码**: 按上述模板编写 JUnit 5 + Mockito 测试
5. **验证覆盖率**: 建议运行 `mvn test jacoco:report`
