package bean;

import lombok.Data;

@Data
public class Page {

    private int id = 0;
    private Integer pageIndex = 0;
    private Integer pageNum = 1;
    private Integer limit = 5000;
}
