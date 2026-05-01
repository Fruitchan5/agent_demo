package cn.edu.agent.background;

import java.util.concurrent.*;

/**
 * 线程池执行器（可选实现）
 */
public class PooledExecutor implements TaskExecutor {
    private final ExecutorService pool;

    public PooledExecutor(int maxTasks) {
        this.pool = new ThreadPoolExecutor(
            0, maxTasks,
            60L, TimeUnit.SECONDS,
            new SynchronousQueue<>(),
            new ThreadFactory() {
                @Override
                public Thread newThread(Runnable r) {
                    Thread t = new Thread(r);
                    t.setDaemon(true);
                    return t;
                }
            },
            new ThreadPoolExecutor.CallerRunsPolicy()
        );
    }

    @Override
    public void execute(Runnable task, String taskId) {
        pool.submit(task);
    }

    @Override
    public void shutdown() {
        pool.shutdown();
        try {
            if (!pool.awaitTermination(10, TimeUnit.SECONDS)) {
                pool.shutdownNow();
            }
        } catch (InterruptedException e) {
            pool.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
