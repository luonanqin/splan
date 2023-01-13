package barchart;

import com.google.common.collect.Lists;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;

import java.util.List;

public class Login {

    public static void main(String[] args) {
        System.getProperties().setProperty("webdriver.chrome.driver", "chromedriver");
        ChromeOptions chromeOptions = new ChromeOptions();
        ChromeDriver driver = new ChromeDriver(chromeOptions);
        driver.get("https://www.barchart.com//");
        WebElement loginBtn = driver.findElement(By.className("login"));
        loginBtn.click();

        // login
        driver.findElement(By.cssSelector("[name='email']")).sendKeys("qinnanluo@sina.com");
        driver.findElement(By.cssSelector("[name='password']")).sendKeys("luonq134931");
        driver.findElement(By.className("bc-nui-modal-login__button")).click();
        // 登录后需要休眠一会儿确定已经登录

        // download historical stock data
        List<String> stockList = Lists.newArrayList("FUTU", "XPEV");
        for (String stock : stockList) {
            driver.get("https://www.barchart.com/my/price-history/download/" + stock);
            driver.findElement(By.xpath("//a[@data-historical='historical']")).click();
            // 点击下载后，需要等一会儿确定文件已下载再跳转到下一个代码
        }

        driver.quit();
    }

}
