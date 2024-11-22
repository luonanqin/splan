package luonq.test;

import com.tigerbrokers.stock.openapi.client.struct.OptionFundamentals;
import com.tigerbrokers.stock.openapi.client.struct.enums.Right;
import com.tigerbrokers.stock.openapi.client.util.OptionCalcUtils;
import org.jquantlib.Settings;
import org.jquantlib.time.Date;

import java.time.LocalDate;

public class CalculateIVTest {

    public static void main(String[] args) {
        Settings settings = new Settings();
        settings.setEvaluationDate(new Date(1, 1, 2022));
        OptionFundamentals optionIndex = OptionCalcUtils.calcOptionIndex(
          Right.CALL,
          250, //对应标的资产的价格
          275,  //期权行权价格
          0.0526,  //无风险利率，这里是取的美国国债利率
          0,  //股息率，大部分标的为0
          1.019, // 隐含波动率
          LocalDate.of(2024, 10, 22), //对应预测价格的日期，要小于期权到期日
          LocalDate.of(2024, 10, 25));  //期权到期日
        System.out.println("value: " + optionIndex.getPredictedValue()); //计算出的期权预测价格
    }
}
