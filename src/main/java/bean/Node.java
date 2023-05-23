package bean;

import lombok.Data;

@Data
public class Node {

    private String name;
    private double value;
    private double price;
    private Node next = null;
    private Node prev = null;

    public Node(String name, double value) {
        this.name = name;
        this.value = value;
    }
}
