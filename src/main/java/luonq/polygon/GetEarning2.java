package luonq.polygon;

import bean.StockFinancialResp;
import bean.StockFinancials;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import com.alibaba.fastjson.JSON;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.methods.GetMethod;
import org.slf4j.LoggerFactory;
import util.BaseUtils;
import util.Constants;

import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class GetEarning2 {

    // https://api.polygon.io/vX/reference/financials?limit=100&apiKey=Ea9FNNIdlWnVnGcoTpZsOWuCWEB3JAqY&filing_date.gte=2024-06-12&filing_date.lt=2024-06-13
    public static void main(String[] args) throws Exception {
        ((LoggerContext) LoggerFactory.getILoggerFactory()).getLogger("org.apache.http").setLevel(Level.INFO);
        ((LoggerContext) LoggerFactory.getILoggerFactory()).getLogger("httpclient.wire").setLevel(Level.INFO);
        ((LoggerContext) LoggerFactory.getILoggerFactory()).getLogger("org.apache.commons").setLevel(Level.ERROR);

        Set<String> weekOptionStock = BaseUtils.getWeekOptionStock();

        Map<String/* date */, Set<String>/* stock */> map = Maps.newHashMap();
        HttpClient httpClient = new HttpClient();
        for (String stock : weekOptionStock) {
            String url = String.format("https://api.polygon.io/vX/reference/financials?limit=100&apiKey=Ea9FNNIdlWnVnGcoTpZsOWuCWEB3JAqY&ticker=%s&filing_date.gte=2022-01-01&filing_date.lte=2024-06-11", stock);

            GetMethod get = new GetMethod(url);
            httpClient.executeMethod(get);
            InputStream content = get.getResponseBodyAsStream();
            StockFinancialResp resp = JSON.parseObject(content, StockFinancialResp.class);
            List<StockFinancials> results = resp.getResults();
            if (CollectionUtils.isNotEmpty(results)) {
                for (StockFinancials result : results) {
                    String date = result.getFiling_date();
                    if (!map.containsKey(date)) {
                        map.put(date, Sets.newHashSet());
                    }

                    map.get(date).add(stock);
                }
            }
            get.releaseConnection();
            System.out.println("finish " + stock);
        }

        for (String date : map.keySet()) {
            Set<String> set = map.get(date);
            List<String> results = set.stream().map(l -> l + " TAS").collect(Collectors.toList());
            BaseUtils.writeFile(Constants.HIS_BASE_PATH + "earning_polygon/" + date, results);
        }
    }
}
