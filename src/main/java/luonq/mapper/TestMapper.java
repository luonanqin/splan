package luonq.mapper;

import bean.Total;

public interface TestMapper {

    void insertTest();

    void insertTest2(Total total);

    String showTables(String date);

    void createTable(String date);
}
