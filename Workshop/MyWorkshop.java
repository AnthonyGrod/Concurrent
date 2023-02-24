
package cp2022.solution;

import cp2022.base.Workplace;
import cp2022.base.WorkplaceId;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;


public class MyWorkshop implements cp2022.base.Workshop {

    private HashMap<Thread, WorkplaceId> threadWorkplaceIdHashMap;

    private HashMap<WorkplaceId, WorkplaceWrapper> idAndWrapper;

    private HashMap<WorkplaceId, Workplace> idAndWorkplace;

    private HashMap<Thread, HashMap<WorkplaceId, WorkplaceId>> whoFromWhereToWhere;

    private HashMap<WorkplaceId, Thread> idAndPostUseThreads;

    private HashMap<WorkplaceId, ArrayList<Thread>> idAndWaitingFor;

    private Semaphore twoNSemaphore;

    private Semaphore mutex;

    private AtomicInteger howManyLeft;

    private int N;


    public MyWorkshop(Collection<Workplace> workplaces) {
        this.N = workplaces.size();
        this.twoNSemaphore = new Semaphore(2 * this.N, true);
        this.threadWorkplaceIdHashMap = new HashMap<>();
        this.idAndWrapper = new HashMap<>();
        this.idAndWorkplace = new HashMap<>();
        this.whoFromWhereToWhere = new HashMap<>();
        this.idAndPostUseThreads = new HashMap<>();
        this.idAndWaitingFor = new HashMap<>();
        this.mutex = new Semaphore(1);
        this.howManyLeft = new AtomicInteger(0);
        for (Workplace workplace : workplaces) {
            idAndWrapper.put(workplace.getId(), new WorkplaceWrapper(workplace, this));
            idAndWorkplace.put(workplace.getId(), workplace);
        }
        for (WorkplaceId wid : idAndWrapper.keySet()) {
            idAndWaitingFor.put(wid, new ArrayList<>(0));
        }

    }

    @Override
    public Workplace enter(WorkplaceId wid) {
        semaphoreTryAcquire(twoNSemaphore);

        semaphoreTryAcquire(mutex);
        if (idAndWrapper.get(wid).getIsOccupied() == false || threadWorkplaceIdHashMap.get(Thread.currentThread()) != null) {
            idAndWrapper.get(wid).setOccupied(true);
            if (threadWorkplaceIdHashMap.get(Thread.currentThread()) != null) {
                threadWorkplaceIdHashMap.replace(Thread.currentThread(), wid);

            } else {
                threadWorkplaceIdHashMap.put(Thread.currentThread(), wid);
            }

            mutex.release();
            return idAndWorkplace.get(wid);
        }

        mutex.release();

        idAndWrapper.get(wid).semaphoreTryAcquire(idAndWrapper.get(wid).getWorkplaceSemaphore());

        semaphoreTryAcquire(mutex);

        idAndWrapper.get(wid).setOccupied(true);
        if (threadWorkplaceIdHashMap.get(Thread.currentThread()) != null) {
            threadWorkplaceIdHashMap.replace(Thread.currentThread(), wid);
        } else {
            threadWorkplaceIdHashMap.put(Thread.currentThread(), wid);
        }

        mutex.release();

        return idAndWorkplace.get(wid);
    }

    @Override
    public Workplace switchTo(WorkplaceId wid) {
        Thread switchingWorker = Thread.currentThread();
        semaphoreTryAcquire(mutex);

        idAndWaitingFor.get(wid).add(switchingWorker);
        WorkplaceId currentWorkplace = threadWorkplaceIdHashMap.get(switchingWorker);

        if (idAndWrapper.get(wid).getIsOccupied() == false) {
            if (idAndWaitingFor.get(currentWorkplace) != null && !idAndWaitingFor.get(currentWorkplace).isEmpty()) {
                mutex.release();
                idAndWrapper.get(threadWorkplaceIdHashMap.get(idAndWaitingFor.get(currentWorkplace).get(0))).getSleepSwitch().release();
                semaphoreTryAcquire(mutex);
            } else {
                if (idAndWrapper.get(currentWorkplace).getWorkplaceSemaphore().availablePermits() == 0) {
                    idAndWrapper.get(currentWorkplace).getWorkplaceSemaphore().release();
                    idAndWrapper.get(currentWorkplace).setOccupied(true);
                } else {
                    idAndWrapper.get(currentWorkplace).setOccupied(false);
                }
            }
            idAndWrapper.get(threadWorkplaceIdHashMap.get(switchingWorker)).workplaceMutexRelease();
            mutex.release();
            return idAndWorkplace.get(wid);
        }

        idAndWaitingFor.get(wid).add(switchingWorker);
        HashMap<WorkplaceId, WorkplaceId> temp = new HashMap<>();
        temp.put(currentWorkplace, wid);
        whoFromWhereToWhere.put(switchingWorker, temp);

        int cycleLength = isCycle(currentWorkplace, Thread.currentThread());
        if (cycleLength == 0) {
            CountDownLatch simpleLatch = new CountDownLatch(2);
            idAndWrapper.get(currentWorkplace).setL2(simpleLatch);
            idAndWrapper.get(wid).setL1(simpleLatch);
            idAndWrapper.get(currentWorkplace).getSleepSwitch().release();
            if (idAndWaitingFor.get(currentWorkplace) != null && !idAndWaitingFor.get(currentWorkplace).isEmpty()) {
                idAndWrapper.get(threadWorkplaceIdHashMap.get(idAndWaitingFor.get(currentWorkplace).get(0))).getSleepSwitch().release();
                idAndWrapper.get(currentWorkplace).setOccupied(false);
            } else if (idAndWaitingFor.get(currentWorkplace) != null && idAndWaitingFor.get(currentWorkplace).isEmpty() && idAndWrapper.get(currentWorkplace).getWorkplaceSemaphore().availablePermits() == 0) {
                idAndWrapper.get(currentWorkplace).getWorkplaceSemaphore().release();
                idAndWrapper.get(currentWorkplace).setOccupied(true);
            } else {
                idAndWrapper.get(currentWorkplace).setOccupied(false);
            }
        } else {
            CountDownLatch cycleLatch = new CountDownLatch(cycleLength);
            WorkplaceId tempWid = currentWorkplace;
            Thread tempThread = switchingWorker;
            for (int i = 0; i < cycleLength; i++) {
                idAndWrapper.get(tempWid).setL1(null);
                idAndWrapper.get(tempWid).setL2(cycleLatch);
                idAndWrapper.get(tempWid).getSleepSwitch().release();
            }

        }

        mutex.release();
        return idAndWrapper.get(wid);
    }

