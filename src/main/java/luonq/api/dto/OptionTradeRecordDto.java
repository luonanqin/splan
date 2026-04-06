package luonq.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/** REST 返回：日期为 yyyy-MM-dd 字符串。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OptionTradeRecordDto {

    private Long id;
    private String underlyingCode;
    private BigDecimal strikePrice;
    private String optionRight;
    private String expirationDate;
    private String buyDate;
    private String sellDate;
    private BigDecimal buyPrice;
    private BigDecimal sellPrice;
    private Integer quantity;
}
