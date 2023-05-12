package test;

import lombok.Data;

@Data
public class Node {

    private double value;
    private Node next = null;
    private Node prev = null;

    public Node(double value) {
        this.value = value;
    }

}
