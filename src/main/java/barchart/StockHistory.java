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

        System.getProperties().setProperty("webdriver.chrome.driver", "chromedriver");
        ChromeOptions chromeOptions = new ChromeOptions();
        ChromeDriver driver = new ChromeDriver(chromeOptions);

        // login
        loginBarchart(driver);

        // has option stock
        String market = "XNAS";
        List<String> stockList = getFiltedStockList(market);

        /** download historical stock data */

        // for daily
                downloadHistoricalStock(driver, stockList, "daily", 100);

        // for weekly
        //        downloadHistoricalStock(driver, stockList, "weekly", 30);

        // for monthly
        //        downloadHistoricalStock(driver, stockList, "monthly", 30);

        // for quarterly
        //        downloadHistoricalStock(driver, stockList, "quarterly", 30);

        driver.quit();
    }

    private static void downloadHistoricalStock(ChromeDriver driver, List<String> stockList, String frequency, int count) throws InterruptedException {
        String hasDownloadPath = "src/main/resources/historicalData/" + frequency;
        File hasDownloadFile = new File(hasDownloadPath);
        if (!hasDownloadFile.exists()) {
            System.out.println("please check the download path");
            return;
        }
        String[] existList = hasDownloadFile.list();
        String searchKey = "_" + frequency + "_historical-data";
        Set<String> hasDownload = Arrays.stream(existList).filter(s -> s.contains(searchKey)).map(s -> s.substring(0, s.indexOf(searchKey))).collect(Collectors.toSet());

        File downloadDir = new File("/Users/luonanqin/Downloads"); // 公司和家里通用
        for (String stock : stockList) {
            if (hasDownload.contains(stock.toLowerCase())) {
                System.out.println("has downloaded: " + stock);
                continue;
            }
            driver.get("https://www.barchart.com/my/price-history/download/" + stock);
            driver.findElement(By.xpath("//select[@data-ng-model='frequency']")).click();
            driver.findElement(By.xpath("//option[@value='string:" + frequency + "']")).click();
            driver.findElement(By.xpath("//a[@data-historical='historical']")).click();

            if (count == 0 || !downloadStockData(downloadDir, stock)) {
                return;
            }
            count--;
        }
    }

    private static boolean downloadStockData(File downloadDir, String stock) throws InterruptedException {
        int retryTimes = 1;
        while (true) {
            String[] stockFile = downloadDir.list((dir, name) -> name.startsWith(stock.toLowerCase() + "_"));
            if (ArrayUtils.isNotEmpty(stockFile)) {
                System.out.println("finish downloading " + stock);
                break;
            }
            // 点击下载后，需要等一会儿确定文件已下载再跳转到下一个代码
            System.out.println("downloading " + stock + " " + retryTimes);
            TimeUnit.SECONDS.sleep(1);

            // 最多重试20次，超过则表示已达每日下载次数上限
            if (retryTimes > 20) {
                return false;
            }
            retryTimes++;
        }
        return true;
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