    @Override
    public void leave() {
        Thread leavingWorker = Thread.currentThread();
        semaphoreTryAcquire(mutex);

        WorkplaceId currentWorkplaceId = threadWorkplaceIdHashMap.get(leavingWorker);
        HashMap<WorkplaceId, WorkplaceId> temp = new HashMap<WorkplaceId, WorkplaceId>();
        temp.put(currentWorkplaceId, null);
        whoFromWhereToWhere.put(leavingWorker, temp);

        howManyLeft.incrementAndGet();
        if (howManyLeft.get() == 2 * N) {
            howManyLeft.set(0);
            for (int i = 0; i < 2 * N; i++) {
                twoNSemaphore.release();
            }
        }
        if (idAndWaitingFor.get(currentWorkplaceId) != null && !idAndWaitingFor.get(currentWorkplaceId).isEmpty()) {
            if (threadWorkplaceIdHashMap.get(idAndWaitingFor.get(currentWorkplaceId).get(0)) == null) {
            }
            idAndWrapper.get(threadWorkplaceIdHashMap.get(idAndWaitingFor.get(currentWorkplaceId).get(0))).getSleepSwitch().release();
            idAndWrapper.get(currentWorkplaceId).setOccupied(true);
        } else {
            if (idAndWrapper.get(currentWorkplaceId).getWorkplaceSemaphore().availablePermits() == 0) {
                idAndWrapper.get(currentWorkplaceId).getWorkplaceSemaphore().release();
                idAndWrapper.get(currentWorkplaceId).setOccupied(true);
            } else {
                idAndWrapper.get(currentWorkplaceId).setOccupied(false);
            }
        }
        whoFromWhereToWhere.remove(leavingWorker);

        mutex.release();
    }

    public void semaphoreTryAcquire(Semaphore semaphore) {
        try {
            semaphore.acquire();
        } catch (InterruptedException e) {
            throw new RuntimeException("panic: unexpected thread interruption");
        }
    }

    public HashMap<WorkplaceId, Thread> getIdAndPostUseThreads() {
        return idAndPostUseThreads;
    }

    public HashMap<Thread, HashMap<WorkplaceId, WorkplaceId>> getWhoFromWhereToWhere() {
        return whoFromWhereToWhere;
    }

    public int isCycle(WorkplaceId startingWid, Thread startingThread) {
        WorkplaceId tempWid = whoFromWhereToWhere.get(startingThread).get(startingWid);
        Thread tempThread;
        if (tempWid == null) {
            tempThread = idAndPostUseThreads.get(whoFromWhereToWhere.get(startingThread).get(startingWid));
        } else {
            return 0;
        }

        int cycleLength = 1;
        while (tempWid != startingWid) {
            tempWid = this.whoFromWhereToWhere.get(tempThread).get(tempWid);
            tempThread = idAndPostUseThreads.get(tempWid);
            if (tempWid == null) {
                return 0;
            }
            cycleLength++;
        }

        return cycleLength;
    }

    public HashMap<Thread, WorkplaceId> getThreadWorkplaceIdHashMap() {
        return threadWorkplaceIdHashMap;
    }

    public HashMap<WorkplaceId, ArrayList<Thread>> getIdAndWaitingFor() {
        return idAndWaitingFor;
    }
}
