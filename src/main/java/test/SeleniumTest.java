package test;

import com.google.common.collect.Maps;
import lombok.Data;
import lombok.ToString;
import org.apache.commons.lang3.StringUtils;
import org.openqa.selenium.By;
import org.openqa.selenium.Keys;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.interactions.Actions;
import util.BaseUtils;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Created by Luonanqin on 2023/2/5.
 */
public class SeleniumTest {

    public static void main(String[] args) throws Exception {
        System.getProperties().setProperty("webdriver.chrome.driver", "chromedriver");
        ChromeOptions chromeOptions = new ChromeOptions();
        ChromeDriver driver = new ChromeDriver(chromeOptions);

        //        getDataFromCanvas(driver);
        //        groupOpenDataByYear();
        setDateRange(driver);
    }

    private static void setDateRange(ChromeDriver driver) throws Exception {
        BaseUtils.loginBarchart(driver);

        try {
            driver.get("https://www.barchart.com/stocks/quotes/AAPL/interactive-chart");
        } catch (Exception e) {
            System.out.println("load timeout");
        }

        while (true) {
            try {
                driver.findElement(By.className("chart-canvas-container"));
                break;
            } catch (Exception e) {
                System.out.println("canvas not find");
            }
            TimeUnit.SECONDS.sleep(1);
        }

        //        driver.findElement(By.xpath("//span[@data-ng-show='label' and text()='Date']")).click();
        driver.findElement(By.xpath("//div[@data-dropdown-id='bc-interactive-chart-dropdown-period']//i[@class='bc-glyph-chevron-down bc-dropdown-flexible-arrow']")).click();
        System.out.println("click period finish");
        driver.findElement(By.xpath("//li[@data-ng-click='changePeriod(item.period)' and contains(text(),'Date Range')]")).click();
        System.out.println("click Date Range");
        driver.findElement(By.xpath("//div[@class='show-for-medium-up for-tablet-and-desktop ng-scope']//input[@data-ng-model='selectedAggregation.range.from']")).sendKeys(Keys.chord(Keys.COMMAND, "a"), Keys.DELETE, "03/01/2019");
        System.out.println("input begin date finish");
        driver.findElement(By.xpath("//div[@class='show-for-medium-up for-tablet-and-desktop ng-scope']//input[@data-ng-model='selectedAggregation.range.to']")).sendKeys("10/01/2019");
        System.out.println("input end date finish");
//        driver.findElement(By.xpath("//button[@data-ng-click='modalConfirm()']")).click();
        driver.findElement(By.xpath("//button[text()='Apply']")).click();
        System.out.println("confirm");


    }

