package cn.edu.agent.worktree;

import cn.edu.agent.task.Task;
import cn.edu.agent.task.TaskManager;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * WorktreeManager测试
 * 注意：这些测试需要在git仓库中运行
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class WorktreeManagerTest {

    @TempDir
    Path tempDir;

    private TaskManager taskManager;
    private WorktreeManager worktreeManager;
    private Path projectRoot;

    @BeforeEach
    void setUp() throws IOException, InterruptedException {
        // 初始化git仓库
        projectRoot = tempDir.resolve("test-repo");
        Files.createDirectories(projectRoot);

        // 初始化git
        ProcessBuilder pb = new ProcessBuilder("git", "init");
        pb.directory(projectRoot.toFile());
        Process process = pb.start();
        int exitCode = process.waitFor();

        if (exitCode != 0) {
            throw new RuntimeException("Failed to initialize git repository");
        }

        // 配置git用户
        runGit("config", "user.name", "Test User");
        runGit("config", "user.email", "test@example.com");

        // 创建初始提交
        Path testFile = projectRoot.resolve("README.md");
        Files.writeString(testFile, "# Test Repository");
        runGit("add", "README.md");
        runGit("commit", "-m", "Initial commit");

        // 初始化managers
        Path tasksDir = projectRoot.resolve(".tasks");
        taskManager = new TaskManager(tasksDir);
        worktreeManager = new WorktreeManagerImpl(projectRoot, taskManager);
    }

    private void runGit(String... args) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder();
        pb.command("git");
        for (String arg : args) {
            pb.command().add(arg);
        }
        pb.directory(projectRoot.toFile());
        pb.redirectErrorStream(true);

        Process process = pb.start();
        int exitCode = process.waitFor();

        if (exitCode != 0) {
            throw new RuntimeException("Git command failed: " + String.join(" ", args));
        }
    }

    @Test
    @Order(1)
    void testCreateWorktree_withoutTask() {
        WorktreeInfo info = worktreeManager.create("test-wt", null, "HEAD");

        assertNotNull(info);
        assertEquals("test-wt", info.getName());
        assertEquals("wt/test-wt", info.getBranch());
        assertEquals(WorktreeState.ACTIVE, info.getStatus());
        assertNull(info.getTaskId());
        assertTrue(Files.exists(info.getPath()));
    }

    @Test
    @Order(2)
    void testCreateWorktree_withTask() throws IOException {
        // 创建任务
        Task task = taskManager.create("Test task", "Description", null);

        // 创建worktree并绑定任务
        WorktreeInfo info = worktreeManager.create("task-wt", task.getId(), "HEAD");

        assertNotNull(info);
        assertEquals("task-wt", info.getName());
        assertEquals(task.getId(), info.getTaskId());

        // 验证任务已绑定
        Task updatedTask = taskManager.get(task.getId());
        assertEquals("task-wt", updatedTask.getWorktree());
    }

    @Test
    @Order(3)
    void testCreateWorktree_duplicateName() {
        worktreeManager.create("duplicate", null, "HEAD");

        assertThrows(IllegalArgumentException.class, () -> {
            worktreeManager.create("duplicate", null, "HEAD");
        });
    }

    @Test
    @Order(4)
    void testCreateWorktree_invalidName() {
        assertThrows(IllegalArgumentException.class, () -> {
            worktreeManager.create("invalid name with spaces", null, "HEAD");
        });

        assertThrows(IllegalArgumentException.class, () -> {
            worktreeManager.create("", null, "HEAD");
        });
    }

    @Test
    @Order(5)
    void testGetWorktree() {
        worktreeManager.create("get-test", null, "HEAD");

        WorktreeInfo info = worktreeManager.get("get-test");
        assertNotNull(info);
        assertEquals("get-test", info.getName());

        WorktreeInfo notFound = worktreeManager.get("non-existent");
        assertNull(notFound);
    }

    @Test
    @Order(6)
    void testListWorktrees() {
        worktreeManager.create("wt1", null, "HEAD");
        worktreeManager.create("wt2", null, "HEAD");

        List<WorktreeInfo> list = worktreeManager.listAll();
        assertTrue(list.size() >= 2);

        assertTrue(list.stream().anyMatch(wt -> "wt1".equals(wt.getName())));
        assertTrue(list.stream().anyMatch(wt -> "wt2".equals(wt.getName())));
    }

    @Test
    @Order(7)
    void testExecuteCommand() throws IOException {
        worktreeManager.create("exec-test", null, "HEAD");

        // 在worktree中创建文件
        CommandResult result = worktreeManager.execute("exec-test",
            System.getProperty("os.name").toLowerCase().contains("windows")
                ? "echo test > test.txt"
                : "echo test > test.txt");

        assertEquals(0, result.getExitCode());

        // 验证文件存在
        WorktreeInfo info = worktreeManager.get("exec-test");
        Path testFile = info.getPath().resolve("test.txt");
        assertTrue(Files.exists(testFile));
    }

    @Test
    @Order(8)
    void testStatus() {
        worktreeManager.create("status-test", null, "HEAD");

        String status = worktreeManager.status("status-test");
        assertNotNull(status);
        // 新创建的worktree应该是干净的
        assertTrue(status.contains("Clean") || status.contains("wt/status-test"));
    }

    @Test
    @Order(9)
    void testKeepWorktree() {
        worktreeManager.create("keep-test", null, "HEAD");

        worktreeManager.keep("keep-test");

        WorktreeInfo info = worktreeManager.get("keep-test");
        assertEquals(WorktreeState.KEPT, info.getStatus());
    }

    @Test
    @Order(10)
    void testRemoveWorktree_withoutTask() {
        WorktreeInfo info = worktreeManager.create("remove-test", null, "HEAD");
        Path worktreePath = info.getPath();

        assertTrue(Files.exists(worktreePath));

        worktreeManager.remove("remove-test", false, false);

        // 验证目录已删除
        assertFalse(Files.exists(worktreePath));

        // 验证状态已更新
        WorktreeInfo updated = worktreeManager.get("remove-test");
        assertEquals(WorktreeState.REMOVED, updated.getStatus());
    }

    @Test
    @Order(11)
    void testRemoveWorktree_withCompleteTask() throws IOException {
        // 创建任务
        Task task = taskManager.create("Remove task", "Description", null);
        int taskId = task.getId();

        // 创建worktree
        worktreeManager.create("remove-complete", taskId, "HEAD");

        // 验证任务已绑定worktree
        Task boundTask = taskManager.get(taskId);
        assertEquals("remove-complete", boundTask.getWorktree());

        // 删除worktree并完成任务
        worktreeManager.remove("remove-complete", false, true);

        // 验证任务已完成（注意：completed状态的任务会被自动删除）
        // 所以我们只验证worktree状态已更新
        WorktreeInfo info = worktreeManager.get("remove-complete");
        assertEquals(WorktreeState.REMOVED, info.getStatus());
    }

    @Test
    @Order(12)
    void testBindTask() throws IOException {
        // 创建worktree和任务
        worktreeManager.create("bind-test", null, "HEAD");
        Task task = taskManager.create("Bind task", "Description", null);

        // 绑定
        worktreeManager.bindTask("bind-test", task.getId());

        // 验证绑定
        WorktreeInfo info = worktreeManager.get("bind-test");
        assertEquals(task.getId(), info.getTaskId());

        Task updatedTask = taskManager.get(task.getId());
        assertEquals("bind-test", updatedTask.getWorktree());
    }

    @Test
    @Order(13)
    void testUnbindTask() throws IOException {
        // 创建任务和worktree
        Task task = taskManager.create("Unbind task", "Description", null);
        worktreeManager.create("unbind-test", task.getId(), "HEAD");

        // 解绑
        worktreeManager.unbindTask("unbind-test");

        // 验证解绑
        WorktreeInfo info = worktreeManager.get("unbind-test");
        assertNull(info.getTaskId());

        Task updatedTask = taskManager.get(task.getId());
        assertNull(updatedTask.getWorktree());
    }

    @Test
    @Order(14)
    void testGetRecentEvents() {
        worktreeManager.create("event-test", null, "HEAD");

        String events = worktreeManager.getRecentEvents(10);
        assertNotNull(events);
        assertTrue(events.contains("worktree.create"));
    }
}
