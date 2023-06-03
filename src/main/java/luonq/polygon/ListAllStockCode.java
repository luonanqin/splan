package luonq.polygon;

import bean.Ticker;
import bean.TickerResp;
import com.alibaba.fastjson.JSON;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.URI;
import org.apache.commons.httpclient.methods.GetMethod;

import java.io.FileWriter;
import java.io.InputStream;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Created by Luonanqin on 2023/1/11.
 */
public class ListAllStockCode {


    public static void getStock(String market) {
        System.out.println(market);
        String apiKeyParam = "apiKey=Ea9FNNIdlWnVnGcoTpZsOWuCWEB3JAqY";
        String url = "https://api.polygon.io/v3/trades/AAPL?" + apiKeyParam;

        HttpClient httpclient = new HttpClient();
        GetMethod get = new GetMethod(url);
        FileWriter fw;
        String fileName = String.format("%s.txt", market);
        try {
            fw = new FileWriter(fileName);

            int index = 1;
            while (true) {
                int code = httpclient.executeMethod(get);
                if (code != 200) {
                    System.err.println("request failed");
                }
                //                String res = get.getResponseBodyAsString();

                InputStream stream = get.getResponseBodyAsStream();
                TickerResp tickerResp = JSON.parseObject(stream, TickerResp.class);
                //                TickerResp tickerResp = JSON.parseObject(res, TickerResp.class);
                int count = tickerResp.getCount();
                String next_url = tickerResp.getNext_url();
                List<Ticker> results = tickerResp.getResults();
                for (Ticker ticker : results) {
                    fw.write(ticker.toString());
                    fw.write("\n");
                }
                fw.flush();

                System.out.println(String.format("index=%d, count=%d", index++, count));
                if (count < 100) {
                    break;
                }

                next_url += apiKeyParam;
                get.setURI(new URI(next_url, false));
                TimeUnit.SECONDS.sleep(15);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    public static void main(String[] args) {
        getStock("XNYS");
        getStock("XNAS");
    }
}
