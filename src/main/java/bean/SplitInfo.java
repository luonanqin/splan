package bean;

import lombok.Data;

@Data
public class SplitInfo {

    private String execution_date;
    private int split_from;
    private int split_to;
    private String ticker;

    @Override
    public String toString() {
        return String.format("%s,%s,%d,%d", execution_date, ticker, split_from, split_to);
    }
}
