package cn.edu.agent.background;

import org.junit.jupiter.api.Test;

/**
 * 通过添加日志追踪 executeTask 的执行流程
 */
class ExecutionFlowTracingTest {

    @Test
    void traceExecutionWithLogging() throws InterruptedException {
        // 临时修改 BackgroundTaskManager 添加日志
        // 或者直接观察任务状态的变化
        
        BackgroundTaskManager manager = new BackgroundTaskManager();
        
        System.out.println("=== 开始追踪执行流程 ===");
        System.out.println("时间: " + System.currentTimeMillis());
        
        String result = manager.runInBackground("ping 127.0.0.1 -n 100", null, 2);
        String taskId = result.split(" ")[2];
        
        System.out.println("任务ID: " + taskId);
        System.out.println("超时设置: 2秒");
        
        // 每0.5秒检查一次状态，持续10秒
        for (int i = 0; i < 20; i++) {
            Thread.sleep(500);
            String status = manager.checkTask(taskId);
            
            // 提取状态行
            for (String line : status.split("\n")) {
                if (line.startsWith("Status:")) {
                    System.out.println(String.format("%.1fs: %s", i * 0.5, line));
                    break;
                }
            }
            
            // 如果状态不是 RUNNING，打印完整信息并退出
            if (!status.contains("Status: RUNNING")) {
                System.out.println("\n=== 任务完成 ===");
                System.out.println(status);
                break;
            }
        }
        
        manager.shutdown();
    }

    @Test
    void checkIfExecuteTaskIsEvenCalled() throws InterruptedException {
        BackgroundTaskManager manager = new BackgroundTaskManager();
        
        System.out.println("=== 检查 executeTask 是否被调用 ===");
        
        // 使用一个会立即完成的命令
        String result = manager.runInBackground("echo test", null, 10);
        String taskId = result.split(" ")[2];
        
        System.out.println("任务ID: " + taskId);
        
        // 等待5秒
        for (int i = 0; i < 10; i++) {
            Thread.sleep(500);
            String status = manager.checkTask(taskId);
            
            System.out.println("\n第 " + (i * 0.5) + " 秒:");
            System.out.println(status);
            
            if (!status.contains("Status: RUNNING") && !status.contains("Status: PENDING")) {
                System.out.println("\n任务已完成！");
                break;
            }
        }
        
        manager.shutdown();
    }
}
