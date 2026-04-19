package cn.edu.agent.pojo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class chatMessage {
    private String role;    // "user" 或 "assistant"
    private Object content; // 可以是 String，也可以是复杂的 List<ContentBlock>
}
