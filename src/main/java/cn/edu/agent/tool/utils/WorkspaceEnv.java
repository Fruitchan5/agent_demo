package cn.edu.agent.tool.utils;

import java.nio.file.Path;
import java.nio.file.Paths;

public class WorkspaceEnv {
    // 默认将你当前运行 Java 项目的根目录设为“安全沙箱”
    public static final Path WORKDIR = Paths.get(System.getProperty("user.dir")).toAbsolutePath().normalize();

    /**
     * s02 核心安全机制：防止路径逃逸
     */
    public static Path safePath(String inputPath) throws IllegalArgumentException {
        // 1. 将输入的相对路径与工作区拼接，并标准化 (消除 ../ 等符号)
        Path requestedPath = WORKDIR.resolve(inputPath).toAbsolutePath().normalize();

        // 2. 检查最终路径是否还在工作区内
        if (!requestedPath.startsWith(WORKDIR)) {
            throw new IllegalArgumentException("严重安全警告: 路径尝试逃逸工作区: " + inputPath);
        }

        return requestedPath;
    }
}