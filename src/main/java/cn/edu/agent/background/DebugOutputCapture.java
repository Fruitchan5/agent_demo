package cn.edu.agent.background;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * 带调试日志的 OutputCapture 测试版本
 */
public class DebugOutputCapture {
    private final Process process;
    private final int maxChars;
    private final StringBuilder stdout = new StringBuilder();
    private final StringBuilder stderr = new StringBuilder();
    private final Thread stdoutReader;
    private final Thread stderrReader;

    public DebugOutputCapture(Process process, int maxChars) {
        this.process = process;
        this.maxChars = maxChars;

        this.stdoutReader = new Thread(() -> {
            System.out.println("[DEBUG-CAPTURE] stdout reader started");
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                int lineCount = 0;
                while ((line = reader.readLine()) != null && stdout.length() < maxChars) {
                    stdout.append(line).append("\n");
                    lineCount++;
                }
                System.out.println("[DEBUG-CAPTURE] stdout reader finished, lines: " + lineCount);
            } catch (Exception e) {
                System.out.println("[DEBUG-CAPTURE] stdout reader exception: " + e.getMessage());
            }
        });
        this.stdoutReader.setDaemon(true);

        this.stderrReader = new Thread(() -> {
            System.out.println("[DEBUG-CAPTURE] stderr reader started");
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8))) {
                String line;
                int lineCount = 0;
                while ((line = reader.readLine()) != null && stderr.length() < maxChars) {
                    stderr.append(line).append("\n");
                    lineCount++;
                }
                System.out.println("[DEBUG-CAPTURE] stderr reader finished, lines: " + lineCount);
            } catch (Exception e) {
                System.out.println("[DEBUG-CAPTURE] stderr reader exception: " + e.getMessage());
            }
        });
        this.stderrReader.setDaemon(true);
    }

    public void start() {
        System.out.println("[DEBUG-CAPTURE] Starting capture threads");
        stdoutReader.start();
        stderrReader.start();
    }

    public String getOutput(int timeoutSeconds) {
        System.out.println("[DEBUG-CAPTURE] getOutput called, timeout: " + timeoutSeconds + "s");
        System.out.println("[DEBUG-CAPTURE] stdout thread alive: " + stdoutReader.isAlive());
        System.out.println("[DEBUG-CAPTURE] stderr thread alive: " + stderrReader.isAlive());
        
        try {
            System.out.println("[DEBUG-CAPTURE] Joining stdout thread...");
            stdoutReader.join(timeoutSeconds * 1000L);
            System.out.println("[DEBUG-CAPTURE] stdout thread joined, still alive: " + stdoutReader.isAlive());
            
            System.out.println("[DEBUG-CAPTURE] Joining stderr thread...");
            stderrReader.join(timeoutSeconds * 1000L);
            System.out.println("[DEBUG-CAPTURE] stderr thread joined, still alive: " + stderrReader.isAlive());
        } catch (InterruptedException e) {
            System.out.println("[DEBUG-CAPTURE] Interrupted while joining");
            Thread.currentThread().interrupt();
        }

        System.out.println("[DEBUG-CAPTURE] Building combined output...");
        String combined = stdout.toString() + stderr.toString();
        System.out.println("[DEBUG-CAPTURE] Combined length: " + combined.length());
        
        if (combined.length() > maxChars) {
            return combined.substring(0, maxChars) + "\n... (truncated)";
        }
        return combined;
    }
}
