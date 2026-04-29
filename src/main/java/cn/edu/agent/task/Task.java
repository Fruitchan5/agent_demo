package cn.edu.agent.task;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.List;

public class Task {
    private int id;
    private String subject;
    private String description;
    private String status;
    private List<Integer> blockedBy = new ArrayList<>();
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)  // serialize only, skip on deserialization
    private List<Integer> blocks = new ArrayList<>();  // computed, not persisted
    private String owner;

    public int getId()                          { return id; }
    public void setId(int id)                   { this.id = id; }
    public String getSubject()                  { return subject; }
    public void setSubject(String subject)      { this.subject = subject; }
    public String getDescription()              { return description; }
    public void setDescription(String d)        { this.description = d; }
    public String getStatus()                   { return status; }
    public void setStatus(String status)        { this.status = status; }
    public List<Integer> getBlockedBy()         { return blockedBy; }
    public void setBlockedBy(List<Integer> l)   { this.blockedBy = l; }
    public List<Integer> getBlocks()            { return blocks; }
    public void setBlocks(List<Integer> l)      { this.blocks = l; }
    public String getOwner()                    { return owner; }
    public void setOwner(String owner)          { this.owner = owner; }
}
