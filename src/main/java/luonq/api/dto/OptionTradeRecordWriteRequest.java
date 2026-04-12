package luonq.api.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.Data;

import java.math.BigDecimal;

/** 新增与更新均提交整单；日期为 {@code yyyy-MM-dd} 字符串。卖出日、卖出价可空。更新时 id 以路径为准。 */
@Data
public class OptionTradeRecordWriteRequest {

    @JsonAlias("strike_price")
    private BigDecimal strikePrice;
    /** {@code call} 或 {@code put} */
    @JsonAlias("option_right")
    private String optionRight;
    /** yyyy-MM-dd */
    @JsonAlias("expiration_date")
    private String expirationDate;
    /** yyyy-MM-dd */
    @JsonAlias("buy_date")
    private String buyDate;
    /** yyyy-MM-dd；可空：未平仓 */
    @JsonAlias("sell_date")
    private String sellDate;
    @JsonAlias("buy_price")
    private BigDecimal buyPrice;
    @JsonAlias("sell_price")
    private BigDecimal sellPrice;
    private Integer quantity;
}
