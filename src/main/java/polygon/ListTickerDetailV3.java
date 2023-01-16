package polygon;

import barchart.Login;
import bean.TickerDetailV3;
import bean.TickerDetailV3Resp;
import com.alibaba.fastjson.JSON;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.methods.GetMethod;

import java.io.FileWriter;
import java.io.InputStream;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Created by Luonanqin on 2023/1/17.
 */
public class ListTickerDetailV3 {

    public static void main(String[] args) throws Exception {
        String market = "XNAS";

        String apiKeyParam = "&apiKey=Ea9FNNIdlWnVnGcoTpZsOWuCWEB3JAqY";

        List<String> stockList = Login.getStockList(market);
        HttpClient httpclient = new HttpClient();
        FileWriter fw;
        String fileName = String.format("%s.txt", market);
        try {
            fw = new FileWriter(fileName);

            for (String stock : stockList) {
                String url = "https://api.polygon.io/v3/reference/tickers/" + stock + "?" + apiKeyParam;
                GetMethod get = new GetMethod(url);
                int code = httpclient.executeMethod(get);
                if (code != 200) {
                    System.err.println("request failed");
                }

                InputStream stream = get.getResponseBodyAsStream();
                TickerDetailV3Resp tickerResp = JSON.parseObject(stream, TickerDetailV3Resp.class);
                TickerDetailV3 tickerDetailV3 = tickerResp.getResults();
                String listDate = tickerDetailV3.getList_date();
                String tickerName = tickerDetailV3.getTicker();

                String data = tickerName + "\t" + listDate + "\n";
                fw.write(data);
                fw.flush();

                System.out.print(data);
                TimeUnit.SECONDS.sleep(14);

                stream.close();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
