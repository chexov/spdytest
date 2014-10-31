package com.vg.live.spdy;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;

public class AsyncDispatcher {
    private int maxExecuting = 1;

    private ExecutorService executorService;
    CountDownLatch latch = new CountDownLatch(1);

    /**
     * Ready calls in the order they'll be run.
     */
    private final ConcurrentLinkedDeque<AsyncUploadCall> readyCalls = new ConcurrentLinkedDeque<>();

    /**
     * Running calls. Includes canceled calls that haven't finished yet.
     */
    private final ConcurrentLinkedDeque<AsyncUploadCall> runningCalls = new ConcurrentLinkedDeque<>();

    public AsyncDispatcher(ExecutorService executorService) {
        this.executorService = executorService;
    }

    public synchronized ExecutorService getExecutorService() {
        return executorService;
    }

    /**
     * Set the maximum number of requests to execute concurrently. Above this
     * requests queue in memory, waiting for the running calls to complete.
     * <p/>
     * <p/>
     * If more than {@code maxRequests} requests are in flight when this is
     * invoked, those requests will remain in flight.
     */
    public synchronized void setMaxRequests(int maxExecuting) {
        if (maxExecuting < 1) {
            throw new IllegalArgumentException("max < 1: " + maxExecuting);
        }
        this.maxExecuting = maxExecuting;
        promoteCalls();
    }

    public synchronized void enqueue(AsyncUploadCall call) {
        if (runningCalls.size() < maxExecuting) {
            runningCalls.add(call);
            getExecutorService().execute(call);
        } else {
            readyCalls.add(call);
        }
    }

    synchronized void enqueueFirst(AsyncUploadCall call) {
        if (runningCalls.size() < maxExecuting) {
            runningCalls.addFirst(call);
            getExecutorService().execute(call);
        } else {
            readyCalls.addFirst(call);
        }
    }

    /**
     * Used by {@code AsyncCall#run} to signal completion.
     */
    synchronized void finished(AsyncUploadCall call) {
        if (!runningCalls.remove(call))
            throw new AssertionError("call wasn't running! " + call);
        promoteCalls();
        System.out.println("runningCalls=" + runningCalls.size() + " readyCalls=" + readyCalls.size());
        if (isEmpty()) {
            latch.countDown();
        }
    }

    private void promoteCalls() {
        if (runningCalls.size() >= maxExecuting)
            return; // Already running max capacity.
        if (readyCalls.isEmpty())
            return; // No ready calls to promote.

        for (Iterator<AsyncUploadCall> i = readyCalls.iterator(); i.hasNext();) {
            AsyncUploadCall call = i.next();

            i.remove();
            runningCalls.add(call);
            getExecutorService().execute(call);

            if (runningCalls.size() >= maxExecuting)
                return; // Reached max capacity.
        }
    }

    synchronized void failed(AsyncUploadCall call) {
        finished(call);
        enqueueFirst(call);
    }

    public void await() throws InterruptedException {
        latch.await();
    }

    public boolean isEmpty() {
        return runningCalls.isEmpty() && readyCalls.isEmpty();
    }

}
