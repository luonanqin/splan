package luonq.ivolatility;

import bean.OptionRT;
import bean.OptionRTResp;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import com.alibaba.fastjson.JSON;
import com.google.common.collect.Lists;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.stream.Collectors;

public class GetRealTimeIV {

    public static void main(String[] args) throws Exception {
        ((LoggerContext) LoggerFactory.getILoggerFactory()).getLogger("org.apache.http").setLevel(Level.INFO);
        ((LoggerContext) LoggerFactory.getILoggerFactory()).getLogger("httpclient.wire").setLevel(Level.INFO);
        ((LoggerContext) LoggerFactory.getILoggerFactory()).getLogger("org.apache.commons").setLevel(Level.ERROR);

        List<String> optionList = Lists.newArrayList("AAPL  240621C00212500",
          "UPST  240621C00021500",
          "DELL  240621C00147000",
          "TSLA  240621C00182500",
          "NVDA  240621C00134000",
          "TSM   240621C00177500",
          "NIO   240621C00004000",
          "BILI  240621C00015500",
          "GME   240621C00024000",
          "AAL   240621C00011000",
          "WMB   240621C00041000");
        optionList = optionList.stream().map(o -> o.replaceAll(" ", "+")).collect(Collectors.toList());

        String options = StringUtils.join(optionList, ",");
        String url = "https://restapi.ivolatility.com/equities/rt/options-rawiv?apiKey=5uO8Wid7AY945OJ2&symbols=" + options;

        HttpClient httpClient = new HttpClient();
        GetMethod get = new GetMethod(url);
        Timer timer = new Timer();
        timer.scheduleAtFixedRate(new TimerTask() {

            @Override
            public void run() {
                try {
                    long curent = System.currentTimeMillis();
                    httpClient.executeMethod(get);
                    InputStream content = get.getResponseBodyAsStream();
                    OptionRTResp resp = JSON.parseObject(content, OptionRTResp.class);
                    List<OptionRT> data = resp.getData();
                    if (CollectionUtils.isNotEmpty(data)) {
                        for (OptionRT rt : data) {
                            String symbol = rt.getSymbol();
                            String timestamp = rt.getTimestamp();
                            double iv = rt.getIv();
                            String line = symbol + "\t" + timestamp + "\t" + iv + "\t" + curent;
                            System.out.println(line);
                        }
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }, 1000, 2000);
    }
}