    private static void getDataFromCanvas(ChromeDriver driver) throws InterruptedException {
        BaseUtils.loginBarchart(driver);

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
        actions.moveToElement(canvas, 0, 0).perform();

        //        MoveInfo moveInfo = getMoveInfo(driver, actions);
        //        int xOffset = moveInfo.getXOffset();
        //        int avgStep = moveInfo.getAvgStep();
        //        getMoveData(driver, actions, xOffset, avgStep);
        getMoveData(driver, actions, -474, 3);
        //        actions.moveByOffset(1, 0).perform();

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

    private static void getMoveData(ChromeDriver driver, Actions actions, int xOffset, int step) {
        WebElement canvas = driver.findElement(By.className("chart-canvas-container"));
        actions.moveToElement(canvas, 0, 0).perform();
        actions.moveByOffset(xOffset, 0).perform();

        System.out.println(System.currentTimeMillis());
        String lastDate = null;
        int moveAdd = 0;
        System.out.println(System.currentTimeMillis());
        while (true) {
            String date = driver.findElement(By.xpath("//span[@class='field-name']")).getText();
            // 获取O H L C delta volume
            //            List<WebElement> elements = driver.findElements(By.xpath("//span[@style='color: #ef4226']"));
            //            String open = elements.get(0).getText();
            //            String high = elements.get(1).getText();
            //            String low = elements.get(2).getText();
            //            String close = elements.get(3).getText();
            //            String change = elements.get(4).getText();
            //
            //            // ma5
            //            String MA5 = driver.findElement(By.xpath("//span[@style='color: #808080']")).getText();
            //
            //            //ma10
            //            String MA10 = driver.findElement(By.xpath("//span[@style='color: #f75f60']")).getText();
            //
            //            //ma20
            //            String MA20 = driver.findElement(By.xpath("//span[@style='color: #f58620']")).getText();
            //
            //            //ma30
            //            String MA30 = driver.findElement(By.xpath("//span[@style='color: #ff53d6]")).getText();
            //
            //            //ma60
            //            String MA60 = driver.findElement(By.xpath("//span[@style='color: #a288d2]")).getText();
            //
            //            //boll upper
            //            String bollUpper = driver.findElement(By.xpath("//span[@style='color: #9075d6']")).getText();
            //
            //            //boll middle
            //            String bollMiddle = driver.findElement(By.xpath("//span[@style='color: #89211e']")).getText();
            //
            //            //boll lower
            //            String bollLower = driver.findElement(By.xpath("//span[@style='color: #00aaab']")).getText();

            List<WebElement> elements = driver.findElements(By.xpath("//span[@class='field-value']"));
            String open = elements.get(0).getText();
            String high = elements.get(1).getText();
            String low = elements.get(2).getText();
            String close = elements.get(3).getText();
            String change = elements.get(4).getText();
            String MA5 = elements.get(5).getText();
            String MA10 = elements.get(6).getText();
            String MA20 = elements.get(7).getText();
            String MA30 = elements.get(8).getText();
            String MA60 = elements.get(9).getText();
            String bollUpper = elements.get(10).getText();
            String bollMiddle = elements.get(11).getText();
            String bollLower = elements.get(12).getText();
            String volume = elements.get(13).getText();

            if (!StringUtils.equals(lastDate, date)) {
                //                System.out.println("left move date:" + date);
                System.out.println(String.format("date:%s open:%s close:%s high:%s low:%s change:%s volume:%s MA5:%s MA10:%S MA20:%s MA30:%s MA60:%S bollUpper:%s bollMiddle:%s bollLower:%s", date, open, close, high, low, change, volume, MA5, MA10, MA20, MA30, MA60, bollUpper, bollMiddle, bollLower));
                moveAdd = 0;
            }
            //            System.out.println("retry");

            moveAdd += step;
            actions.moveByOffset(step, 0).perform();

            if (lastDate != null && lastDate.equals(date) && moveAdd > 15) {
                break;
            }

            lastDate = date;
        }
        System.out.println(System.currentTimeMillis());
    }

    private static MoveInfo getMoveInfo(ChromeDriver driver, Actions actions) {
        int x = 0;
        int step = -1;
        String lastDate = null;
        int moveAdd = 0;
        int moveAddSum = 0;
        int moveAddTimes = 0;
        while (true) {
            String date = driver.findElement(By.xpath("//span[@class='field-name']")).getText();
            if (!StringUtils.equals(lastDate, date)) {
                System.out.println("left move date:" + date);
                System.out.println("moveAdd " + moveAdd);
                moveAddSum += moveAdd;
                moveAddTimes += 1;
                moveAdd = 0;
            }
            System.out.println("retry");

            x = x + step;
            moveAdd += step;
            actions.moveByOffset(step, 0).perform();

            if (lastDate != null && lastDate.equals(date) && moveAdd < -15) {
                break;
            }

            lastDate = date;
        }
        int avgStep = moveAddSum / moveAddTimes;

        MoveInfo moveInfo = new MoveInfo();
        moveInfo.setAvgStep(-avgStep);
        moveInfo.setXOffset(moveAddSum + 2);

        System.out.println(moveInfo);
        return moveInfo;
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

    @Data
    @ToString
    static class MoveInfo {
        private int avgStep;
        private int xOffset;
    }
}
