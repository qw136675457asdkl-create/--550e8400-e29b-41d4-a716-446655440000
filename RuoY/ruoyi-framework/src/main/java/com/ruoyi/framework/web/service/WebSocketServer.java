package com.ruoyi.framework.web.service;

import org.springframework.stereotype.Component;

import javax.websocket.OnClose;
import javax.websocket.OnMessage;
import javax.websocket.OnOpen;
import javax.websocket.Session;
import javax.websocket.server.PathParam;
import javax.websocket.server.ServerEndpoint;
import java.util.concurrent.ConcurrentHashMap;

@Component
@ServerEndpoint("/websocket/{userId}")
public class WebSocketServer {
    // 静态变量，用来记录当前在线连接数
    private static final ConcurrentHashMap<Long, Session> sessionPool = new ConcurrentHashMap<>();

    @OnOpen
    public void onOpen(Session session, @PathParam("userId") Long userId) {
        sessionPool.put(userId, session);
        System.out.println("新建连接：" + userId);
    }

    @OnClose
    public void onClose(@PathParam("userId") Long userId) {
        sessionPool.remove(userId);
        System.out.println("连接关闭：" + userId);
    }

    @OnMessage
    public void onMessage(String message, Session session) {
        System.out.println("收到消息：" + message);
    }

    // 自定义单发消息方法
    public void sendText(Long userId, String message) {
        Session session = sessionPool.get(userId);
        System.out.println("准备发送 userId=" + userId);
        System.out.println("当前在线用户=" + sessionPool.keySet());
        System.out.println("session=" + session);

        if (session != null && session.isOpen()) {
            session.getAsyncRemote().sendText(message);
            System.out.println("消息已提交发送: " + message);
        } else {
            System.out.println("发送失败，session 不存在或未打开");
        }
    }
}