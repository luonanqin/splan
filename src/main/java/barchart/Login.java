package barchart;

import com.google.common.collect.Lists;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.openqa.selenium.By;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class Login {

    public static void main(String[] args) throws Exception {
        String downloadPath = "/Users/luonanqin/Downloads"; // 公司和家里通用

        System.getProperties().setProperty("webdriver.chrome.driver", "chromedriver");
        ChromeOptions chromeOptions = new ChromeOptions();
        ChromeDriver driver = new ChromeDriver(chromeOptions);

        // login
        loginBarchart(driver);

        // has option stock
        List<String> stockList = getStockList();

        File file = new File(downloadPath);
        if (!file.exists()) {
            System.out.println("please check the download path");
            return;
        }
        String[] existList = file.list();
        String searchKey = "_daily_historical-data";
        Set<String> hasDownload = Arrays.stream(existList).filter(s -> s.contains(searchKey)).map(s -> s.substring(0, s.indexOf(searchKey))).collect(Collectors.toSet());

        // download historical stock data
        downloadHistoricalStock(driver, stockList, file, hasDownload);

        driver.quit();
    }

    private static void downloadHistoricalStock(ChromeDriver driver, List<String> stockList, File file, Set<String> hasDownload) throws InterruptedException {
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

    private static List<String> getStockList() throws IOException {
        List<String> stockList = Lists.newArrayList();
        BufferedReader br = new BufferedReader(new InputStreamReader(Login.class.getResourceAsStream("/historicalData/code/hasOption/XNAS")));
        String hasOption;
        while (StringUtils.isNotBlank(hasOption = br.readLine())) {
            stockList.add(hasOption);
        }
        br.close();

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
