package cn.edu.agent.util;

import cn.edu.agent.task.TaskManager;
import cn.edu.agent.worktree.WorktreeInfo;
import cn.edu.agent.worktree.WorktreeManager;
import cn.edu.agent.worktree.WorktreeManagerImpl;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * CLI helper for worktree operations
 */
public class WorktreeCliHelper {
    
    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.out.println("Usage: WorktreeCliHelper <command> <args...>");
            System.out.println("Commands:");
            System.out.println("  create <name> <task_id> [base_ref]");
            System.out.println("  list");
            return;
        }
        
        Path projectRoot = Paths.get(System.getProperty("user.dir"));
        Path tasksDir = projectRoot.resolve(".tasks");
        
        TaskManager taskManager = new TaskManager(tasksDir);
        WorktreeManager manager = new WorktreeManagerImpl(projectRoot, taskManager);
        
        String command = args[0];
        
        switch (command) {
            case "create":
                String name = args[1];
                Integer taskId = args.length > 2 ? Integer.parseInt(args[2]) : null;
                String baseRef = args.length > 3 ? args[3] : "HEAD";
                
                WorktreeInfo info = manager.create(name, taskId, baseRef);
                System.out.println("Created worktree '" + info.getName() + "'");
                System.out.println("  Path: " + info.getPath());
                System.out.println("  Branch: " + info.getBranch());
                if (info.getTaskId() != null) {
                    System.out.println("  Bound to task: #" + info.getTaskId());
                }
                System.out.println("  Status: " + info.getStatus());
                break;
                
            case "list":
                var worktrees = manager.listAll();
                System.out.println("Active worktrees:");
                for (WorktreeInfo wt : worktrees) {
                    System.out.printf("  %s -> %s (task: %s, status: %s)%n",
                        wt.getName(), wt.getPath(), 
                        wt.getTaskId() != null ? "#" + wt.getTaskId() : "none",
                        wt.getStatus());
                }
                break;
                
            default:
                System.out.println("Unknown command: " + command);
        }
    }
}
