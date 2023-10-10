package barchart;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import com.google.common.collect.Lists;
import org.openqa.selenium.By;
import org.openqa.selenium.Dimension;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.slf4j.LoggerFactory;
import util.BaseUtils;
import util.Constants;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Created by Luonanqin on 2023/2/5.
 */
public class GrabEarningHistory {

    public static void main(String[] args) throws Exception {
        ((LoggerContext) LoggerFactory.getILoggerFactory()).getLogger("org.apache.commons").setLevel(Level.ERROR);
        ((LoggerContext) LoggerFactory.getILoggerFactory()).getLogger("httpclient.wire").setLevel(Level.ERROR);
        ((LoggerContext) LoggerFactory.getILoggerFactory()).getLogger("org.asynchttpclient").setLevel(Level.ERROR);
        System.getProperties().setProperty("webdriver.chrome.driver", "chromedriver");

        ChromeOptions chromeOptions = new ChromeOptions();
        chromeOptions.addArguments("--remote-allow-origins=*");
        //        chromeOptions.addArguments("--whitelisted-ips=*");
        ChromeDriver driver = new ChromeDriver(chromeOptions);
        driver.manage().window().setSize(new Dimension(1280, 1027));
        driver.manage().timeouts().pageLoadTimeout(10, TimeUnit.SECONDS);

        List<String> dayList = Lists.newArrayList("2023-09-01", "2023-08-31", "2023-08-30", "2023-08-29", "2023-08-28", "2023-08-25", "2023-08-24", "2023-08-23", "2023-08-22", "2023-08-21", "2023-08-18", "2023-08-17", "2023-08-16", "2023-08-15", "2023-08-14", "2023-08-11", "2023-08-10", "2023-08-09", "2023-08-08", "2023-08-07", "2023-08-04", "2023-08-03", "2023-08-02", "2023-08-01", "2023-07-31", "2023-07-28", "2023-07-27", "2023-07-26", "2023-07-25", "2023-07-24", "2023-07-21", "2023-07-20", "2023-07-19", "2023-07-18", "2023-07-17", "2023-07-14", "2023-07-13", "2023-07-12", "2023-07-11", "2023-07-10", "2023-07-07", "2023-07-06", "2023-07-05", "2023-07-03", "2023-06-30", "2023-06-29", "2023-06-28", "2023-06-27", "2023-06-26", "2023-06-23", "2023-06-22", "2023-06-21", "2023-06-20", "2023-06-16", "2023-06-15", "2023-06-14", "2023-06-13", "2023-06-12", "2023-06-09", "2023-06-08", "2023-06-07", "2023-06-06", "2023-06-05", "2023-06-02", "2023-06-01", "2023-05-31", "2023-05-30", "2023-05-26", "2023-05-25", "2023-05-24", "2023-05-23", "2023-05-22", "2023-05-19", "2023-05-18", "2023-05-17", "2023-05-16", "2023-05-15", "2023-05-12", "2023-05-11", "2023-05-10", "2023-05-09", "2023-05-08", "2023-05-05", "2023-05-04", "2023-05-03", "2023-05-02", "2023-05-01", "2023-04-28", "2023-04-27", "2023-04-26", "2023-04-25", "2023-04-24", "2023-04-21", "2023-04-20", "2023-04-19", "2023-04-18", "2023-04-17", "2023-04-14", "2023-04-13", "2023-04-12", "2023-04-11", "2023-04-10", "2023-04-06", "2023-04-05", "2023-04-04", "2023-04-03", "2023-03-31", "2023-03-30", "2023-03-29", "2023-03-28", "2023-03-27", "2023-03-24", "2023-03-23", "2023-03-22", "2023-03-21", "2023-03-20", "2023-03-17", "2023-03-16", "2023-03-15", "2023-03-14", "2023-03-13", "2023-03-10", "2023-03-09", "2023-03-08", "2023-03-07", "2023-03-06", "2023-03-03", "2023-03-02", "2023-03-01", "2023-02-28", "2023-02-27", "2023-02-24", "2023-02-23", "2023-02-22", "2023-02-21", "2023-02-17", "2023-02-16", "2023-02-15", "2023-02-14", "2023-02-13", "2023-02-10", "2023-02-09", "2023-02-08", "2023-02-07", "2023-02-06", "2023-02-03", "2023-02-02", "2023-02-01", "2023-01-31", "2023-01-30", "2023-01-27", "2023-01-26", "2023-01-25", "2023-01-24", "2023-01-23", "2023-01-20", "2023-01-19", "2023-01-18", "2023-01-17", "2023-01-13", "2023-01-12", "2023-01-11", "2023-01-10", "2023-01-09", "2023-01-06", "2023-01-05", "2023-01-04", "2023-01-03");

        for (String day : dayList) {
            By tableXpath = By.xpath("//div[@id='cal-res-table']");
            BaseUtils.viewloadPage(driver, "https://finance.yahoo.com/calendar/earnings?day=" + day, tableXpath);
            String resultCountText = driver.findElement(By.xpath("//span[@class='Mstart(15px) Fw(500) Fz(s)']")).getText();
            int ofIndex = resultCountText.indexOf("of");
            int resultCount = Integer.valueOf(resultCountText.substring(ofIndex + 3, resultCountText.length() - 8));

            int pageNo = (resultCount + 99) / 100;
            List<String> resList = Lists.newArrayList();
            for (int i = 0; i < pageNo; i++) {
                if (i == 0) {
                    resList.addAll(print(driver));
                } else {
                    BaseUtils.viewloadPage(driver, "https://finance.yahoo.com/calendar/earnings?day=" + day + "&offset=" + (100 * i) + "&size=100", tableXpath);
                    resList.addAll(print(driver));
                }
            }

            BaseUtils.writeFile(Constants.HIS_BASE_PATH + "earning/" + day, resList);
            System.out.println("finish " + day);
        }
    }

    private static List<String> print(ChromeDriver driver) {
        List<String> res = Lists.newArrayList();

        By tableXpath = By.xpath("//div[@id='cal-res-table']");
        WebElement element = driver.findElement(tableXpath);
        List<WebElement> symbolList = element.findElements(By.xpath("//td[@aria-label='Symbol']"));
        List<WebElement> timeList = element.findElements(By.xpath("//td[@aria-label='Earnings Call Time']"));
        for (int i = 0; i < symbolList.size(); i++) {
            String symbol = symbolList.get(i).getText();
            String time = timeList.get(i).getText();
            res.add(symbol + " " + time);
        }

        return res;
    }
}
