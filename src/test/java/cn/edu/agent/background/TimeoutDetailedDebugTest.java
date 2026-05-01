package cn.edu.agent.background;

import org.junit.jupiter.api.Test;

/**
 * 详细追踪超时执行流程
 */
class TimeoutDetailedDebugTest {

    @Test
    void traceTimeoutExecution() throws InterruptedException {
        BackgroundTaskManager manager = new BackgroundTaskManager();
        
        System.out.println("=== 开始测试超时 ===");
        System.out.println("当前时间: " + System.currentTimeMillis());
        
        // 使用一个简单的长时间命令
        String command = "ping 127.0.0.1 -n 10";  // 大约10秒
        System.out.println("命令: " + command);
        System.out.println("超时设置: 2秒");
        
        String result = manager.runInBackground(command, null, 2);
        String taskId = result.split(" ")[2];
        System.out.println("任务ID: " + taskId);
        
        // 每500ms检查一次状态
        for (int i = 0; i < 20; i++) {
            Thread.sleep(500);
            String status = manager.checkTask(taskId);
            
            // 只打印状态行
            String[] lines = status.split("\n");
            for (String line : lines) {
                if (line.startsWith("Status:")) {
                    System.out.println("第 " + (i * 0.5) + " 秒: " + line);
                    break;
                }
            }
            
            if (!status.contains("RUNNING")) {
                System.out.println("\n=== 任务完成，最终状态 ===");
                System.out.println(status);
                break;
            }
        }
        
        manager.shutdown();
    }

    @Test
    void testVeryShortTimeout() throws InterruptedException {
        BackgroundTaskManager manager = new BackgroundTaskManager();
        
        System.out.println("=== 测试极短超时（1秒） ===");
        
        String command = "ping 127.0.0.1 -n 100";
        String result = manager.runInBackground(command, null, 1);
        String taskId = result.split(" ")[2];
        
        System.out.println("任务ID: " + taskId);
        System.out.println("超时: 1秒");
        System.out.println("预期: 1秒后应该 TIMEOUT");
        
        // 等待3秒，足够超时发生
        Thread.sleep(3000);
        
        String status = manager.checkTask(taskId);
        System.out.println("\n=== 3秒后的状态 ===");
        System.out.println(status);
        
        manager.shutdown();
    }
}
