package luonq.polygon;

import bean.SplitInfo;
import bean.SplitInfoResp;
import com.alibaba.fastjson.JSON;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.methods.GetMethod;
import util.BaseUtils;
import util.Constants;

import java.io.InputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Created by Luonanqin on 2023/5/5.
 */
public class GetSplitInfo {

    public static void main(String[] args) throws Exception {
        String apiKey = "apiKey=Ea9FNNIdlWnVnGcoTpZsOWuCWEB3JAqY";
        String api = "https://api.polygon.io/v3/reference/splits?";
        String dateGte = "2023-01-01";

        LocalDate localDate = LocalDate.now();
        String dateLte = localDate.plusDays(7).format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));

        String filePath = Constants.SPLIT_PATH + "splitInfo";
        List<String> splitInfoLines = BaseUtils.readFile(filePath);
        if (CollectionUtils.isNotEmpty(splitInfoLines)) {
            String lastLine = splitInfoLines.get(splitInfoLines.size() - 1);
            String[] split = lastLine.split(",");
            dateGte = split[0];
        }

        HttpClient httpclient = new HttpClient();
        String url = api + "execution_date.gt=" + dateGte + "&execution_date.lte=" + dateLte + "&limit=1000&order=asc&" + apiKey;
        GetMethod get = new GetMethod(url);

        try {
            int code = httpclient.executeMethod(get);
            if (code != 200) {
                String error = get.getResponseBodyAsString();
                System.out.println(" error=" + error);
                return;
            }
            InputStream stream = get.getResponseBodyAsStream();
            SplitInfoResp result = JSON.parseObject(stream, SplitInfoResp.class);
            List<SplitInfo> results = result.getResults();
            List<String> lineList = results.stream().map(SplitInfo::toString).collect(Collectors.toList());
            BaseUtils.appendIfFile(filePath, lineList);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            get.releaseConnection();
        }
    }
}
