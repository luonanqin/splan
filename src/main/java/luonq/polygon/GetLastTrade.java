package luonq.polygon;

import bean.BOLL;
import bean.LastTrade;
import bean.LastTradeResp;
import bean.StockKLine;
import com.alibaba.fastjson.JSON;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.lang3.StringUtils;
import util.BaseUtils;
import util.Constants;

import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Created by Luonanqin on 2023/5/5.
 */
public class GetLastTrade {

    public static void main(String[] args) throws Exception {
        String apiKey = "apiKey=Ea9FNNIdlWnVnGcoTpZsOWuCWEB3JAqY";
        String api = "https://api.polygon.io/v2/last/trade/";

        Map<String, String> stockMap = BaseUtils.getFileMap(Constants.HIS_BASE_PATH + "2023daily/");
        Map<String, StockKLine> filtedStockMap = Maps.newHashMap();
        for (String stock : stockMap.keySet()) {
            String file = stockMap.get(stock);
            List<StockKLine> stockKLines = BaseUtils.loadDataToKline(file, 2023);
            StockKLine stockKLine = stockKLines.get(0);
            if (stockKLine.getClose() > 7) {
                filtedStockMap.put(stock, stockKLine);
            }
        }

        Set<String> filtedStock = Sets.newHashSet();
        Map<String, String> bollMap = BaseUtils.getFileMap(Constants.HIS_BASE_PATH + "mergeBoll/");
        for (String stock : filtedStockMap.keySet()) {
            String file = bollMap.get(stock);
            if (StringUtils.isBlank(file)) {
                continue;
            }
            List<BOLL> bolls = BaseUtils.readBollFile(file, 2023, 2022);
            BOLL boll = bolls.get(0);
            StockKLine stockKLine = filtedStockMap.get(stock);
            if (boll.getMb()> stockKLine.getOpen()) {
                filtedStock.add(stock);
            }
        }
        System.out.println("filted stock size: " + filtedStock.size());

        long begin = System.currentTimeMillis();
        HttpClient httpclient = new HttpClient();
        for (String stock : filtedStock) {
            String url = api + stock + "?" + apiKey;
            GetMethod get = new GetMethod(url);

            try {
                int code = httpclient.executeMethod(get);
                if (code != 200) {
                    String error = get.getResponseBodyAsString();
                    System.out.println(stock + " error=" + error);
                    continue;
                }
                InputStream stream = get.getResponseBodyAsStream();
                LastTradeResp result = JSON.parseObject(stream, LastTradeResp.class);
                LastTrade lastTrade = result.getResults();
                System.out.println(stock + " price=" + lastTrade.getP() + " size=" + lastTrade.getS());
            } catch (Exception e) {
                System.out.println(stock + " " + e.getMessage());
            } finally {
                get.releaseConnection();
            }
        }
        System.out.println("cost: " + ((System.currentTimeMillis() - begin) / 1000) + "s");
    }
}
