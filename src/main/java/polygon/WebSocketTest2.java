package polygon;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;

/**
 * Created by Luonanqin on 2023/5/8.
 */
public class WebSocketTest2 {


    public static void main(String[] args) throws IOException {
        try {
            // open websocket
            final WebsocketClientEndpoint clientEndPoint = new WebsocketClientEndpoint(new URI("wss://delayed.polygon.io/stocks"));

            // add listener
            clientEndPoint.addMessageHandler(message -> System.out.println(message));

            // send message to websocket
            clientEndPoint.sendMessage("{\"action\":\"auth\",\"params\":\"Ea9FNNIdlWnVnGcoTpZsOWuCWEB3JAqY\"}");
            clientEndPoint.sendMessage("{\"action\":\"subscribe\", \"params\":\"T.*\"}");

            // wait 5 seconds for messages from websocket

            while (true) {
                System.out.println();
                Thread.sleep(1000);
            }
        } catch (InterruptedException ex) {
            System.err.println("InterruptedException exception: " + ex.getMessage());
        } catch (URISyntaxException ex) {
            System.err.println("URISyntaxException exception: " + ex.getMessage());
        }
    }
}
