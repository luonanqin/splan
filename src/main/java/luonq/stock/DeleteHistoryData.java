package luonq.stock;

import bean.BOLL;
import util.BaseUtils;
import util.Constants;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class DeleteHistoryData {

    public static void main(String[] args) throws Exception {
        deleteOpenBoll();
        deleteMergeOpenBoll();
    }

    public static void deleteOpenBoll() throws Exception {
        String openBollDirPath = Constants.HIS_BASE_PATH + "2024/openBOLL";

        Map<String, String> openBollFileMap = BaseUtils.getFileMap(openBollDirPath);
        for (String stock : openBollFileMap.keySet()) {
            if (!stock.equals("BILI")) {
//                continue;
            }
            String openBollFile = openBollFileMap.get(stock);
            List<BOLL> bolls = BaseUtils.readBollFile(openBollFile, 2024);
            List<BOLL> afterDeleteOpenBolls = bolls.stream().filter(b -> BaseUtils.dateToInt(b.getDate()) < 20240119).collect(Collectors.toList());
            //            System.out.println(afterDeleteOpenBolls);
            BaseUtils.writeFile(openBollFile, afterDeleteOpenBolls.stream().map(BOLL::toString).collect(Collectors.toList()));
            System.out.println("deleteOpenBoll " + stock + " finish");
        }
    }

    public static void deleteMergeOpenBoll() throws Exception {
        String mergeOpenBollDirPath = Constants.HIS_BASE_PATH + "bollWithOpen";

        Map<String, String> mergeOpenBollFileMap = BaseUtils.getFileMap(mergeOpenBollDirPath);
        for (String stock : mergeOpenBollFileMap.keySet()) {
            if (!stock.equals("WRK")) {
//                continue;
            }
            String file = mergeOpenBollFileMap.get(stock);
            List<BOLL> bolls = BaseUtils.readBollFile(file, 2024);
            List<BOLL> afterDelete = bolls.stream().filter(b -> BaseUtils.dateToInt(b.getDate()) < 20240119).collect(Collectors.toList());
//                        System.out.println(afterDelete);
            BaseUtils.writeFile(file, afterDelete.stream().map(BOLL::toString).collect(Collectors.toList()));
            System.out.println("deleteMergeOpenBoll " + stock + " finish");
        }
    }
}
