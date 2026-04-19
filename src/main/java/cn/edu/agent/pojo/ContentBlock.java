package cn.edu.agent.pojo;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import java.util.Map;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ContentBlock {
    private String type; // "text" 或 "tool_use"

    // 文本部分
    private String text;

    // 工具调用部分
    private String id;   // tool_use_id
    private String name; // 工具名，如 "bash"
    private Map<String, Object> input; // 工具参数，如 {"command": "ls"}
}
