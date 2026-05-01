package cn.edu.agent.background;

import org.junit.jupiter.api.Test;

/**
 * 最简单的后台任务测试 - 手动验证
 */
class SimpleBackgroundTest {

    @Test
    void manualTest() throws InterruptedException {
        BackgroundTaskManager manager = new BackgroundTaskManager();
        
        System.out.println("=== 启动任务 ===");
        String result = manager.runInBackground("echo Hello", null, null);
        System.out.println(result);
        
        String taskId = result.split(" ")[2];
        
        // 多次检查状态，观察变化
        for (int i = 0; i < 10; i++) {
            Thread.sleep(1000);
            System.out.println("\n=== 第 " + (i+1) + " 秒 ===");
            String status = manager.checkTask(taskId);
            System.out.println(status);
            
            if (status.contains("COMPLETED") || status.contains("FAILED")) {
                System.out.println("\n任务已完成！");
                break;
            }
        }
        
        manager.shutdown();
    }
}
