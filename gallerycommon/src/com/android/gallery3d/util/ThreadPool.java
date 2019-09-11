/*
 * Copyright (C) 2010 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.gallery3d.util;

import android.util.Log;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class ThreadPool {
    // Resource type
    public static final int MODE_NONE = 0;
    public static final int MODE_CPU = 1;
    public static final int MODE_NETWORK = 2;
    public static final JobContext JOB_CONTEXT_STUB = new JobContextStub();

    public static final int CPU_CORES_NUM = Runtime.getRuntime().availableProcessors();
    public static final int PARALLEL_THREAD_NUM_MIN = 2;
    public static final int PARALLEL_THREAD_NUM =
            PARALLEL_THREAD_NUM_MIN > (CPU_CORES_NUM / 2) ? PARALLEL_THREAD_NUM_MIN : (CPU_CORES_NUM / 2);

    private static final String TAG = "ThreadPool";
    private static final int CORE_POOL_SIZE = 8;
    private static final int MAX_POOL_SIZE = 10;
    private static final int KEEP_ALIVE_TIME = 5; // 10 seconds
    private final ThreadPoolExecutor mExecutor;

    ResourceCounter mCpuCounter = new ResourceCounter(PARALLEL_THREAD_NUM);
    ResourceCounter mNetworkCounter = new ResourceCounter(PARALLEL_THREAD_NUM);

    public ThreadPool() {
        this(CORE_POOL_SIZE, MAX_POOL_SIZE);
    }

    public ThreadPool(int initPoolSize, int maxPoolSize) {
        Log.d(TAG, "cpu cores: " + CPU_CORES_NUM + " MIN: " + PARALLEL_THREAD_NUM_MIN + " NUM: " + PARALLEL_THREAD_NUM);
        mExecutor = new ThreadPoolExecutor(
                initPoolSize, maxPoolSize, KEEP_ALIVE_TIME,
                TimeUnit.SECONDS, new LinkedBlockingQueue<Runnable>(),
                new PriorityThreadFactory("thread-pool",
                        android.os.Process.THREAD_PRIORITY_DEFAULT));
    }

    // Submit a job to the thread pool. The listener will be called when the
    // job is finished (or cancelled).
    public <T> Future<T> submit(Job<T> job, FutureListener<T> listener) {
        Worker<T> w = new Worker<T>(job, listener);
        mExecutor.execute(w);
        /*
        Log.d(TAG, " pool size: " + mExecutor.getPoolSize()
                + " queue size: " + mExecutor.getQueue().size() + " task count: " + mExecutor.getTaskCount()
                + " max pool size: " + mExecutor.getMaximumPoolSize() + " completed count: " + mExecutor.getCompletedTaskCount()
                + " large size: " + mExecutor.getLargestPoolSize() + " active count: " + mExecutor.getActiveCount());
                */
        return w;
    }

    public <T> Future<T> submit(Job<T> job) {
        return submit(job, null);
    }

    // A Job is like a Callable, but it has an addition JobContext parameter.
    public interface Job<T> {
        public T run(JobContext jc);
    }

    public interface JobContext {
        boolean isCancelled();

        void setCancelListener(CancelListener listener);

        boolean setMode(int mode);
    }

    public interface CancelListener {
        public void onCancel();
    }

    private static class JobContextStub implements JobContext {
        @Override
        public boolean isCancelled() {
            return false;
        }

        @Override
        public void setCancelListener(CancelListener listener) {
        }

        @Override
        public boolean setMode(int mode) {
            return true;
        }
    }

    private static class ResourceCounter {
        public int value;

        public ResourceCounter(int v) {
            value = v;
        }
    }

    private class Worker<T> implements Runnable, Future<T>, JobContext {
        @SuppressWarnings("hiding")
        //private static final String TAG = "Worker";
        private Job<T> mJob;
        private FutureListener<T> mListener;
        private CancelListener mCancelListener;
        private ResourceCounter mWaitOnResource;
        private volatile boolean mIsCancelled;
        private boolean mIsDone;
        private T mResult;
        private int mMode;

        public Worker(Job<T> job, FutureListener<T> listener) {
            mJob = job;
            mListener = listener;
        }

        // This is called by a thread in the thread pool.
        @Override
        public void run() {
            T result = null;

            // A job is in CPU mode by default. setMode returns false
            // if the job is cancelled.
            if (setMode(MODE_CPU)) {
                try {
                    long t1 = System.currentTimeMillis();
                    result = mJob.run(this);
                    Log.v(TAG, "It takes " + (System.currentTimeMillis() - t1) + " ms to execute cmd for job: " + mJob);
                } catch (Throwable ex) {
                    Log.w(TAG, "Exception in running a job", ex);
                }
            }

            synchronized (this) {
                setMode(MODE_NONE);
                mResult = result;
                mIsDone = true;
                notifyAll();
            }
            if (mListener != null) mListener.onFutureDone(this);
        }

        // Below are the methods for Future.
        @Override
        public synchronized void cancel() {
            if (mIsCancelled) return;
            mIsCancelled = true;
            if (mWaitOnResource != null) {
                synchronized (mWaitOnResource) {
                    mWaitOnResource.notifyAll();
                }
            }
            if (mCancelListener != null) {
                mCancelListener.onCancel();
            }
        }

        @Override
        public boolean isCancelled() {
            return mIsCancelled;
        }

        @Override
        public synchronized boolean isDone() {
            return mIsDone;
        }

        @Override
        public synchronized T get() {
            while (!mIsDone) {
                try {
                    wait();
                } catch (Exception ex) {
                    Log.w(TAG, "ingore exception", ex);
                    // ignore.
                }
            }
            return mResult;
        }

        @Override
        public void waitDone() {
            get();
        }

        // Below are the methods for JobContext (only called from the
        // thread running the job)
        @Override
        public synchronized void setCancelListener(CancelListener listener) {
            mCancelListener = listener;
            if (mIsCancelled && mCancelListener != null) {
                mCancelListener.onCancel();
            }
        }

        @Override
        public boolean setMode(int mode) {
            // Release old resource
            ResourceCounter rc = modeToCounter(mMode);
            if (rc != null) releaseResource(rc);
            mMode = MODE_NONE;

            // Acquire new resource
            rc = modeToCounter(mode);
            if (rc != null) {
                if (!acquireResource(rc)) {
                    return false;
                }
                mMode = mode;
            }

            return true;
        }

        private ResourceCounter modeToCounter(int mode) {
            if (mode == MODE_CPU) {
                return mCpuCounter;
            } else if (mode == MODE_NETWORK) {
                return mNetworkCounter;
            } else {
                return null;
            }
        }

        private boolean acquireResource(ResourceCounter counter) {
            while (true) {
                synchronized (this) {
                    if (mIsCancelled) {
                        mWaitOnResource = null;
                        return false;
                    }
                    mWaitOnResource = counter;
                }

                synchronized (counter) {
                    if (counter.value > 0) {
                        counter.value--;
                        break;
                    } else {
                        try {
                            counter.wait();
                        } catch (InterruptedException ex) {
                            // ignore.
                        }
                    }
                }
            }

            synchronized (this) {
                mWaitOnResource = null;
            }

            return true;
        }

        private void releaseResource(ResourceCounter counter) {
            synchronized (counter) {
                counter.value++;
                counter.notifyAll();
            }
        }
    }
}
