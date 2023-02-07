package util;

import barchart.StockHistory;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import org.apache.commons.lang3.StringUtils;
import org.openqa.selenium.By;
import org.openqa.selenium.chrome.ChromeDriver;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Created by Luonanqin on 2023/2/5.
 */
public class BaseUtils {

    public static void loginBarchart(ChromeDriver driver) throws InterruptedException {
        System.out.println("loading login page");
        driver.get("https://www.barchart.com/login");
        System.out.println("finish loding");
        TimeUnit.SECONDS.sleep(5);

        // login
        System.out.println("input email and pwd");
        driver.findElement(By.cssSelector("[name='email']")).sendKeys("qinnanluo@sina.com");
        driver.findElement(By.cssSelector("[name='password']")).sendKeys("luonq134931");
        driver.findElement(By.className("login-button")).click();
        System.out.println("start login");
        // 登录后需要休眠一会儿确定已经登录
        while (true) {
            String MyAccount;
            try {
                MyAccount = driver.findElement(By.tagName("span")).getText();
                if ("My Account".equals(MyAccount)) {
                    System.out.println("finish login");
                    break;
                }
            } catch (Exception e) {
                System.out.println("wait login");
                TimeUnit.SECONDS.sleep(1);
            }
        }
    }

    public static Map<String, String> getOpenData(String market) throws IOException {
        Map<String, String> openDate = Maps.newHashMap();
        BufferedReader openBr = new BufferedReader(new FileReader("src/main/resources/historicalData/open/" + market + ".txt"));
        String open;
        while (StringUtils.isNotBlank(open = openBr.readLine())) {
            String[] split = open.split("\t");
            String code = split[0];
            String date = split[1];
            if (StringUtils.isBlank(date) || date.equalsIgnoreCase("null")) {
                continue;
            }

            openDate.put(code, date);
        }
        return openDate;
    }

    public static List<String> getHasOptionStockList(String market) throws IOException {
        List<String> stockList = Lists.newArrayList();
        BufferedReader br = new BufferedReader(new InputStreamReader(StockHistory.class.getResourceAsStream("/historicalData/code/hasOption/" + market)));
        String hasOption;
        while (StringUtils.isNotBlank(hasOption = br.readLine())) {
            stockList.add(hasOption);
        }
        br.close();

        return stockList;
    }

    public static List<String> getFiltedStockList(String market) throws IOException {
        List<String> stockList = getHasOptionStockList(market);

        Map<String, String> openDate = getOpenData(market);

        stockList = stockList.stream().filter(s -> openDate.get(s) != null).sorted((o1, o2) -> {
            String date1 = openDate.get(o1);
            String date2 = openDate.get(o2);
            LocalDate localDate1 = LocalDate.parse(date1);
            LocalDate localDate2 = LocalDate.parse(date2);
            return localDate1.isBefore(localDate2) ? -1 : 1;
        }).collect(Collectors.toList());

        return stockList;
    }
}
