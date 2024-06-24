package luonq.ivolatility;

import bean.NearestOption;
import bean.NearestOptionResp;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import com.alibaba.fastjson.JSON;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.methods.GetMethod;
import org.slf4j.LoggerFactory;
import util.BaseUtils;

import java.io.InputStream;
import java.util.List;
import java.util.Set;

public class GetNearestCallOption {

    public static void main(String[] args) {
        ((LoggerContext) LoggerFactory.getILoggerFactory()).getLogger("org.apache.http").setLevel(Level.INFO);
        ((LoggerContext) LoggerFactory.getILoggerFactory()).getLogger("httpclient.wire").setLevel(Level.INFO);
        ((LoggerContext) LoggerFactory.getILoggerFactory()).getLogger("org.apache.commons").setLevel(Level.ERROR);

        Set<String> weekOptionStock = BaseUtils.getWeekOptionStock();

        HttpClient httpClient = new HttpClient();
        for (String stock : weekOptionStock) {
            String url = String.format("https://restapi.ivolatility.com/equities/eod/nearest-option-tickers-with-prices-nbbo?apiKey=S3j7pBefWG0J0glb&symbol=%s&dte=1&cp=C&delta=0.6", stock);
            GetMethod getMethod = new GetMethod(url);

            try {
                httpClient.executeMethod(getMethod);
                InputStream content = getMethod.getResponseBodyAsStream();
                NearestOptionResp resp = JSON.parseObject(content, NearestOptionResp.class);
                List<NearestOption> data = resp.getData();
                if (CollectionUtils.isNotEmpty(data)) {
                    NearestOption nearestOption = data.get(0);
                    System.out.println(nearestOption.getOption_symbol());
                }
//                Thread.sleep(500);
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                getMethod.releaseConnection();
            }
        }
    }
}
