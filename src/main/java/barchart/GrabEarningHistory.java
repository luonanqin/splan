package barchart;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import org.openqa.selenium.By;
import org.openqa.selenium.Dimension;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.slf4j.LoggerFactory;
import util.BaseUtils;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Created by Luonanqin on 2023/2/5.
 */
public class GrabEarningHistory {

    public static void main(String[] args) throws Exception {
        ((LoggerContext) LoggerFactory.getILoggerFactory()).getLogger("org.apache.commons").setLevel(Level.ERROR);
        ((LoggerContext) LoggerFactory.getILoggerFactory()).getLogger("httpclient.wire").setLevel(Level.ERROR);
        System.getProperties().setProperty("webdriver.chrome.driver", "chromedriver");

        ChromeOptions chromeOptions = new ChromeOptions();
        chromeOptions.addArguments("--remote-allow-origins=*");
//        chromeOptions.addArguments("--whitelisted-ips=*");
        ChromeDriver driver = new ChromeDriver(chromeOptions);
        driver.manage().window().setSize(new Dimension(1280, 1027));
        driver.manage().timeouts().pageLoadTimeout(10, TimeUnit.SECONDS);

        By xpath = By.xpath("//td[@aria-label='Earnings Date']");
        BaseUtils.viewloadPage(driver, "https://finance.yahoo.com/calendar/earnings?symbol=LE", xpath);
        List<WebElement> elements = driver.findElements(xpath);
        for (WebElement ele : elements) {
            String span = ele.findElement(By.xpath("span")).getText();
            System.out.println(span);
        }
    }
}
