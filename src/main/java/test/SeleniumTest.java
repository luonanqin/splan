package test;

import bean.StockKLine;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import lombok.Data;
import lombok.ToString;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.openqa.selenium.By;
import org.openqa.selenium.Dimension;
import org.openqa.selenium.Keys;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.interactions.Actions;
import util.BaseUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static util.Constants.FORMATTER;

/**
 * Created by Luonanqin on 2023/2/5.
 */
public class SeleniumTest {

    public static void main(String[] args) throws Exception {
        String market = "XNYS";
        System.getProperties().setProperty("webdriver.chrome.driver", "chromedriver");
        ChromeOptions chromeOptions = new ChromeOptions();
        ChromeDriver driver = new ChromeDriver(chromeOptions);
        driver.manage().window().setSize(new Dimension(1280, 1027));

        BaseUtils.loginBarchart(driver);
        //        setDateRange(driver, null, null);
        //        getDataFromCanvas(driver);
        //        groupOpenDataByYear();
        // 从昨天开始每365天查询一次并抓取
        // 默认step=3，每次从最左边开始抓取时，如果获取不到field-value，则表示改股票所有数据均已抓去完成，开始下一个股票
        List<String> stockList = BaseUtils.getStockListOrderByOpenDesc(market);
        stockList.remove("STR");
        stockList.clear();
        stockList.add("AAPL");

        for (String stock : stockList) {
            WebElement canvas = loadStockCanvas(driver, stock);

            LocalDate endParse = LocalDate.now().minusDays(1);
            while (true) {
                LocalDate beginParse = endParse.minusDays(365);

                String beginDate = beginParse.format(FORMATTER);
                String endDate = endParse.format(FORMATTER);

                setDateRange(driver, beginDate, endDate);
                List<StockKLine> dataList = getDataFromCanvas(driver, canvas);

                // write data

                if (CollectionUtils.isEmpty(dataList)) {
                    System.out.println("finish " + stock);
                    break;
                } else {
                    endParse = beginParse;
                }
            }
        }
    }


    public static WebElement loadStockCanvas(ChromeDriver driver, String stock) throws Exception {
        try {
            driver.get("https://www.barchart.com/stocks/quotes/" + stock + "/interactive-chart");
        } catch (Exception e) {
            System.out.println("load timeout");
        }

        while (true) {
            try {
                WebElement element = driver.findElement(By.className("chart-canvas-container"));
                return element;
            } catch (Exception e) {
                System.out.println("canvas not find");
            }
            TimeUnit.SECONDS.sleep(1);
        }
    }

    public static void setDateRange(ChromeDriver driver, String beginDate, String endDate) throws Exception {
        int retryTimes = 10;
        while (retryTimes > 0) {
            try {
                System.out.println("set new date range: " + beginDate + " - " + endDate);
                //        driver.findElement(By.xpath("//span[@data-ng-show='label' and text()='Date']")).click();
                driver.findElement(By.xpath("//div[@data-dropdown-id='bc-interactive-chart-dropdown-period']//i[@class='bc-glyph-chevron-down bc-dropdown-flexible-arrow']")).click();
                //        System.out.println("click period finish");
                driver.findElement(By.xpath("//li[@data-ng-click='changePeriod(item.period)' and contains(text(),'Date Range')]")).click();
                //        System.out.println("click Date Range");
                driver.findElement(By.xpath("//div[@class='show-for-medium-up for-tablet-and-desktop ng-scope']//input[@data-ng-model='selectedAggregation.range.from']")).sendKeys(Keys.chord(Keys.COMMAND, "a"), Keys.DELETE, beginDate);
                //        System.out.println("input begin date finish");
                driver.findElement(By.xpath("//div[@class='show-for-medium-up for-tablet-and-desktop ng-scope']//input[@data-ng-model='selectedAggregation.range.to']")).sendKeys(endDate);
                //        System.out.println("input end date finish");
                new Actions(driver).sendKeys(Keys.ENTER).perform();
                driver.findElement(By.xpath("//button[@data-ng-click='modalConfirm()']")).click();
                //        driver.findElement(By.xpath("//button[text()='Apply']")).click();
                System.out.println("confirm new date range: " + beginDate + " - " + endDate);
                break;
            } catch (Exception e) {
                retryTimes--;
            }
        }
    }

    private static List<StockKLine> getDataFromCanvas(ChromeDriver driver, WebElement canvas) throws InterruptedException {
        Actions actions = new Actions(driver);
        actions.moveToElement(canvas, 0, 0).perform();
        TimeUnit.SECONDS.sleep(2);

        //        MoveInfo moveInfo = getMoveInfo(driver, actions);
        //        int xOffset = moveInfo.getXOffset();
        //        int avgStep = moveInfo.getAvgStep();
        //        getMoveData(driver, actions, xOffset, avgStep);
        return getMoveData(driver, actions, -514, 3);
        //        actions.moveByOffset(1, 0).perform();

        //        for (int i = 0; i < 10; i++) {
        //            actions.moveByOffset(10, 0).perform();
        //            String date = driver.findElement(By.xpath("//span[@class='field-name']")).getText();
        //            System.out.println(date);
        //            //            String text = driver.findElement(By.cssSelector("[style='color: #808080']")).getText();
        //            //            System.out.println(text);
        //            TimeUnit.MILLISECONDS.sleep(500);
        //        }
    }

