package stock;

import bean.StockKLine;
import com.google.common.collect.Lists;

import java.io.File;
import java.util.List;

import static util.Constants.BASE_PATH;
import static util.Constants.FIX_WEEKLY_PATH;

/**
 * Created by Luonanqin on 2023/2/13.
 */
public class FixDownloadData {

    public static void main(String[] args) throws Exception {
        // 加载weekly下的文件列表，转换成stock列表大写
        // 加载fixWeekly下的文件列表，转换成stock列表大写
        // 只有weekly中有且fixWeekly没有的数据才需要fix
        // 加载daily数据
        // 加载weekly数据
        // 当daily某天小于weekly的某天时，开始累加周成交量x，
        // 当daily某天小于下一个weekly的某天时，最新一周成交量累加结束，写入fixWeekly，并清零x，接着继续累加新的成交量
        // 结束

    }

    public List<String> fixedWeeklyList() throws Exception {
        List<String> list = Lists.newArrayList();

        File dir = new File(FIX_WEEKLY_PATH);
        for (String file : dir.list()) {
            String stock = file.substring(0, file.indexOf("_weekly"));
            list.add(stock.toUpperCase());
        }

        return list;
    }

    public List<String> downloadDataList(String period) throws Exception {
        List<String> list = Lists.newArrayList();

        File dir = new File(BASE_PATH + period + "/");
        for (String file : dir.list()) {
            String stock = file.substring(0, file.indexOf("_" + period));
            list.add(stock.toUpperCase());
        }

        return list;
    }

    public List<StockKLine> loadOriginalData(String period, String stock){
        
    }
}
