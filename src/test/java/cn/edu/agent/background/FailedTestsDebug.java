package cn.edu.agent.background;

import org.junit.jupiter.api.Test;

/**
 * 调试失败的测试用例
 */
class FailedTestsDebug {

    @Test
    void debugTimeout() throws InterruptedException {
        BackgroundTaskManager manager = new BackgroundTaskManager();
        
        System.out.println("=== 测试超时功能 ===");
        String command = "ping 127.0.0.1 -n 100";
        String result = manager.runInBackground(command, null, 1);
        String taskId = result.split(" ")[2];
        
        System.out.println("任务ID: " + taskId);
        System.out.println("超时设置: 1秒");
        
        // 等待超时发生
        for (int i = 0; i < 5; i++) {
            Thread.sleep(1000);
            System.out.println("\n=== 第 " + (i+1) + " 秒 ===");
            String status = manager.checkTask(taskId);
            System.out.println(status);
            
            if (!status.contains("RUNNING")) {
                break;
            }
        }
        
        manager.shutdown();
    }

    @Test
    void debugTruncation() throws InterruptedException {
        BackgroundTaskManager manager = new BackgroundTaskManager();
        
        System.out.println("=== 测试输出截断 ===");
        
        // 生成大量输出
        StringBuilder longString = new StringBuilder();
        for (int i = 0; i < 2000; i++) {
            longString.append("ABCDEFGHIJ"); // 20000 字符
        }
        
        String command = "echo " + longString.toString();
        String result = manager.runInBackground(command, null, null);
        String taskId = result.split(" ")[2];
        
        System.out.println("任务ID: " + taskId);
        System.out.println("预期输出长度: 20000 字符");
        
        Thread.sleep(3000);
        
        String status = manager.checkTask(taskId);
        System.out.println("\n=== 任务状态 ===");
        System.out.println("状态字符串长度: " + status.length());
        System.out.println("是否包含 'truncated': " + status.contains("truncated"));
        System.out.println("是否包含 '截断': " + status.contains("截断"));
        System.out.println("\n前500字符:");
        System.out.println(status.substring(0, Math.min(500, status.length())));
        System.out.println("\n后500字符:");
        System.out.println(status.substring(Math.max(0, status.length() - 500)));
        
        manager.shutdown();
    }
}