    private static List<StockKLine> getMoveData(ChromeDriver driver, Actions actions, int xOffset, int step) {
        //        WebElement canvas = driver.findElement(By.className("chart-canvas-container"));
        //        actions.moveToElement(canvas, 0, 0).perform();
        actions.moveByOffset(xOffset, 0).perform();

        long begin = System.currentTimeMillis();
        String lastDate = null;
        int moveAdd = 0, moveMaxTimes = 15, moveTimes = 0, totalWidth = 967;
        List<StockKLine> dataList = Lists.newArrayList();
        while (moveTimes < moveMaxTimes) {
            String date = null;
            try {
                date = driver.findElement(By.xpath("//span[@class='field-name']")).getText();
            } catch (Exception e) {
                if (StringUtils.isBlank(date)) {
                    actions.moveByOffset(step, 0).perform();
                    moveTimes++;
                    System.out.println("move to begin retry " + moveTimes);
                    continue;
                }
            }

            if (lastDate != null && StringUtils.equals(lastDate, date)) {
                //                System.out.println("retry");
                moveAdd += step;
                actions.moveByOffset(step, 0).perform();

                if (lastDate != null && moveAdd > totalWidth) {
                    break;
                }
            } else {
                // 获取O H L C delta volume
                //            List<WebElement> elements = driver.findElements(By.xpath("//span[@style='color: #ef4226']"));
                //            String open = elements.get(0).getText();
                //            String high = elements.get(1).getText();
                //            String low = elements.get(2).getText();
                //            String close = elements.get(3).getText();
                //            String change = elements.get(4).getText();
                //            String ma5 = driver.findElement(By.xpath("//span[@style='color: #808080']")).getText();
                //            String ma10 = driver.findElement(By.xpath("//span[@style='color: #f75f60']")).getText();
                //            String ma20 = driver.findElement(By.xpath("//span[@style='color: #f58620']")).getText();
                //            String ma30 = driver.findElement(By.xpath("//span[@style='color: #ff53d6]")).getText();
                //            String ma60 = driver.findElement(By.xpath("//span[@style='color: #a288d2]")).getText();
                //            String bollUpper = driver.findElement(By.xpath("//span[@style='color: #9075d6']")).getText();
                //            String bollMiddle = driver.findElement(By.xpath("//span[@style='color: #89211e']")).getText();
                //            String bollLower = driver.findElement(By.xpath("//span[@style='color: #00aaab']")).getText();

                List<WebElement> elements = driver.findElements(By.xpath("//span[@class='field-value']"));
                String open = StringUtils.defaultIfBlank(elements.get(0).getText(), "0");
                String high = StringUtils.defaultIfBlank(elements.get(1).getText(), "0");
                String low = StringUtils.defaultIfBlank(elements.get(2).getText(), "0");
                String close = StringUtils.defaultIfBlank(elements.get(3).getText(), "0");
                String change = StringUtils.defaultIfBlank(elements.get(4).getText(), "0");
                String volume = StringUtils.defaultIfBlank(elements.get(5).getText(), "0");
                //                String ma5 = StringUtils.defaultIfBlank(elements.get(5).getText(), "0");
                //                String ma10 = StringUtils.defaultIfBlank(elements.get(6).getText(), "0");
                //                String ma20 = StringUtils.defaultIfBlank(elements.get(7).getText(), "0");
                //                String ma30 = StringUtils.defaultIfBlank(elements.get(8).getText(), "0");
                //                String ma60 = StringUtils.defaultIfBlank(elements.get(9).getText(), "0");
                //                String bollUpper = StringUtils.defaultIfBlank(elements.get(10).getText(), "0");
                //                String bollMiddle = StringUtils.defaultIfBlank(elements.get(11).getText(), "0");
                //                String bollLower = StringUtils.defaultIfBlank(elements.get(12).getText(), "0");
                //                System.out.println("left move date:" + date);
                //                System.out.println(String.format("date:%s open:%s close:%s high:%s low:%s change:%s volume:%s ma5:%s ma10:%S ma20:%s ma30:%s ma60:%S bollUpper:%s bollMiddle:%s bollLower:%s", date, open, close, high, low, change, volume, ma5, ma10, ma20, ma30, ma60, bollUpper, bollMiddle, bollLower));
                StockKLine kLine = StockKLine.builder()
                  .date(date)
                  .open(Double.valueOf(open))
                  .high(Double.valueOf(high))
                  .low(Double.valueOf(low))
                  .close(Double.valueOf(close))
                  .change(Double.valueOf(change))
                  .volume(BigDecimal.valueOf(Double.valueOf(volume.replace(",", ""))))
                  .build();
                dataList.add(kLine);
                System.out.println(kLine);

                lastDate = date;
            }
        }
        System.out.println("cost " + ((System.currentTimeMillis() - begin) / 1000) + "s");
        return dataList;
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

            //            if (lastDate != null && lastDate.equals(date) && moveAdd < -15) {
            if (lastDate != null && lastDate.equals(date) && StringUtils.isBlank(date)) {
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
