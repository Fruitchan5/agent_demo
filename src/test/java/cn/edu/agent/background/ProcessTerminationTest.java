package cn.edu.agent.background;

import org.junit.jupiter.api.Test;

/**
 * 验证进程终止和输出捕获的行为
 */
class ProcessTerminationTest {

    @Test
    void testProcessKillAndOutputCapture() throws Exception {
        System.out.println("=== 测试进程终止和输出捕获 ===");
        
        // 创建一个长时间运行的进程
        ProcessBuilder pb = new ProcessBuilder("ping", "127.0.0.1", "-n", "100");
        Process process = pb.start();
        
        System.out.println("进程已启动，PID: " + process.pid());
        System.out.println("进程存活: " + process.isAlive());
        
        // 等待1秒
        Thread.sleep(1000);
        
        System.out.println("\n强制终止进程...");
        process.destroyForcibly();
        
        // 检查进程是否真的被终止
        boolean exited = process.waitFor(2, java.util.concurrent.TimeUnit.SECONDS);
        System.out.println("进程是否退出: " + exited);
        System.out.println("进程存活: " + process.isAlive());
        
        if (exited) {
            System.out.println("退出码: " + process.exitValue());
        }
        
        // 尝试读取输出（这可能会阻塞）
        System.out.println("\n尝试读取输出...");
        Thread readerThread = new Thread(() -> {
            try (java.io.BufferedReader reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(process.getInputStream()))) {
                String line;
                int count = 0;
                while ((line = reader.readLine()) != null && count < 5) {
                    System.out.println("输出: " + line);
                    count++;
                }
                System.out.println("读取完成");
            } catch (Exception e) {
                System.out.println("读取异常: " + e.getClass().getSimpleName() + " - " + e.getMessage());
            }
        });
        readerThread.setDaemon(true);
        readerThread.start();
        
        // 等待最多3秒
        readerThread.join(3000);
        if (readerThread.isAlive()) {
            System.out.println("读取线程仍在运行（阻塞）");
        } else {
            System.out.println("读取线程已完成");
        }
    }
}
