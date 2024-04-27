package luonq.test;

import com.google.common.collect.Lists;
import lombok.Data;
import luonq.polygon.EarningPipeline;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import us.codecraft.webmagic.Page;
import us.codecraft.webmagic.Site;
import us.codecraft.webmagic.Spider;
import us.codecraft.webmagic.processor.PageProcessor;
import us.codecraft.webmagic.selector.Html;
import us.codecraft.webmagic.selector.Selectable;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Component
@Data
public class GrabEarningTest2 implements PageProcessor {

    public String day = "2023-11-17";
    public boolean hasInit = false;
    public int pageIndex = 0;
    public int pageNo = 0;

    @Override
    public void process(Page page) {
        Html html = page.getHtml();

        if (!hasInit) {
            String resultCountText = html.xpath("//span[@class='Mstart(15px) Fw(500) Fz(s)']/span/text()").get();
            int ofIndex = resultCountText.indexOf("of");
            int resultCount = Integer.valueOf(resultCountText.substring(ofIndex + 3, resultCountText.length() - 8));
            pageNo = (resultCount + 99) / 100;
            hasInit = true;
        } else {
            List<String> print = print(html);
            page.putField("", print);
            return;
        }

        for (; pageIndex < pageNo; pageIndex++) {
            if (pageIndex == 0) {
                List<String> print = print(html);
                page.putField("", print);
            } else {
                String nextUrl = "https://finance.yahoo.com/calendar/earnings?day=" + day + "&offset=" + (100 * pageIndex) + "&size=100";
                page.addTargetRequest(nextUrl);
            }
        }
    }

    private static List<String> print(Html html) {
        List<String> res = Lists.newArrayList();

        List<Selectable> symbolList = html.xpath("//td[@aria-label='Symbol']").nodes();
        List<Selectable> timeList = html.xpath("//td[@aria-label='Earnings Call Time']").nodes();
        for (int i = 0; i < symbolList.size(); i++) {
            String symbol = symbolList.get(i).xpath("/td/a/text()").get();
            String time = timeList.get(i).xpath("/td/text()").get();
            if (StringUtils.isBlank(time)) {
                time = timeList.get(i).xpath("/td/span/text()").get();
            }
            if (StringUtils.isBlank(time)) {
                continue;
            }
            String result = symbol + " " + time;
            res.add(result);
            System.out.println(result);
        }

        return res;
    }

    @Override
    public Site getSite() {
        return Site.me().setSleepTime(100).setRetryTimes(3).setTimeOut(100000000);
    }

    public static void main(String[] args) throws Exception {
//        ((LoggerContext) LoggerFactory.getILoggerFactory()).getLogger("org.apache.http").setLevel(Level.ERROR);
        getData();
    }

    private static void getData() throws Exception {
        LocalDate now = LocalDate.now();
        String day = now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));

        GrabEarningTest2 pageProcessor = new GrabEarningTest2();
        pageProcessor.setDay(day);
        EarningPipeline pipeline = new EarningPipeline();

        Spider.create(pageProcessor)
          .addUrl("https://api.nasdaq.com/api/calendar/earnings?date=2020-02-28")
//          .addPipeline(pipeline)
          .run();
        System.out.println();
//        List<String> res = pipeline.getRes();
//        BaseUtils.writeFile(Constants.HIS_BASE_PATH + "earning/" + day, res);
    }
}
