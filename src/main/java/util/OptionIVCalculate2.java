package util;

import bean.OptionRTResp;
import bean.OptionSnapshotResp;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import com.alibaba.fastjson.JSON;
import com.google.common.collect.Maps;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.methods.GetMethod;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

public class OptionIVCalculate2 {


    public static void main(String[] args) {
        //        Settings settings = new Settings();
        //        settings.setEvaluationDate(new Date(1, 1, 2022));
//        OptionFundamentals optionIndex = OptionCalcUtils.calcOptionIndex(
//          Right.PUT,
//          212.49, //对应标的资产的价格
//          210,  //期权行权价格
//          0.0526,  //无风险利率，这里是取的美国国债利率
//          0,  //股息率，大部分标的为0
//          0.224632, // 隐含波动率
//          LocalDate.of(2024, 6, 14), //对应预测价格的日期，要小于期权到期日
//          LocalDate.of(2024, 6, 21));  //期权到期日
//        System.out.println("value: " + optionIndex.getPredictedValue()); //计算出的期权预测价格
//        System.out.println("delta: " + optionIndex.getDelta());
//        System.out.println("gamma " + optionIndex.getGamma());
//        System.out.println("theta " + optionIndex.getTheta());
//        System.out.println("vega: " + optionIndex.getVega());
//        System.out.println("rho: " + optionIndex.getRho());
        ((LoggerContext) LoggerFactory.getILoggerFactory()).getLogger("org.apache.http").setLevel(Level.INFO);
        ((LoggerContext) LoggerFactory.getILoggerFactory()).getLogger("httpclient.wire").setLevel(Level.INFO);
        ((LoggerContext) LoggerFactory.getILoggerFactory()).getLogger("org.apache.commons").setLevel(Level.ERROR);

//        String optionCode = "NVDA240621P00126000";
//        String optionCode = "COIN240621P00240000";
//        String optionCode = "NIO240621P00004500";
//        String optionCode = "FUTU240621P00066000";
//        String optionCode = "MSFT240621P00450000";
//        String optionCode = "BABA240621P00076000";
//        String optionCode = "JD240621P00030500";
//        String optionCode = "NTES240621P00100000";
        String optionCode = "PATH240621P00012500";
        String type = optionCode.substring(optionCode.length() - 9, optionCode.length() - 8);
        String priceStr = optionCode.substring(optionCode.length() - 8);
        int priceInt = Integer.valueOf(priceStr);
        String strikePrice = BigDecimal.valueOf(priceInt).divide(BigDecimal.valueOf(1000)).setScale(1, RoundingMode.DOWN).toString();
        if (strikePrice.contains(".0")) {
            strikePrice = strikePrice.substring(0, strikePrice.length() - 2);
        }
        double strikePriced = Double.valueOf(strikePrice);
        String stock = optionCode.substring(0, optionCode.length() - 15);
        String strike = optionCode.substring(optionCode.length() - 15);
        Map<String, Double> ivMap = Maps.newHashMap();

        HttpClient httpClient = new HttpClient();
        GetMethod get = new GetMethod(String.format("https://restapi.ivolatility.com/equities/rt/options-rawiv?apiKey=S3j7pBefWG0J0glb&symbols=%s++%s", stock, strike));
        Timer timer = new Timer();
        timer.scheduleAtFixedRate(new TimerTask() {

            @Override
            public void run() {
                try {
                    httpClient.executeMethod(get);
                    InputStream resp = get.getResponseBodyAsStream();
                    OptionRTResp snap = JSON.parseObject(resp, OptionRTResp.class);
                    double impliedVolatility = snap.getData().get(0).getIv();
                    ivMap.put(optionCode, impliedVolatility);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }, 1000, 60000);

        HttpClient httpClient2 = new HttpClient();
        GetMethod trade = new GetMethod(String.format("https://api.polygon.io/v3/snapshot/options/%s/O:%s?apiKey=Ea9FNNIdlWnVnGcoTpZsOWuCWEB3JAqY", stock, optionCode));
        Timer timer2 = new Timer();
        timer2.scheduleAtFixedRate(new TimerTask() {

            @Override
            public void run() {
                try {
                    httpClient2.executeMethod(trade);
                    InputStream resp = trade.getResponseBodyAsStream();
                    OptionSnapshotResp snap = JSON.parseObject(resp, OptionSnapshotResp.class);
                    if (!ivMap.containsKey(optionCode)) {
                        return;
                    }
                    double impliedVolatility = ivMap.get(optionCode);
                    double price = snap.getResults().getUnderlying_asset().getPrice();
                    double putPredictedValue = BaseUtils.getPutPredictedValue(price, strikePriced, 0.0526, impliedVolatility, "2024-06-17", "2024-06-21");
                    System.out.printf("%.2f\t%.2f\t%.5f\n", price, putPredictedValue, impliedVolatility);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }, 2000, 1000);
    }
}
