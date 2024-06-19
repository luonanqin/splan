package luonq.strategy;

import bean.OptionDaily;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import com.alibaba.fastjson.JSON;
import com.google.common.collect.Lists;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 只抓苹果每周五的期权，代码为离开盘价最近的价外call和put，直接以开盘收盘最高最低计算收益
 */
public class Strategy31 {

    public static CloseableHttpClient httpClient = HttpClients.createDefault();
    public static BlockingQueue<CloseableHttpClient> queue;
    public static ThreadPoolExecutor cachedThread;
    public static String apiKey = "&apiKey=Ea9FNNIdlWnVnGcoTpZsOWuCWEB3JAqY";

    public static OptionDaily getOptionDaily(String code, String date) throws Exception {
        String url = String.format("https://api.polygon.io/v1/open-close/%s/%s?adjusted=true&apiKey=Ea9FNNIdlWnVnGcoTpZsOWuCWEB3JAqY",
          code, date);

        HttpGet get = new HttpGet(url);
        try {
            CloseableHttpResponse execute = httpClient.execute(get);
            InputStream stream = execute.getEntity().getContent();
            OptionDaily resp = JSON.parseObject(stream, OptionDaily.class);
            String status = resp.getStatus();
            if (!StringUtils.equalsIgnoreCase(status, "OK")) {
                System.out.println("get failed. " + url);
                return null;
            }

            return resp;
        } finally {
            get.releaseConnection();
        }
    }

    public static void main(String[] args) throws Exception {
        ((LoggerContext) LoggerFactory.getILoggerFactory()).getLogger("org.apache.http").setLevel(Level.INFO);
        ((LoggerContext) LoggerFactory.getILoggerFactory()).getLogger("httpclient.wire").setLevel(Level.INFO);

        List<String> list = Lists.newArrayList();
        // 价外一档
//        list.add("2024-01-05	O:AAPL240105C00182500	O:AAPL240105P00180000");
//        list.add("2024-01-12	O:AAPL240112C00187500	O:AAPL240112P00185000");
//        list.add("2024-01-19	O:AAPL240119C00190000	O:AAPL240119P00187500");
//        list.add("2024-01-26	O:AAPL240126C00195000	O:AAPL240126P00192500");
//        list.add("2024-02-02	O:AAPL240202C00180000	O:AAPL240202P00177500");
//        list.add("2024-02-09	O:AAPL240209C00190000	O:AAPL240209P00187500");
//        list.add("2024-02-16	O:AAPL240216C00185000	O:AAPL240216P00182500");
//        list.add("2024-02-23	O:AAPL240223C00185000	O:AAPL240223P00182500");
//        list.add("2024-03-01	O:AAPL240301C00180000	O:AAPL240301P00177500");
//        list.add("2024-03-08	O:AAPL240308C00170000	O:AAPL240308P00167500");
//        list.add("2024-03-15	O:AAPL240315C00172500	O:AAPL240315P00170000");
//        list.add("2024-03-22	O:AAPL240322C00172500	O:AAPL240322P00170000");
//        list.add("2024-03-28	O:AAPL240328C00172500	O:AAPL240328P00170000");
//        list.add("2024-04-05	O:AAPL240405C00170000	O:AAPL240405P00167500");
//        list.add("2024-04-12	O:AAPL240412C00175000	O:AAPL240412P00172500");
//        list.add("2024-04-19	O:AAPL240419C00167500	O:AAPL240419P00165000");
//        list.add("2024-04-26	O:AAPL240426C00170000	O:AAPL240426P00167500");
//        list.add("2024-05-03	O:AAPL240503C00187500	O:AAPL240503P00185000");
//        list.add("2024-05-10	O:AAPL240510C00185000	O:AAPL240510P00182500");
//        list.add("2024-05-17	O:AAPL240517C00190000	O:AAPL240517P00187500");
//        list.add("2024-05-24	O:AAPL240524C00190000	O:AAPL240524P00187500");

        // 价外二档
        list.add("2024-01-05	O:AAPL240105C00185000	O:AAPL240105P00177500");
        list.add("2024-01-12	O:AAPL240112C00190000	O:AAPL240112P00182500");
        list.add("2024-01-19	O:AAPL240119C00192500	O:AAPL240119P00185000");
        list.add("2024-01-26	O:AAPL240126C00197500	O:AAPL240126P00190000");
        list.add("2024-02-02	O:AAPL240202C00182500	O:AAPL240202P00175000");
        list.add("2024-02-09	O:AAPL240209C00192500	O:AAPL240209P00185000");
        list.add("2024-02-16	O:AAPL240216C00187500	O:AAPL240216P00180000");
        list.add("2024-02-23	O:AAPL240223C00187500	O:AAPL240223P00180000");
        list.add("2024-03-01	O:AAPL240301C00182500	O:AAPL240301P00175000");
        list.add("2024-03-08	O:AAPL240308C00172500	O:AAPL240308P00165000");
        list.add("2024-03-15	O:AAPL240315C00175000	O:AAPL240315P00167500");
        list.add("2024-03-22	O:AAPL240322C00175000	O:AAPL240322P00167500");
        list.add("2024-03-28	O:AAPL240328C00175000	O:AAPL240328P00167500");
        list.add("2024-04-05	O:AAPL240405C00172500	O:AAPL240405P00165000");
        list.add("2024-04-12	O:AAPL240412C00177500	O:AAPL240412P00170000");
        list.add("2024-04-19	O:AAPL240419C00170000	O:AAPL240419P00162500");
        list.add("2024-04-26	O:AAPL240426C00172500	O:AAPL240426P00165000");
        list.add("2024-05-03	O:AAPL240503C00190000	O:AAPL240503P00182500");
        list.add("2024-05-10	O:AAPL240510C00187500	O:AAPL240510P00180000");
        list.add("2024-05-17	O:AAPL240517C00192500	O:AAPL240517P00185000");
        list.add("2024-05-24	O:AAPL240524C00192500	O:AAPL240524P00185000");

        for (String line : list) {
            String[] split = line.split("\t");
            String date = split[0];
            String call = split[1];
            String put = split[2];
            OptionDaily callDaily = getOptionDaily(call, date);
            //            System.out.println(callDaily.getSymbol() + "\t" + callDaily.getOpen() + "\t" + callDaily.getClose() + "\t" + callDaily.getHigh() + "\t" + callDaily.getLow());
            OptionDaily putDaily = getOptionDaily(put, date);
            //            System.out.println(putDaily.getSymbol() + "\t" + putDaily.getOpen() + "\t" + putDaily.getClose() + "\t" + putDaily.getHigh() + "\t" + putDaily.getLow());

            OptionDaily daily = getOptionDaily("AAPL", date);
            LocalDate day = LocalDate.parse(date, DateTimeFormatter.ofPattern("yyyy-MM-dd"));
            LocalDate yesterday = day.minusDays(1);
            OptionDaily lastDaily = getOptionDaily("AAPL", yesterday.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")));
            double ratio = Math.abs(daily.getOpen() - lastDaily.getClose()) / lastDaily.getClose() * 100;

            System.out.println(date + "\t" + daily.getOpen() + "\t" + call + "\t" + put + "\t" + ratio + "\t" + callDaily.getOpen() + "\t" + callDaily.getClose() + "\t" + callDaily.getHigh() + "\t" + callDaily.getLow() +
              "\t" + putDaily.getOpen() + "\t" + putDaily.getClose() + "\t" + putDaily.getHigh() + "\t" + putDaily.getLow());
        }
    }
}
