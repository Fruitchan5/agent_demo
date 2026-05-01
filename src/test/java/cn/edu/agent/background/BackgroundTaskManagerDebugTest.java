package cn.edu.agent.background;

import org.junit.jupiter.api.*;

/**
 * 调试测试 - 用于了解实际的 API 行为和返回格式
 */
class BackgroundTaskManagerDebugTest {

    private BackgroundTaskManager taskManager;

    @BeforeEach
    void setUp() {
        taskManager = new BackgroundTaskManager();
    }

    @AfterEach
    void tearDown() {
        if (taskManager != null) {
            taskManager.shutdown();
        }
    }

    @Test
    @DisplayName("调试：查看 runInBackground 的返回格式")
    void debugRunInBackground() throws InterruptedException {
        String result = taskManager.runInBackground("echo Hello", null, null);
        System.out.println("=== runInBackground 返回值 ===");
        System.out.println(result);
        System.out.println("=== 结束 ===");
        
        Thread.sleep(500);
    }

    @Test
    @DisplayName("调试：查看 checkTask 的返回格式")
    void debugCheckTask() throws InterruptedException {
        String result = taskManager.runInBackground("echo Test", null, null);
        System.out.println("=== runInBackground 返回 ===");
        System.out.println(result);
        
        // 提取任务ID（假设格式类似 "Background task <id> started"）
        String[] parts = result.split(" ");
        String taskId = null;
        for (int i = 0; i < parts.length - 1; i++) {
            if (parts[i].equals("task")) {
                taskId = parts[i + 1];
                break;
            }
        }
        
        System.out.println("=== 提取的任务ID ===");
        System.out.println(taskId);
        
        Thread.sleep(500);
        
        String status = taskManager.checkTask(taskId);
        System.out.println("=== checkTask 返回 ===");
        System.out.println(status);
        System.out.println("=== 结束 ===");
    }

    @Test
    @DisplayName("调试：查看 cancelTask 的返回格式")
    void debugCancelTask() throws InterruptedException {
        String result = taskManager.runInBackground("ping 127.0.0.1 -n 10", null, null);
        String[] parts = result.split(" ");
        String taskId = null;
        for (int i = 0; i < parts.length - 1; i++) {
            if (parts[i].equals("task")) {
                taskId = parts[i + 1];
                break;
            }
        }
        
        Thread.sleep(500);
        
        String cancelResult = taskManager.cancelTask(taskId);
        System.out.println("=== cancelTask 返回 ===");
        System.out.println(cancelResult);
        System.out.println("=== 结束 ===");
        
        Thread.sleep(500);
    }

    @Test
    @DisplayName("调试：查看失败命令的状态")
    void debugFailedCommand() throws InterruptedException {
        String result = taskManager.runInBackground("cmd /c exit 1", null, null);
        String[] parts = result.split(" ");
        String taskId = null;
        for (int i = 0; i < parts.length - 1; i++) {
            if (parts[i].equals("task")) {
                taskId = parts[i + 1];
                break;
            }
        }
        
        Thread.sleep(500);
        
        String status = taskManager.checkTask(taskId);
        System.out.println("=== 失败命令的 checkTask 返回 ===");
        System.out.println(status);
        System.out.println("=== 结束 ===");
    }

    @Test
    @DisplayName("调试：查看 listTasks 的返回格式")
    void debugListTasks() throws InterruptedException {
        taskManager.runInBackground("echo Task1", null, null);
        taskManager.runInBackground("echo Task2", null, null);
        
        Thread.sleep(500);
        
        String list = taskManager.listTasks();
        System.out.println("=== listTasks 返回 ===");
        System.out.println(list);
        System.out.println("=== 结束 ===");
    }

    @Test
    @DisplayName("调试：查看空命令和null命令的行为")
    void debugInvalidCommands() {
        System.out.println("=== 测试空命令 ===");
        try {
            String result = taskManager.runInBackground("", null, null);
            System.out.println("空命令返回: " + result);
        } catch (Exception e) {
            System.out.println("空命令抛出异常: " + e.getClass().getName() + " - " + e.getMessage());
        }
        
        System.out.println("=== 测试null命令 ===");
        try {
            String result = taskManager.runInBackground(null, null, null);
            System.out.println("null命令返回: " + result);
        } catch (Exception e) {
            System.out.println("null命令抛出异常: " + e.getClass().getName() + " - " + e.getMessage());
        }
    }

    @Test
    @DisplayName("调试：查看通知功能")
    void debugNotifications() throws InterruptedException {
        String result = taskManager.runInBackground("echo Notify", null, null);
        
        Thread.sleep(500);
        
        var notifications = taskManager.drainNotifications();
        System.out.println("=== drainNotifications 返回 ===");
        System.out.println("通知数量: " + notifications.size());
        for (var notification : notifications) {
            System.out.println("通知: " + notification);
        }
        System.out.println("=== 结束 ===");
    }
}
