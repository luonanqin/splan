package luonq.polygon;

import bean.TickerDetailV3;
import bean.TickerDetailV3Resp;
import com.alibaba.fastjson.JSON;
import com.google.common.collect.Lists;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.lang3.StringUtils;
import util.BaseUtils;
import util.Constants;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.InputStream;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Created by Luonanqin on 2023/1/17.
 */
public class ListTickerDetailV3 {

    public static void main(String[] args) throws Exception {
        String market = "XNYS";

        String apiKeyParam = "apiKey=Ea9FNNIdlWnVnGcoTpZsOWuCWEB3JAqY";

        List<String> stockList = BaseUtils.getHasOptionStockList(market);
        HttpClient httpclient = new HttpClient();
        FileWriter fw;
        BufferedReader br;
        String fileName = String.format("%s.txt", market);
        try {
            fw = new FileWriter(fileName);
            br = new BufferedReader(new FileReader(Constants.HIS_BASE_PATH + "open/" + fileName));

            List<String> hasDownload = Lists.newArrayList();
            String download;
            while (StringUtils.isNotBlank(download = br.readLine())) {
                String code = download.substring(0, download.indexOf("\t"));
                hasDownload.add(code);
            }

            for (String stock : stockList) {
                if (hasDownload.contains(stock)) {
                    System.out.println("has downloaded: " + stock);
                    continue;
                }

                String url = "https://api.polygon.io/v3/reference/tickers/" + stock + "?" + apiKeyParam;
                GetMethod get = new GetMethod(url);
                int code = httpclient.executeMethod(get);
                if (code != 200) {
                    System.err.println(stock + " request failed. code=" + code);
                    continue;
                }

                InputStream stream = get.getResponseBodyAsStream();
                TickerDetailV3Resp tickerResp = JSON.parseObject(stream, TickerDetailV3Resp.class);
                TickerDetailV3 tickerDetailV3 = tickerResp.getResults();
                String data;
                if (tickerDetailV3 == null || StringUtils.isBlank(tickerDetailV3.getList_date()) || StringUtils.isBlank(tickerDetailV3.getTicker())) {
                    data = stock + "\t" + null + "\n";
                } else {
                    String listDate = tickerDetailV3.getList_date();
                    String tickerName = tickerDetailV3.getTicker();

                    data = tickerName + "\t" + listDate + "\n";
                }
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
