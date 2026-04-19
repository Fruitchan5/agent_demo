package cn.edu.agent;

import cn.edu.agent.core.AgentLoop;

public class AgentApplication {
    public static void main(String[] args) {
        AgentLoop agent = new AgentLoop();
        agent.start();
    }
}