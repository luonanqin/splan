package luonq.ivolatility;

import bean.AggregateOptionIV;
import bean.AggregateOptionIVResp;
import com.alibaba.fastjson.JSON;
import com.google.common.collect.Maps;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.lang3.StringUtils;
import util.BaseUtils;
import util.Constants;

import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

public class GetAggregateImpliedVolatility {

    public static Map<String/* data */, Map<String/* optionCode */, Double/* IV */>> dateToIvMap = Maps.newHashMap();
    public static HttpClient httpClient = new HttpClient();

    public static void init() throws Exception {
        Map<String, String> fileMap = BaseUtils.getFileMap(Constants.USER_PATH + "optionData/aggregateIV");

        for (String stock : fileMap.keySet()) {
            String dirPath = fileMap.get(stock);

            Map<String, String> optionFileMap = BaseUtils.getFileMap(dirPath);
            for (String optionCode : optionFileMap.keySet()) {
                String optionFilePath = optionFileMap.get(optionCode);

                List<String> lines = BaseUtils.readFile(optionFilePath);
                for (String line : lines) {
                    String[] split = line.split("\t");
                    String date = split[0].substring(0, 10);
                    double iv = Double.parseDouble(split[1]);

                    if (!dateToIvMap.containsKey(date)) {
                        dateToIvMap.put(date, Maps.newHashMap());
                    }

                    dateToIvMap.get(date).put(optionCode, iv);
                }
            }
        }
    }

    // https://restapi.ivolatility.com/equities/intraday/single-equity-option-rawiv?apiKey=S3j7pBefWG0J0glb&symbol=C&date=2024-01-11&expDate=2024-01-12&strike=53&optType=CALL&minuteType=MINUTE_1
    // https://restapi.ivolatility.com/equities/intraday/single-equity-optionsymbol-rawiv?apiKey=S3j7pBefWG0J0glb&optionSymbol=AAPL%20%20240503P00180000&date=2024-05-03&minuteType=MINUTE_1
    public static double getAggregateIv(String optionCode, String date) throws Exception {
        if (dateToIvMap.containsKey(date) && dateToIvMap.get(date).containsKey(optionCode)) {
            return dateToIvMap.get(date).get(optionCode);
        }

        String stock = optionCode.substring(0, optionCode.length() - 15);
        //        String optionInfo = optionCode.substring(optionCode.length() - 15);
        String expireDate = optionCode.substring(optionCode.length() - 15, optionCode.length() - 9);
        String expireDay = LocalDate.parse(expireDate, DateTimeFormatter.ofPattern("yyMMdd")).format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        String type = optionCode.substring(optionCode.length() - 9, optionCode.length() - 8);
        String optionType = StringUtils.equalsIgnoreCase(type, "C") ? "CALL" : "PUT";
        String priceStr = optionCode.substring(optionCode.length() - 8);
        int priceInt = Integer.valueOf(priceStr);
        String strikePrice = BigDecimal.valueOf(priceInt).divide(BigDecimal.valueOf(1000)).setScale(1, RoundingMode.DOWN).toString();
        if (strikePrice.contains(".0")) {
            strikePrice = strikePrice.substring(0, strikePrice.length() - 2);
        }

        String optionFileDir = Constants.USER_PATH + "optionData/aggregateIV/" + stock + "/";
        String optionFilePath = optionFileDir + optionCode;
        List<String> lines = BaseUtils.readFile(optionFilePath);

        //        String url = String.format("https://restapi.ivolatility.com/equities/intraday/single-equity-optionsymbol-rawiv?apiKey=S3j7pBefWG0J0glb&optionSymbol=%s&date=%s&minuteType=MINUTE_1", optionCode, date);
        String url = String.format("https://restapi.ivolatility.com/equities/intraday/single-equity-option-rawiv?apiKey=S3j7pBefWG0J0glb&symbol=%s&date=%s&expDate=%s&strike=%s&optType=%s&minuteType=MINUTE_1", stock, date, expireDay, strikePrice, optionType);
        GetMethod get = new GetMethod(url);
        double hisIv = 0d;
        String timestamp = "";
        try {
            httpClient.executeMethod(get);
            InputStream content = get.getResponseBodyAsStream();
            AggregateOptionIVResp resp = JSON.parseObject(content, AggregateOptionIVResp.class);
            List<AggregateOptionIV> dataList = resp.getData();
            for (int i = 0; i < 10; i++) {
                AggregateOptionIV iv = dataList.get(i);
                String optionBidDateTime = iv.getOptionBidDateTime();
                if (!optionBidDateTime.startsWith(date)) {
                    continue;
                }
                double optionIv = iv.getOptionIv();
                if (optionIv == -1) {
                    continue;
                }
                timestamp = iv.getTimestamp();
                hisIv = optionIv;
                break;
            }
            if (hisIv == 0) {
                hisIv = -2d;
            }
        } catch (Exception e) {
            System.out.println("error: " + url);
            e.printStackTrace();
            return 0;
        } finally {
            get.releaseConnection();
        }

        lines.add(timestamp + "\t" + hisIv);
        System.out.println(stock + ": " + lines);
        BaseUtils.createDirectory(optionFileDir);
        BaseUtils.writeFile(optionFilePath, lines);

        return hisIv;
    }
}
