package net.carleolee.android.util;

import android.graphics.Bitmap;
import android.widget.BaseAdapter;
import android.widget.ImageView;

/**
 * Base adapter for list, grid and gallery with icons.
 * @param <K> the type of key for icon
 * @param <E> the type of extra parameter
 */
public abstract class IconAdapter<K, E> extends BaseAdapter {

    protected CacheStrategy<K, Bitmap> mImageCache;

    protected AsyncLoader<K, String, ImageView, E, Bitmap> mImageLoader;

    protected boolean mActive;

    protected volatile boolean mNetworkUp = true;

    protected final boolean mLocalAsync;

    protected final int mDefaultRes;

    protected final int mLoadingRes;

    protected AsyncLoader.LoaderProxy<K, String, ImageView, E, Bitmap> mProxy;

    /**
     * constructor with default cache and loader capacity.
     * @param async load local image async or not
     * @param default_res default resource id
     * @param loading_res loading resource id
     */
    public IconAdapter(boolean async, int default_res, int loading_res) {
        mLocalAsync = async;
        mDefaultRes = default_res;
        mLoadingRes = loading_res;
        init();
        mImageCache = new CacheStrategy<K, Bitmap>();
        mImageLoader = new AsyncLoader<K, String, ImageView, E, Bitmap>(mProxy);
    }

    /**
     * constructor with custom capacity.
     * @param async load local image async or not
     * @param default_res default resource id
     * @param loading_res loading resource id
     * @param cacheCapacity cache capacity
     */
    public IconAdapter(boolean async, int default_res, int loading_res,
            int cacheCapacity) {
        mLocalAsync = async;
        mDefaultRes = default_res;
        mLoadingRes = loading_res;
        init();
        mImageCache = new CacheStrategy<K, Bitmap>(cacheCapacity);
        mImageLoader = new AsyncLoader<K, String, ImageView, E, Bitmap>(mProxy);
    }

    /**
     * constructor with custom capacity.
     * @param async load local image async or not
     * @param default_res default resource id
     * @param loading_res loading resource id
     * @param cacheCapacity cache capacity
     * @param loaderCapacity loader capacity
     * @param loaderConcurrency max number of workers in loader
     */
    public IconAdapter(boolean async, int default_res, int loading_res,
            int cacheCapacity, int loaderCapacity, int loaderConcurrency) {
        mLocalAsync = async;
        mDefaultRes = default_res;
        mLoadingRes = loading_res;
        init();
        mImageCache = new CacheStrategy<K, Bitmap>(cacheCapacity);
        mImageLoader = new AsyncLoader<K, String, ImageView, E, Bitmap>(
                loaderCapacity, loaderConcurrency, mProxy);
    }

    private void init() {
        mProxy = new AsyncLoader.LoaderProxy<K, String, ImageView, E, Bitmap>() {
            @Override
            public Bitmap doInBackground(K key, String url,
                    ImageView image, E extra) {
                Bitmap bm = null;
                if (mLocalAsync)
                    bm = loadImageLocal(key, url, extra);
                if (bm == null) {
                    if (mNetworkUp)
                        bm = loadImageRemote(key, url, extra);
                }
                return bm;
            }

            @Override
            public void onLoaded(K key, String url, ImageView image,
                    E extra, Bitmap drawable) {
                onImageLoaded(key, url, image, extra, drawable);
            }
        };
    }

    /**
     * set network status
     */
    public void setNetworkStatus(boolean available) {
        mNetworkUp = available;
    }

    /**
     * load image from local in main thread
     */
    protected abstract Bitmap loadImageLocal(K key, String url, E extra);

    /**
     * load image from remote in background thread
     */
    protected abstract Bitmap loadImageRemote(K key, String url, E extra);

    /**
     * call on main thread when image loaded.
     */
    protected void onImageLoaded(K key, String url, ImageView image,
            E extra, Bitmap bm) {
        Object objTag = image.getTag();
        boolean matched = (objTag != null && key.equals(objTag));

        if (bm == null) {
            if (matched && mLoadingRes > 0)
                image.setImageResource(mDefaultRes);
        } else {
            if (matched)
                image.setImageBitmap(bm);
            if (mActive)
                mImageCache.put(key, bm);
            else
                mImageCache.putWeak(key, bm);
        }

        bindImageHook(key, url, image, extra, bm);
    }

    /**
     * hook after bind image
     */
    protected void bindImageHook(K key, String url, ImageView image,
            E extra, Bitmap bm) {
        // stub
    }

    /**
     * bind image
     */
    protected void bindImage(K key, String url, ImageView image, E extra) {
        image.setTag(key);
        if (key == null) {
            image.setImageResource(mDefaultRes);
            bindImageHook(key, url, image, extra, null);
            return;
        }

        Bitmap bm = mImageCache.get(key);
        if (bm == null && !mLocalAsync)
            bm = loadImageLocal(key, url, extra);

        if (bm != null) {
            image.setImageBitmap(bm);
            bindImageHook(key, url, image, extra, bm);
        } else {
            if (mNetworkUp && url != null && url.length() > 0) {
                mImageLoader.loadData(key, url, image, extra);
                if (mLoadingRes > 0)
                    image.setImageResource(mLoadingRes);
                else
                    image.setImageResource(mDefaultRes);
            } else {
                image.setImageResource(mDefaultRes);
                bindImageHook(key, url, image, extra, null);
            }
        }
    }

    /**
     * owner activty should call this in its onDestroy() method to
     * clear cache and stop loader.
     */
    public void onDestroy() {
        mImageCache.clear();
        mImageLoader.stop();
    }

    /**
     * owner activity should call this method in its onStop() method
     * to pause loader and reduce cache usage.
     */
    public void onStop() {
        mActive = false;
        mImageCache.release();
        mImageLoader.pause();
    }

    /**
     * owner activity should call this method in its onResume() method to
     * resume loader.
     */
    public void onResume() {
        mActive = true;
        mImageLoader.resume();
    }

    /**
     * discard pending task.
     */
    public void resetLoader() {
        mImageLoader.invalidate();
    }

    /**
     * release or clear (if 'clear' is true) cache.
     */
    public void releaseCache(boolean clear) {
        if (clear)
            mImageCache.clear();
        else
            mImageCache.release();
    }

    @Override
    public void notifyDataSetChanged() {
        mImageLoader.invalidate();
        super.notifyDataSetChanged();
    }
}
