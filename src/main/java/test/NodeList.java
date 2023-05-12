package test;

import com.google.common.collect.Lists;
import org.apache.commons.lang3.StringUtils;

import java.util.List;

public class NodeList {

    private Node head = null;
    private Node last = null;
    private int capacity = 0;
    private int count = 0;

    public NodeList(int capacity) {
        this.capacity = capacity;
    }

    public boolean add(double d) {
        Node node = new Node(d);
        if (head == null) {
            last = node;
            head = node;
            count = 1;
            return true;
        } else {
            if (last.getValue() > node.getValue()) {
                return false;
            }

            Node temp = last.getPrev();
            while (true) {
                if (temp == null) {
                    node.setNext(head);
                    head = node;
                    head.getNext().setPrev(node);
//                    Node h_next = head.getNext();
//                    if (h_next != null) {
//                        head = node;
//                        node.setNext(h_next);
//                        h_next.setPrev(node);
//                    } else {
//                        node.setNext(last);
//                        head = node;
//                        last.setPrev(node);
//                    }
                    count++;
                    break;
                }

                if (temp.getValue() > node.getValue()) {
                    Node t_next = temp.getNext();
                    temp.setNext(node);
                    node.setPrev(temp);
                    node.setNext(t_next);
                    t_next.setPrev(node);
                    count++;
                    break;
                } else {
                    temp = temp.getPrev();
                }
            }
            if (count > capacity) {
                Node l_prev = last.getPrev();
                last = l_prev;
                l_prev.setNext(null);
            }
            return true;
        }
    }

    public String show() {
        List<Double> list = Lists.newLinkedList();
        Node temp = head;
        while (temp != null) {
            list.add(temp.getValue());
            temp = temp.getNext();
        }
        return StringUtils.join(list, ",");
    }

}
