package test;

import barchart.StockHistory;
import com.google.common.collect.Maps;
import org.apache.commons.lang3.StringUtils;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.interactions.Actions;
import util.BaseUtils;

import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Created by Luonanqin on 2023/2/5.
 */
public class SeleniumTest {

    public static void main(String[] args) throws Exception {
        getDataFromCanvas();
        //        groupOpenDataByYear();
    }

    private static void getDataFromCanvas() throws InterruptedException {
        System.getProperties().setProperty("webdriver.chrome.driver", "chromedriver");
        ChromeOptions chromeOptions = new ChromeOptions();
//        chromeOptions.setPageLoadTimeout(Duration.ofSeconds(20));
        ChromeDriver driver = new ChromeDriver(chromeOptions);
        //        driver.manage().window().maximize();

        // login
        StockHistory.loginBarchart(driver);

        try {
            driver.get("https://www.barchart.com/stocks/quotes/AAPL/interactive-chart");
        } catch (Exception e) {
            System.out.println("load timeout");
        }

        WebElement canvas;
        while (true) {
            try {
                canvas = driver.findElement(By.className("chart-canvas-container"));
                break;
            } catch (Exception e) {
                System.out.println("canvas not find");
            }
            TimeUnit.SECONDS.sleep(1);
        }

        //        WebElement canvas = driver.findElement(By.className("chart-canvas-container"));
        Actions actions = new Actions(driver);
        int xOffset = -475;
        actions.moveToElement(canvas, 0, 0).perform();
        actions.moveByOffset(xOffset, 0).perform();
        String date1 = driver.findElement(By.xpath("//span[@class='field-name']")).getText();
        System.out.println("first date:" + date1);
        //        actions.moveByOffset(-xOffset, 0).perform();
        //        actions.moveByOffset(xOffset, 0).perform();
        //        while (true) {
        //            try {
        //                String date = driver.findElement(By.xpath("//span[@class='field-name']")).getText();
        //                System.out.println("first date:" + date);
        //                break;
        //            } catch (Exception e) {
        //                System.out.println("right move");
        //            }
        //            actions.moveByOffset(1, 0).perform();
        //            System.out.println(xOffset + 1);
        //        }
        int x = 0;
        int step = 3;
        String lastDate = null;
        int moveAdd = 0;
        System.out.println(System.currentTimeMillis());
        while (true) {
            String date = driver.findElement(By.xpath("//span[@class='field-name']")).getText();
            if (!StringUtils.equals(lastDate, date)) {
                System.out.println("left move date:" + date);
                System.out.println("moveAdd " + moveAdd);
                moveAdd = 0;
            }
            System.out.println("retry");

            x = x + step;
            moveAdd += step;
            actions.moveByOffset(step, 0).perform();

            if (lastDate != null && lastDate.equals(date) && moveAdd > 10) {
                break;
            }

            lastDate = date;
        }
        System.out.println("xxxxxx=" + x);
        System.out.println(System.currentTimeMillis());

        //        actions.moveByOffset(1, 0).perform();

        //        String lastDate2 = null;
        //        int x2 = 0;
        //        while (true) {
        //            try {
        //                String date = driver.findElement(By.xpath("//span[@class='field-name']")).getText();
        //                System.out.println("right move date:" + date);
        //                actions.moveByOffset(1, 0).perform();
        //                if (StringUtils.isNotBlank(lastDate2) && lastDate.equals(date)) {
        //                    break;
        //                }
        //                lastDate2 = date;
        //                x2 = x2 + 1;
        //            } catch (Exception e) {
        //                break;
        //            }
        //        }
        //        System.out.println("x2222222=" + x2);
        //        for (int i = 0; i < 10; i++) {
        //            actions.moveByOffset(10, 0).perform();
        //            String date = driver.findElement(By.xpath("//span[@class='field-name']")).getText();
        //            System.out.println(date);
        //            //            String text = driver.findElement(By.cssSelector("[style='color: #808080']")).getText();
        //            //            System.out.println(text);
        //            TimeUnit.MILLISECONDS.sleep(500);
        //        }

        System.out.println();
    }

    private static void groupOpenDataByYear() throws Exception {
        Map<String, String> xnasOpenMap = BaseUtils.getOpenData("XNAS");

        Map<String/*year*/, Integer/*count*/> countMap = Maps.newHashMap();
        for (String code : xnasOpenMap.keySet()) {
            String open = xnasOpenMap.get(code);
            if (open.length() != 10) {
                continue;
            }
            String year = open.substring(0, 4);
            if (!countMap.containsKey(year)) {
                countMap.put(year, 0);
            }
            int count = countMap.get(year) + 1;
            countMap.put(year, count);
        }

        System.out.println(countMap);
    }
}
