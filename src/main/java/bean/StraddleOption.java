package bean;

import lombok.Data;

/**
 * 跨式期权, 加内价外期权都适用
 */
@Data
public class StraddleOption {

    private OptionDaily call_1; // call一档
    private OptionDaily call_2; // call二档
    private OptionDaily put_1; // put一档
    private OptionDaily put_2; // put二档

    public String toString(){
        return call_1 + "#" + put_1 + "#" + call_2 + "#" + put_2;
    }
}
