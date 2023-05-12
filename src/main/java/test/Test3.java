package test;

import com.google.common.collect.Lists;
import lombok.Data;
import org.apache.commons.lang3.StringUtils;

import java.util.List;

public class Test3 {

    @Data
    static class Node {
        private double value;
        private Node next = null;
        private Node prev = null;

        public Node(double value) {
            this.value = value;
        }
    }

    @Data
    static class NodeList {

        private Node head;
        private Node last;
        private int capacity;
        private int count = 0;

        public NodeList(int capacity) {
            this.capacity = capacity;
        }

        public boolean add(double d) {
            Node node = new Node(d);
            if (head == null) {
                head = node;
//                last = node;
                count = 1;
                return true;
            } else {
                if (last.getValue() > node.getValue()) {
                    return false;
                }

                Node temp = last.prev;
                while (true) {
                    if (temp == null) {
                        head.prev = node;
                        node.next = head;
                        head = node;
                        count++;
                        break;
                    }

                    if (temp.getValue() > node.getValue()) {
                        Node t_next = temp.next;
                        temp.next = node;
                        node.prev = temp;
                        t_next.prev = node;
                        count++;
                        break;
                    } else {
                        temp = temp.prev;
                    }
                }
                if (count == capacity) {
                    Node l_prev = last.prev;
                    last = l_prev;
                    l_prev.next = null;
                }
                return true;
            }
        }

        public String toString() {
            List<Double> list = Lists.newLinkedList();
            while (head != null) {
                list.add(head.value);
                head = head.next;
            }
            return StringUtils.join(list, ",");
        }
    }

    public static void main(String[] args) {
        NodeList list = new NodeList(10);
        for (int i = 0; i < 10; i++) {
            list.add(i);

            System.out.println(list);
        }
    }
}
