package luonq.ws;

import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

public class MyWebSocketHandler extends TextWebSocketHandler {


    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        // 连接建立时发送初始数据
        String initialData = "{\"a\": \"value1\",\"b\": \"value2\",\"c\": \"value3\",\"d\": \"value4\",\"e\": [\"item1\", \"item2\", \"item3\", \"item4\", \"item5\"]}";
        session.sendMessage(new TextMessage(initialData));
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String payload = message.getPayload();  // 接收客户端消息
        System.out.println("Received: " + payload);

        // 向客户端发送消息
        session.sendMessage(new TextMessage("Server response: " + payload));
    }
}
