package luonq.stock;

import bean.BOLL;
import lombok.extern.slf4j.Slf4j;
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
public class MergeOpenBollingerForYear {

    public static void calculate() throws Exception {
        LocalDate today = LocalDate.now();
        int curYear = today.getYear();

        String mergePath = Constants.HIS_BASE_PATH + "bollWithOpen/";
        Map<String, String> bollMap = BaseUtils.getFileMap(mergePath);
        for (String stock : bollMap.keySet()) {
            if (!stock.equals("AAPL")) {
                //                continue;
            }

            List<BOLL> curBolls = BaseUtils.readBollFile(mergePath + stock, curYear);
            List<String> curBollLines = curBolls.stream().map(BOLL::toString).collect(Collectors.toList());
            int originSize = curBollLines.size();
            BOLL boll = curBolls.get(0);
            String date = boll.getDate();
            int bollYear = Integer.parseInt(date.substring(6));

            String bollPath = Constants.HIS_BASE_PATH + bollYear + "/openBOLL/";
            List<BOLL> bolls = BaseUtils.readBollFile(bollPath + stock, bollYear);
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
                List<String> bollLines = BaseUtils.readFile(Constants.HIS_BASE_PATH + i + "/openBOLL/" + stock);
                curBollLines.addAll(0, bollLines);
            }
            if (curBollLines.size() > originSize) {
                BaseUtils.writeFile(Constants.HIS_BASE_PATH + "bollWithOpen/" + stock, curBollLines);
                //                log.info("finish " + stock);
            }
        }
    }

    public static void main(String[] args) throws Exception {
        calculate();
    }
}
