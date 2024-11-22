package bean;

import lombok.Data;
import lombok.ToString;

@Data
@ToString
public class OptionGreek {

    private double delta;
    private double gamma;
    private double theta;
    private double vega;
}
