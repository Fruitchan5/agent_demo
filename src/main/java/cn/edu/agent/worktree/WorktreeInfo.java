package cn.edu.agent.worktree;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;

public class WorktreeInfo {
    private String name;
    @JsonIgnore
    private Path path;
    private String branch;
    private Integer taskId;
    private WorktreeState status;
    private Instant createdAt;

    public WorktreeInfo() {}

    public WorktreeInfo(String name, Path path, String branch, Integer taskId, WorktreeState status) {
        this.name = name;
        this.path = path;
        this.branch = branch;
        this.taskId = taskId;
        this.status = status;
        this.createdAt = Instant.now();
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    @JsonIgnore
    public Path getPath() {
        return path;
    }

    public void setPath(Path path) {
        this.path = path;
    }

    // JSON序列化辅助方法
    @JsonProperty("path")
    public String getPathString() {
        return path != null ? path.toString() : null;
    }

    @JsonProperty("path")
    public void setPathString(String pathString) {
        this.path = pathString != null ? Paths.get(pathString) : null;
    }

    public String getBranch() {
        return branch;
    }

    public void setBranch(String branch) {
        this.branch = branch;
    }

    public Integer getTaskId() {
        return taskId;
    }

    public void setTaskId(Integer taskId) {
        this.taskId = taskId;
    }

    public WorktreeState getStatus() {
        return status;
    }

    public void setStatus(WorktreeState status) {
        this.status = status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
