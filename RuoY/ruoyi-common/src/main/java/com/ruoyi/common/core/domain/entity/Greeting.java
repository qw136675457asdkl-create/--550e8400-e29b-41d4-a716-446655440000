package com.ruoyi.common.core.domain.entity;

public class Greeting {
    private String content;
    private String username;
    public Greeting(String content, String username) {
        this.content = content;
        this.username = username;
    }
    public String getContent() {
        return content;
    }
    public void setContent(String content) {
        this.content = content;
    }
    public String getUsername() {
        return username;
    }
    public void setUsername(String username) {
        this.username = username;
    }
}
