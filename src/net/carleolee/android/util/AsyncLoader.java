package net.carleolee.android.util;

import java.util.HashMap;

import android.os.Handler;
import android.os.Message;

/**
 * Utility to load data asynchronously. It is designed to load icons for
 * ListView at first, but you can use it in other circumstance.
 *
 * @param <K> the type of keys
 * @param <T> the type of parameter
 * @param <E> the type of extra parameter
 * @param <V> the type of object passed to callback
 * @param <R> the type of result
 */
public class AsyncLoader<K, T, E, V, R> {

    public interface LoaderProxy<K, T, E, V, R> {

        /**
         * perform data loading on background threads. Note that this method
         * will be called concurrently by several working thread.
         */
        public R doInBackground(K key, T param, E extra);

        /**
         * run on main thread after {@link #doInBackground}
         */
        public void onLoaded(K key, T param, E extra, V obj, R data);
    }

    class Node {
        Node prev;
        Node next;
        K key;
        T param;
        E extra;
        V obj;
        R data;
    }


    public final static int DEFAULT_WORKERS = 3;

    public final static int DEFAULT_CAPACITY = 20;

    final int mCapacity;
    final int mMaxWorker;
    private int mWorkerNum;

    final Object mLock = new Object();

    private final HashMap<K, Node> mMap;
    private Node mHead;
    private Node mTail;

    private final LoaderProxy<K, T, E, V, R> mProxy;

    private final Handler mHandler;

    private volatile boolean mStoped;
    private volatile boolean mPaused;

    private volatile int mTag;

    public AsyncLoader(LoaderProxy<K, T, E, V, R> proxy) {
        this(DEFAULT_CAPACITY, DEFAULT_WORKERS, proxy);
    }

    public AsyncLoader(int capacity, LoaderProxy<K, T, E, V, R> proxy) {
        this(capacity, DEFAULT_WORKERS, proxy);
    }

    public AsyncLoader(int capacity, int maxWorker, LoaderProxy<K, T, E, V, R> proxy) {
        if (maxWorker < 1)
            throw new IllegalArgumentException("maxWorker must be great than 1");
        if (capacity <= maxWorker)
            capacity = maxWorker + 1;

        mCapacity = capacity;
        mMaxWorker = maxWorker;
        mProxy = proxy;

        mTag = 1;
        mStoped = false;
        mPaused = false;

        mWorkerNum = 0;
        mMap = new HashMap<K, Node>();
        mHead = new Node();
        mTail = new Node();
        mHead.next = mTail;
        mTail.prev = mHead;


        mHandler = new Handler() {
            @Override
            public void handleMessage(Message msg) {
                @SuppressWarnings({"unchecked"})
                Node node = (Node)msg.obj;
                synchronized (mLock) {
                    if (mStoped || msg.arg1 != mTag)
                        return;
                    mMap.remove(node.key);
                }
                mProxy.onLoaded(node.key, node.param, node.extra, node.obj, node.data);
            }
        };
    }

    private void detach(Node node) {
        node.prev.next = node.next;
        node.next.prev = node.prev;
    }

    private void attach(Node head, Node node) {
        node.next = head.next;
        node.prev = head;
        node.next.prev = node;
        head.next = node;
    }

    /**
     * add new task to the queue.
     * if task for key already exist, this new 'obj' will bind to that task.
     */
    public void loadData(K key, T param, E extra, V obj) {
        synchronized (mLock) {
            if (mStoped) {
                throw new IllegalStateException("This loader is stoped already");
            } else {
                Node node = mMap.get(key);
                if (node != null) {
                    node.obj = obj;
                    // in queue, move to head
                    if (node.next != null && node.prev != null) {
                        if (node.prev != mHead) {
                            detach(node);
                            attach(mHead, node);
                        }
                    }
                } else {
                    node = new Node();
                    node.key = key;
                    node.param = param;
                    node.obj = obj;
                    node.extra = extra;
                    attach(mHead, node);
                    if (mMap.size() > mCapacity) {
                        mMap.remove(mTail.prev.key);
                        detach(mTail.prev);
                    }
                }

                if (mWorkerNum < mMaxWorker) {
                    mWorkerNum++;
                    Worker w = new Worker(mWorkerNum);
                    w.start();
                } else {
                    mLock.notify();
                }
            }
        }
    }

    /**
     * discard all task (include queued and processing)
     */
    public void invalidate() {
        synchronized (mLock) {
            if (mStoped)
                return;
            else {
                mTag++;
                mMap.clear();
                mHead.next = mTail;
                mTail.prev = mHead;
            }
        }
    }

    /**
     * discard all task and stop all worker threads. You can not call
     * {@link loadData} once this method is called.
     */
    public void stop() {
        synchronized (mLock) {
            if (mStoped) {
                return;
            } else {
                mStoped = true;
                mMap.clear();
                mHead.next = mTail;
                mTail.prev = mHead;
                mLock.notifyAll();
            }
        }
    }

    /**
     * pause loader.
     */
    public void pause() {
        synchronized (mLock) {
            if (mStoped || mPaused)
                return;
            mPaused = true;
        }
    }

    /**
     * resume loader.
     */
    public void resume() {
        synchronized (mLock) {
            if (mStoped)
                throw new IllegalArgumentException("This loader is stoped already");
            if (mPaused) {
                mPaused = false;
                mLock.notify();
            }
        }
    }

    class Worker extends Thread {
        private int mWorkTag;

        public Worker(int id) {
            super("AsyncWorker #" + id);
            mWorkTag = mTag;
        }

        public void run() {
            while (true) {
                Node node = null;
                synchronized (mLock) {
                    mWorkTag = mTag;
                    if (mStoped)
                        break;
                    if (mPaused || mHead.next == mTail) {
                        try {
                            mLock.wait();
                        } catch (InterruptedException e) {
                            // ignore
                        }
                        if (mStoped)
                            break;
                    } else {
                        node = mHead.next;
                        detach(node);
                        node.next = null;
                        node.prev = null;

                        // awake another worker if there is pending task
                        if (mHead.next != mTail)
                            mLock.notify();
                    }
                }
                if (node != null) {
                    R data = mProxy.doInBackground(node.key, node.param, node.extra);
                    node.data = data;
                    Message msg = mHandler.obtainMessage();
                    msg.obj = node;
                    msg.arg1 = mWorkTag;
                    mHandler.sendMessage(msg);
                }
            }
        }
    }
}
