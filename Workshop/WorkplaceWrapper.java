package cp2022.solution;

import cp2022.base.Workplace;
import cp2022.base.WorkplaceId;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Semaphore;

public class WorkplaceWrapper extends Workplace {

    private Semaphore mutex ;

    private Workplace workplace;

    private MyWorkshop myWorkshop;

    private Semaphore sleepSwitch;

    private boolean isOccupied;

    private Semaphore workplaceSemaphore;

    private CountDownLatch l1;

    private CountDownLatch l2;


    public WorkplaceWrapper(Workplace workplace, MyWorkshop myWorkshop) {
        super(workplace.getId());
        this.workplace = workplace;
        this.myWorkshop = myWorkshop;
        this.mutex = new Semaphore(1);
        this.sleepSwitch = new Semaphore(0);
        this.isOccupied = false;
        this.workplaceSemaphore = new Semaphore(1, true);
        this.l1 = null;
        this.l2 = null;
    }

    @Override
    public void use() {

        if (l1 != null) {
            this.l1.countDown();
        }
        if (l2 != null) {
            this.l2.countDown();
            tryAwait(l2);
        }
        WorkplaceId currentWorkplaceId = this.myWorkshop.getThreadWorkplaceIdHashMap().get(Thread.currentThread());
        semaphoreTryAcquire(this.mutex);

        Thread threadUsing = Thread.currentThread();
        this.myWorkshop.getWhoFromWhereToWhere().remove(threadUsing);
        this.myWorkshop.getThreadWorkplaceIdHashMap().remove(threadUsing);
        this.myWorkshop.getThreadWorkplaceIdHashMap().put(threadUsing, currentWorkplaceId);
        this.myWorkshop.getIdAndPostUseThreads().remove(threadUsing);
        this.myWorkshop.getIdAndWaitingFor().get(this.getId()).remove(threadUsing);
        this.isOccupied = true;

        this.mutex.release();

        this.workplace.use();
        this.myWorkshop.getIdAndPostUseThreads().put(currentWorkplaceId, threadUsing);
        this.myWorkshop.getIdAndPostUseThreads().put(this.getId(), threadUsing);
    }

    public void setOccupied(boolean occupied) {
        this.isOccupied = occupied;
    }

    public void semaphoreTryAcquire(Semaphore semaphore) {
        try {
            semaphore.acquire();
        } catch (InterruptedException e) {
            throw new RuntimeException("panic: unexpected thread interruption");
        }
    }

    public void workplaceMutexRelease() {
        this.mutex.release();
    }

    public Semaphore getWorkplaceSemaphore() {
        return this.workplaceSemaphore;
    }

    public Semaphore getSleepSwitch() {
        return this.sleepSwitch;
    }

    public boolean getIsOccupied() {
        return this.isOccupied;
    }

    public CountDownLatch getL1() {
        return l1;
    }

    public CountDownLatch getL2() {
        return l2;
    }

    public void setL1(CountDownLatch latch) {
        this.l1 = latch;
    }

    public void setL2(CountDownLatch latch) {
        this.l2 = latch;
    }

    public void tryAwait(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            throw new RuntimeException("panic: unexpected thread interruption");
        }
    }
}
