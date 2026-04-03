package com.ruoyi.Xidian.controller;

import com.ruoyi.common.core.domain.entity.Greeting;
import com.ruoyi.common.core.domain.entity.Message;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.stereotype.Controller;

@Controller
public class MessageController {
    @MessageMapping("/chat")
    @SendTo("/topic/chat")
    public Greeting chat(Message message) {
        return new Greeting("0",message.getName());
    }
}
