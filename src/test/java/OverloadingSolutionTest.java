import org.apache.commons.collections4.queue.CircularFifoQueue;

import java.util.Queue;

public class OverloadingSolutionTest {
    public static void main(String[] args) {
        Queue<Integer> fifo = new CircularFifoQueue<>(4);
        fifo.add(1);
        fifo.add(2);
        fifo.add(3);
        fifo.add(4);
        fifo.add(5);

        System.out.println(fifo.poll());
        System.out.println(fifo);

        fifo.add(6);
        fifo.add(7);
        fifo.add(8);

        System.out.println(fifo.poll());
        System.out.println(fifo);
    }
}
