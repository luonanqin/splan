package luonq.polygon;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

import java.net.URI;

/**
 * Created by Luonanqin on 2023/5/8.
 */
public class TradeWS {


    public WebSocketClient webSocketClient() {
        try {
            WebSocketClient webSocketClient = new WebSocketClient(new URI("wss://socket.polygon.io/stocks")) {
                //连接服务端时触发
                @Override
                public void onOpen(ServerHandshake handshakedata) {
                    System.out.println("websocket客户端和服务器连接成功");
                    this.send("{\"action\":\"auth\",\"params\":\"Ea9FNNIdlWnVnGcoTpZsOWuCWEB3JAqY\"}");
                }

                //收到服务端消息时触发
                @Override
                public void onMessage(String message) {
                    System.out.println("websocket客户端收到消息 " + Thread.currentThread().getId() + " =" + message);
                }

                //和服务端断开连接时触发
                @Override
                public void onClose(int code, String reason, boolean remote) {
                    System.out.println("websocket客户端退出连接");
                }

                //连接异常时触发
                @Override
                public void onError(Exception ex) {
                    System.out.println("websocket客户端和服务器连接发生错误=" + ex.getMessage());
                }
            };
            webSocketClient.connect();
            return webSocketClient;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public static void main(String[] args) {
        TradeWS test = new TradeWS();
        WebSocketClient client = test.webSocketClient();
//        client.send("{\"action\":\"auth\",\"params\":\"Ea9FNNIdlWnVnGcoTpZsOWuCWEB3JAqY\"}");
        client.send("{\"action\":\"subscribe\", \"params\":\"T.AAPL\"}");
        System.out.println();
    }
}
