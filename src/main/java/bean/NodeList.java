package bean;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.util.List;
import java.util.Map;

@Slf4j
public class NodeList {

    private Node head = null;
    private Node last = null;
    private int capacity = 0;
    private int count = 0;
    private Map<String, Node> map = Maps.newHashMap();

    public NodeList(int capacity) {
        this.capacity = capacity;
    }

    public boolean add(String name, double value) {
        Node node = new Node(name, value);
        return add(node);
    }

    public boolean add(Node node) {
        String name = node.getName();
        double value = node.getValue();
        if (head == null) {
            last = node;
            head = node;
            count = 1;
            map.put(name, node);
            return true;
        } else {
            boolean contain = map.containsKey(name);
            if (last.getValue() > value) {
                if (contain) {
                    last.setNext(node);
                    node.setPrev(last);
                    last = node;
                    deleteAndAdd(name, node);
                }
                return false;
            }

            Node temp = last.getPrev();
            while (true) {
                if (temp == null) {
                    if (head.getName().equals(name)) {
                        head.setValue(value);
                        return false;
                    } else {
                        node.setNext(head);
                        head = node;
                        head.getNext().setPrev(node);
                        if (!contain) {
                            map.put(name, node);
                            count++;
                        } else {
                            deleteAndAdd(name, node);
                        }
                    }
                    break;
                }

                if (temp.getValue() > value) {
                    Node t_next = temp.getNext();
                    if (t_next.getName().equals(name)) {
                        t_next.setValue(value);
                        return false;
                    } else {
                        temp.setNext(node);
                        node.setPrev(temp);
                        node.setNext(t_next);
                        t_next.setPrev(node);
                        if (!contain) {
                            map.put(name, node);
                            count++;
                        } else {
                            deleteAndAdd(name, node);
                        }
                    }
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

    public void deleteAndAdd(String stock, Node newNode) {
        if (!map.containsKey(stock)) {
            return;
        }

        Node node = map.get(stock);
        Node prev = node.getPrev();
        Node next = node.getNext();
        if (prev != null) {
            prev.setNext(next);
        } else {
            head = next;
            next.setPrev(null);
        }
        if (next != null) {
            next.setPrev(prev);
        } else {
            last = prev;
            prev.setNext(null);
        }
        map.remove(stock);
        map.put(stock, newNode);
    }

    public String show() {
        List<String> list = Lists.newLinkedList();
        Node temp = head;
        while (temp != null) {
            list.add(temp.getName() + "=" + temp.getValue());
            temp = temp.getNext();
        }
        System.out.println(StringUtils.join(list, ","));
        return StringUtils.join(list, ",");
    }

    public List<Node> getNodes() {
        List<Node> nodes = Lists.newArrayList();
        Node temp = head;
        while (temp != null) {
            nodes.add(temp);
            temp = temp.getNext();
        }
        return nodes;
    }
}
