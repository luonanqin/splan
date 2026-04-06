package luonq.service;

import bean.OptionTradeRecord;
import luonq.api.dto.OptionTradeRecordDto;
import luonq.api.dto.OptionTradeRecordWriteRequest;
import luonq.mapper.OptionTradeRecordMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;
import util.Constants;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class OptionTradeRecordService {

    private final OptionTradeRecordMapper optionTradeRecordMapper;

    public OptionTradeRecordService(OptionTradeRecordMapper optionTradeRecordMapper) {
        this.optionTradeRecordMapper = optionTradeRecordMapper;
    }

    public static String normalizeSymbol(String symbol) {
        if (symbol == null) {
            return "";
        }
        return symbol.trim().toUpperCase();
    }

    public List<OptionTradeRecordDto> listByUnderlying(String symbol) {
        String code = normalizeSymbol(symbol);
        if (code.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "symbol 不能为空");
        }
        return optionTradeRecordMapper.listByUnderlyingCode(code).stream()
                .map(OptionTradeRecordService::toDto)
                .collect(Collectors.toList());
    }

    public OptionTradeRecordDto create(String symbol, OptionTradeRecordWriteRequest req) {
        String code = normalizeSymbol(symbol);
        if (code.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "symbol 不能为空");
        }
        OptionTradeRecord row = mapRequest(code, req);
        optionTradeRecordMapper.insert(row);
        OptionTradeRecord loaded = optionTradeRecordMapper.selectById(row.getId());
        if (loaded == null) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "插入后无法读取记录");
        }
        return toDto(loaded);
    }

    public OptionTradeRecordDto update(String symbol, long id, OptionTradeRecordWriteRequest req) {
        String code = normalizeSymbol(symbol);
        if (code.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "symbol 不能为空");
        }
        OptionTradeRecord existing = optionTradeRecordMapper.selectById(id);
        if (existing == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "记录不存在: id=" + id);
        }
        if (!existing.getUnderlyingCode().equalsIgnoreCase(code)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "记录不属于该标的");
        }
        OptionTradeRecord row = mapRequest(code, req);
        row.setId(id);
        optionTradeRecordMapper.updateById(row);
        OptionTradeRecord loaded = optionTradeRecordMapper.selectById(id);
        return toDto(loaded);
    }

    public void delete(String symbol, long id) {
        String code = normalizeSymbol(symbol);
        if (code.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "symbol 不能为空");
        }
        OptionTradeRecord existing = optionTradeRecordMapper.selectById(id);
        if (existing == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "记录不存在: id=" + id);
        }
        if (!existing.getUnderlyingCode().equalsIgnoreCase(code)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "记录不属于该标的");
        }
        optionTradeRecordMapper.deleteById(id);
    }

    private static OptionTradeRecord mapRequest(String underlyingCode, OptionTradeRecordWriteRequest req) {
        if (req == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请求体不能为空");
        }
        if (req.getStrikePrice() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "strikePrice 必填");
        }
        if (req.getBuyPrice() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "buyPrice 必填");
        }
        if (req.getQuantity() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "quantity 必填");
        }
        if (req.getQuantity() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "quantity 须为正整数");
        }
        String right = req.getOptionRight() == null ? "" : req.getOptionRight().trim().toLowerCase();
        if (!OptionTradeRecord.RIGHT_CALL.equals(right) && !OptionTradeRecord.RIGHT_PUT.equals(right)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "optionRight 须为 call 或 put");
        }
        if (!StringUtils.hasText(req.getExpirationDate()) || !StringUtils.hasText(req.getBuyDate())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "expirationDate、buyDate 必填");
        }
        LocalDate expiration = parseDate(req.getExpirationDate(), "expirationDate");
        LocalDate buy = parseDate(req.getBuyDate(), "buyDate");
        LocalDate sell = parseOptionalDate(req.getSellDate(), "sellDate");

        BigDecimal strike = req.getStrikePrice().setScale(2, RoundingMode.HALF_UP);
        BigDecimal buyPx = req.getBuyPrice().setScale(2, RoundingMode.HALF_UP);
        BigDecimal sellPx = null;
        if (req.getSellPrice() != null) {
            sellPx = req.getSellPrice().setScale(2, RoundingMode.HALF_UP);
        }

        return OptionTradeRecord.builder()
                .underlyingCode(underlyingCode)
                .strikePrice(strike)
                .optionRight(right)
                .expirationDate(expiration)
                .buyDate(buy)
                .sellDate(sell)
                .buyPrice(buyPx)
                .sellPrice(sellPx)
                .quantity(req.getQuantity())
                .build();
    }

    private static LocalDate parseDate(String raw, String field) {
        try {
            return LocalDate.parse(raw.trim(), Constants.DB_DATE_FORMATTER);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, field + " 日期格式须为 yyyy-MM-dd");
        }
    }

    private static LocalDate parseOptionalDate(String raw, String field) {
        if (raw == null || !StringUtils.hasText(raw.trim())) {
            return null;
        }
        return parseDate(raw, field);
    }

    private static OptionTradeRecordDto toDto(OptionTradeRecord r) {
        return OptionTradeRecordDto.builder()
                .id(r.getId())
                .underlyingCode(r.getUnderlyingCode())
                .strikePrice(r.getStrikePrice())
                .optionRight(r.getOptionRight())
                .expirationDate(r.getExpirationDate() == null ? null : r.getExpirationDate().format(Constants.DB_DATE_FORMATTER))
                .buyDate(r.getBuyDate() == null ? null : r.getBuyDate().format(Constants.DB_DATE_FORMATTER))
                .sellDate(r.getSellDate() == null ? null : r.getSellDate().format(Constants.DB_DATE_FORMATTER))
                .buyPrice(r.getBuyPrice())
                .sellPrice(r.getSellPrice())
                .quantity(r.getQuantity())
                .build();
    }
}
