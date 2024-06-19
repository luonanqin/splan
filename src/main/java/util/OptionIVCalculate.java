package util;

import bean.OptionSnapshotResp;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import com.alibaba.fastjson.JSON;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.methods.GetMethod;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Timer;
import java.util.TimerTask;

public class OptionIVCalculate {


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

        HttpClient httpClient = new HttpClient();
        GetMethod get = new GetMethod(String.format("https://api.polygon.io/v3/snapshot/options/%s/O:%s?apiKey=Ea9FNNIdlWnVnGcoTpZsOWuCWEB3JAqY", stock, optionCode));
        Timer timer = new Timer();
        timer.scheduleAtFixedRate(new TimerTask() {

            @Override
            public void run() {
                try {
                    httpClient.executeMethod(get);
                    InputStream resp = get.getResponseBodyAsStream();
                    OptionSnapshotResp snap = JSON.parseObject(resp, OptionSnapshotResp.class);
                    double impliedVolatility = snap.getResults().getImplied_volatility();
                    double price = snap.getResults().getUnderlying_asset().getPrice();
                    double putPredictedValue = BaseUtils.getPutPredictedValue(price, strikePriced, 0.0526, impliedVolatility, "2024-06-17", "2024-06-21");
                    System.out.printf("%.2f\t%.2f\t%.5f\n", price, putPredictedValue, impliedVolatility);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }, 1000, 1000);
    }
}
