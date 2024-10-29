package luonq.stock;

import bean.BOLL;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import util.BaseUtils;
import util.Constants;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Created by Luonanqin on 2023/2/3.
 * 读取前一年和当年的k线计算当年布林线，新年前19个交易日需要前一年的数据一起计算布林线
 * 计算好的布林线要merge到mergeBoll
 */
@Slf4j
public class MergeBollingerForYear {

    public static void calculate() throws Exception {
        LocalDate today = LocalDate.now();
        int curYear = today.getYear();
        LocalDate firstWorkDay = BaseUtils.getFirstWorkDay();
        int year = today.getYear(), beforeYear = year;
        int bollYear;
        String bollPath;
        if (today.isAfter(firstWorkDay)) {
            bollYear = year;
        } else {
            bollYear = year - 1;
        }
        bollPath = Constants.HIS_BASE_PATH + bollYear + "/BOLL/";

        String mergePath = Constants.HIS_BASE_PATH + "mergeBoll/";
        Map<String, String> bollMap = BaseUtils.getFileMap(bollPath);
        for (String stock : bollMap.keySet()) {
            if (!stock.equals("AAPL")) {
//                continue;
            }

            List<BOLL> bolls = BaseUtils.readBollFile(bollMap.get(stock), bollYear);
            List<BOLL> curBolls = BaseUtils.readBollFile(mergePath + stock, curYear);
            if (CollectionUtils.isNotEmpty(curBolls)) {
                List<String> curBollLines = curBolls.stream().map(BOLL::toString).collect(Collectors.toList());
                int originSize = curBollLines.size();
                BOLL boll = curBolls.get(0);
                String date = boll.getDate();

                int index = 0;
                for (; index < bolls.size(); index++) {
                    if (bolls.get(index).getDate().equals(date)) {
                        break;
                    }
                }
                if (index != 0) {
                    bolls = bolls.subList(0, index);
                    curBollLines.addAll(0, bolls.stream().map(BOLL::toString).collect(Collectors.toList()));
                }

                for (int i = bollYear + 1; i <= curYear; i++) {
                    List<String> bollLines = BaseUtils.readFile(Constants.HIS_BASE_PATH + i + "/BOLL/" + stock);
                    curBollLines.addAll(0, bollLines);
                }
                if (curBollLines.size() > originSize) {
                    BaseUtils.writeFile(Constants.HIS_BASE_PATH + "mergeBoll/" + stock, curBollLines);
                    //                log.info("finish " + stock);
                }
            } else {
                List<String> list = bolls.stream().map(BOLL::toString).collect(Collectors.toList());
                BaseUtils.writeFile(Constants.HIS_BASE_PATH + "mergeBoll/" + stock, list);
            }
        }
    }

    public static void main(String[] args) throws Exception {
        calculate();
    }
}
