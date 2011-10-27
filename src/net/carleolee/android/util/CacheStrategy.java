package net.carleolee.android.util;

import java.lang.ref.SoftReference;
import java.util.HashMap;

/**
 * LRU cache utility. Note that this implementation is not synchronized.
 *
 * @param <K> the type of keys
 * @param <T> the type of cached values
 */
public class CacheStrategy<K, V> {

    /** default cache size */
    public static final int DEFAULT_CAPACITY = 16;

    /** Node for bi-directional linked list */
    class Node {
        Node prev;
        Node next;
        K key;
        V data;
    }

    private final int mCapacity;
    private final Node mHead;
    private final Node mTail;
    private HashMap<K, SoftReference<Node>> mMap =
            new HashMap<K, SoftReference<Node>>();
    private int mSize;

    /**
     * construct CacheStrategy with default capacity. Currently
     * default capacity is 16.
     */
    public CacheStrategy() {
        this(DEFAULT_CAPACITY);
    }

    /**
     * construct CacheStrategy with customized capacity.
     * Note that you should not set a large capacity. Most recently used
     * 'capacity' data will be cached with strong reference.
     *
     * @param capacity
     */
    public CacheStrategy(int capacity) {
        if (capacity <= 1)
            throw new IllegalArgumentException("capacity must be great than one");
        mCapacity = capacity;
        mHead = new Node();
        mTail = new Node();
        mHead.next = mTail;
        mTail.prev = mHead;
        mSize = 0;
    }

    private void detach(Node node) {
        node.prev.next = node.next;
        node.next.prev = node.prev;

        node.next = null;
        node.prev = null;
    }

    private void attach(Node head, Node node) {
        node.prev = head;
        node.next = head.next;
        node.next.prev = node;
        head.next = node;
    }

    private void enqueue(Node node) {
        attach(mHead, node);
        mSize++;
        if (mSize > mCapacity) {
            if (mTail.prev != mHead) {
                detach(mTail.prev);
                mSize--;
            }
        }
    }

    /**
     * get cached data of given key, return null if cache missed.
     */
    public V get(K key) {
        SoftReference<Node> ref = mMap.get(key);
        if (ref == null)
            return null;

        Node node = ref.get();
        if (node == null) {
            mMap.remove(key);
            return null;
        } else {
            // still in link, try to move to head
            if (node.prev != null && node.next != null) {
                if (node.prev != mHead) {
                    detach(node);
                    attach(mHead, node);
                }
            } else {
                enqueue(node);
            }
            return node.data;
        }
    }

    /**
     * cache data for given key.
     */
    public void put(K key, V data) {
        SoftReference<Node> ref = mMap.get(key);
        Node node = null;
        if (ref != null) {
            node = ref.get();
            if (node != null) {
                node.key = key;
                node.data = data;
                // still in link, move to head
                if (node.prev != null && node.next != null) {
                    if (node.prev != mHead) {
                        detach(node);
                        attach(mHead, node);
                    }
                } else {
                    enqueue(node);
                }
                return;
            }
        }

        node = new Node();
        node.key = key;
        node.data = data;
        ref = new SoftReference<Node>(node);
        mMap.put(key, ref);
        enqueue(node);
    }

    /**
     * Just keep a soft reference
     */
    public void putWeak(K key, V data) {
        Node node = new Node();
        node.key = key;
        node.data = data;
        SoftReference<Node> ref = new SoftReference<Node>(node);
        ref = new SoftReference<Node>(node);
        mMap.put(key, ref);
    }

    /**
     * clear cache.
     */
    public void clear() {
        mMap.clear();
        mSize = 0;
        mHead.next = mTail;
        mTail.prev = mHead;
    }

    /**
     * release all strong references to cached data. Typically you call this
     * method in Activity.onPause() or Activity.onStop().
     */
    public void release() {
        while (mHead.next != mTail)
            detach(mHead.next);
        mSize = 0;
    }

}
