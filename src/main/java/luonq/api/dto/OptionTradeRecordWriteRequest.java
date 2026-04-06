package luonq.api.dto;

import lombok.Data;

import java.math.BigDecimal;

/** 新增 / 全量更新期权交易（正股代码由路径提供）。 */
@Data
public class OptionTradeRecordWriteRequest {

    private BigDecimal strikePrice;
    /** {@code call} 或 {@code put} */
    private String optionRight;
    private String expirationDate;
    private String buyDate;
    /** 可空：未平仓 */
    private String sellDate;
    private BigDecimal buyPrice;
    private BigDecimal sellPrice;
    private Integer quantity;
}
