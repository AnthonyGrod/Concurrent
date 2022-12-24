import java.util.LinkedList;
import java.util.concurrent.*;

public class BlockingQueue<T> {

    private LinkedList<T> queue =  new LinkedList<>();
    private int capacity;

    public BlockingQueue(int capacity) {
        this.capacity = capacity;
    }

    public synchronized T take() throws InterruptedException {
        while (queue.size() == 0) {
            wait();
        }
        T result = this.queue.removeFirst();
        notifyAll();
        return result;
    }

    public synchronized void put(T item) throws InterruptedException {
        while (queue.size() == this.capacity) {
            wait();
        }
        this.queue.addLast(item);
        notifyAll();
    }

    public synchronized int getSize() {
        return queue.size();
    }

    public int getCapacity() {
        return this.capacity;
    }
}