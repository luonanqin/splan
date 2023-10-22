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

        List<String> dayList = Lists.newArrayList("2023-10-16", "2023-10-17", "2023-10-19", "2023-10-19", "2023-10-20");

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
