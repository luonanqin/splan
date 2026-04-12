package bean;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 对应表 {@code option_trade_record}：期权单笔交易记录（正股、行权价、call/put、到期日、买卖日、价格、数量）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OptionTradeRecord {

    public static final String RIGHT_CALL = "call";
    public static final String RIGHT_PUT = "put";

    private Long id;
    /** 正股代码 */
    private String underlyingCode;
    /** 行权价，与库表一致最多两位小数 */
    private BigDecimal strikePrice;
    /** {@code call} 或 {@code put}，见 {@link #RIGHT_CALL} / {@link #RIGHT_PUT} */
    private String optionRight;
    /** 到期日 yyyy-MM-dd，与库 {@code DATE} 一致，不用 {@code LocalDate} 避免 JDBC 时区换算 */
    private String expirationDate;
    /** 买入日 yyyy-MM-dd */
    private String buyDate;
    /** 卖出日 yyyy-MM-dd，未平仓为 null */
    private String sellDate;
    /** 买入价（单价），与库表一致最多两位小数 */
    private BigDecimal buyPrice;
    /** 卖出价（单价），未平仓为 null；与库表一致最多两位小数 */
    private BigDecimal sellPrice;
    /** 数量（张/合约数） */
    private Integer quantity;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
