package luonq.polygon;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import com.google.common.collect.Lists;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.LoggerFactory;
import us.codecraft.webmagic.Page;
import us.codecraft.webmagic.Site;
import us.codecraft.webmagic.Spider;
import us.codecraft.webmagic.processor.PageProcessor;
import us.codecraft.webmagic.selector.Html;
import us.codecraft.webmagic.selector.Selectable;
import util.BaseUtils;
import util.Constants;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Data
@Slf4j
public class GrabEarning implements PageProcessor {

    public String day;
    public boolean hasInit = false;
    public int pageIndex = 0;
    public int pageNo = 0;

    @Override
    public void process(Page page) {
        Html html = page.getHtml();

        if (!hasInit) {
            String resultCountText = html.xpath("//*[@id='nimbus-app']/section/section/section/article/main/main/div[1]/div/div/p/text()").get().trim();
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

        List<Selectable> symbolList = html.xpath("//td[@class='tw-text-left yf-2twxe2 lpin  shad']").nodes();
        List<Selectable> timeList = html.xpath("//td[@class='tw-text-left yf-2twxe2']").nodes();
        for (int i = 0; i < symbolList.size(); i++) {
            String symbol = symbolList.get(i).xpath("/td/div/a/text()").get().trim();
            String time = timeList.get(i).xpath("/td/text()").get().trim();
            if (StringUtils.isBlank(time)) {
                time = timeList.get(i).xpath("/td/span/text()").get();
            }
            if (StringUtils.isBlank(time)) {
                continue;
            }
            String result = symbol + " " + time;
            res.add(result);
//            log.info(result);
        }

        return res;
    }

    @Override
    public Site getSite() {
        return Site.me().setSleepTime(100).setRetryTimes(3);
    }

    public static void main(String[] args) throws Exception {
        ((LoggerContext) LoggerFactory.getILoggerFactory()).getLogger("org.apache.http").setLevel(Level.ERROR);
        getData();
    }

    public static void getData() throws Exception {
        LocalDate now = LocalDate.now();
        String day = now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        int dayOfWeek = now.getDayOfWeek().getValue();
        if (!(dayOfWeek >= 1 && dayOfWeek <= 5)) {
            return;
        }

        day = "2024-08-16";
        GrabEarning pageProcessor = new GrabEarning();
        pageProcessor.setDay(day);
        EarningPipeline pipeline = new EarningPipeline();

        Spider.create(pageProcessor)
          .addUrl("https://finance.yahoo.com/calendar/earnings?day=" + day)
          .addPipeline(pipeline)
          .run();
        List<String> res = pipeline.getRes();
        BaseUtils.writeFile(Constants.HIS_BASE_PATH + "earning/" + day, res);
    }
}
