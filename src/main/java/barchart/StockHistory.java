package barchart;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.openqa.selenium.By;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class StockHistory {

    public static void main(String[] args) throws Exception {
        String downloadPath = "src/main/resources/historicalData/stock"; // 公司和家里通用

        // has option stock
        String market = "XNAS";
        List<String> stockList = getStockList(market);

        System.getProperties().setProperty("webdriver.chrome.driver", "chromedriver");
        ChromeOptions chromeOptions = new ChromeOptions();
        ChromeDriver driver = new ChromeDriver(chromeOptions);

        // login
        loginBarchart(driver);

        File file = new File(downloadPath);
        if (!file.exists()) {
            System.out.println("please check the download path");
            return;
        }
        String[] existList = file.list();
        String searchKey = "_daily_historical-data";
        Set<String> hasDownload = Arrays.stream(existList).filter(s -> s.contains(searchKey)).map(s -> s.substring(0, s.indexOf(searchKey))).collect(Collectors.toSet());

        // download historical stock data
        downloadHistoricalStock(driver, stockList, hasDownload);

        driver.quit();
    }

    private static void downloadHistoricalStock(ChromeDriver driver, List<String> stockList, Set<String> hasDownload) throws InterruptedException {
        File file = new File("/Users/luonanqin/Downloads");
        for (String stock : stockList) {
            if (hasDownload.contains(stock.toLowerCase())) {
                System.out.println("has downloaded: " + stock);
                continue;
            }
            driver.get("https://www.barchart.com/my/price-history/download/" + stock);
            driver.findElement(By.xpath("//a[@data-historical='historical']")).click();

            int retryTimes = 1;
            while (true) {
                String[] stockFile = file.list((dir, name) -> name.startsWith(stock.toLowerCase() + "_"));
                if (ArrayUtils.isNotEmpty(stockFile)) {
                    System.out.println("finish downloading " + stock);
                    break;
                }
                // 点击下载后，需要等一会儿确定文件已下载再跳转到下一个代码
                System.out.println("downloading " + stock + " " + retryTimes);
                TimeUnit.SECONDS.sleep(1);

                // 最多重试20次，超过则表示已达每日下载次数上限
                if (retryTimes > 20) {
                    return;
                }
                retryTimes++;
            }
        }
    }

    public static List<String> getStockList(String market) throws IOException {
        List<String> stockList = Lists.newArrayList();
        BufferedReader br = new BufferedReader(new InputStreamReader(StockHistory.class.getResourceAsStream("/historicalData/code/hasOption/" + market)));
        String hasOption;
        while (StringUtils.isNotBlank(hasOption = br.readLine())) {
            stockList.add(hasOption);
        }
        br.close();

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

        stockList = stockList.stream().filter(s -> openDate.get(s) != null).sorted((o1, o2) -> {
            String date1 = openDate.get(o1);
            String date2 = openDate.get(o2);
            LocalDate localDate1 = LocalDate.parse(date1);
            LocalDate localDate2 = LocalDate.parse(date2);
            return localDate1.isBefore(localDate2) ? -1 : 1;
        }).collect(Collectors.toList());

        return stockList;
    }

    private static void loginBarchart(ChromeDriver driver) throws InterruptedException {
        System.out.println("loading login page");
        driver.get("https://www.barchart.com/login");
        System.out.println("finish loding");
        //        WebElement loginBtn = driver.findElement(By.className("login"));
        //        loginBtn.click();
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

}
