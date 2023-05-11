package polygon;

import com.alibaba.fastjson.JSON;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.Socket;

/**
 * Created by Luonanqin on 2023/5/8.
 */
public class WebSocketTest2 {


    public static void main(String[] args) throws IOException {
        Socket socket = new Socket();
        socket.connect(new InetSocketAddress("wss://delayed.polygon.io/stocks", 80));

        InputStream inputStream = socket.getInputStream();
        String s = JSON.parseObject(inputStream, String.class);
        System.out.println(s);
    }
}
