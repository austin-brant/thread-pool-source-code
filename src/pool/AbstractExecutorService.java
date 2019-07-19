/*
 * ORACLE PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 */

/*
 *
 *
 *
 *
 *
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain, as explained at
 * http://creativecommons.org/publicdomain/zero/1.0/
 */

package java.util.concurrent;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

/**
 * AbstractExecutorService 抽象类派生自 ExecutorService 接口，然后在其基础上实现了几个实用的方法，这些方法提供给子类进行调用。
 * <p>
 * 这个抽象类实现了 invokeAny 方法和 invokeAll 方法，这里的两个 newTaskFor 方法也比较有用，用于将任务包装成 FutureTask。
 * 定义于最上层接口 Executor中的 void execute(Runnable command) 由于不需要获取结果，不会进行 FutureTask 的包装。
 * <p>
 * 需要获取结果（FutureTask），用 submit 方法，不需要获取结果，可以用 execute 方法。
 */
public abstract class AbstractExecutorService implements ExecutorService {

    /**
     * RunnableFuture 是用于获取执行结果的，我们常用它的子类 FutureTask
     * 下面两个 newTaskFor 方法用于将我们的任务包装成 FutureTask 提交到线程池中执行
     */
    protected <T> RunnableFuture<T> newTaskFor(Runnable runnable, T value) {
        return new FutureTask<T>(runnable, value);
    }

    protected <T> RunnableFuture<T> newTaskFor(Callable<T> callable) {
        return new FutureTask<T>(callable);
    }

    /**
     * 提交任务
     */
    public Future<?> submit(Runnable task) {
        if (task == null) {
            throw new NullPointerException();
        }
        // 1. 将任务包装成 FutureTask
        RunnableFuture<Void> ftask = newTaskFor(task, null);
        // 2. 交给执行器执行
        execute(ftask);
        return ftask;
    }

    public <T> Future<T> submit(Runnable task, T result) {
        if (task == null) {
            throw new NullPointerException();
        }
        // 1. 将任务包装成 FutureTask
        RunnableFuture<T> ftask = newTaskFor(task, result);
        // 2. 交给执行器执行
        execute(ftask);
        return ftask;
    }

    public <T> Future<T> submit(Callable<T> task) {
        if (task == null) {
            throw new NullPointerException();
        }
        // 1. 将任务包装成 FutureTask
        RunnableFuture<T> ftask = newTaskFor(task);
        // 2. 交给执行器执行
        execute(ftask);
        return ftask;
    }

    /**
     * 此方法目的：将 tasks 集合中的任务提交到线程池执行，任意一个线程执行完后就可以结束了
     * 第二个参数 timed 代表是否设置超时机制，超时时间为第三个参数，
     * 如果 timed 为 true，同时超时了还没有一个线程返回结果，那么抛出 TimeoutException 异常
     */
    private <T> T doInvokeAny(Collection<? extends Callable<T>> tasks,
                              boolean timed, long nanos)
            throws InterruptedException, ExecutionException, TimeoutException {
        if (tasks == null) {
            throw new NullPointerException();
        }
        int ntasks = tasks.size();
        if (ntasks == 0) {
            throw new IllegalArgumentException();
        }
        ArrayList<Future<T>> futures = new ArrayList<Future<T>>(ntasks);

        // ExecutorCompletionService 不是一个真正的执行器，参数 this 才是真正的执行器
        // 它对执行器进行了包装，每个任务结束后，将结果保存到内部的一个 completionQueue 队列中
        // 这也是为什么这个类的名字里面有个 Completion 的原因。
        ExecutorCompletionService<T> ecs =
                new ExecutorCompletionService<T>(this);

        // For efficiency, especially in executors with limited
        // parallelism, check to see if previously submitted tasks are
        // done before submitting more of them. This interleaving
        // plus the exception mechanics account for messiness of main
        // loop.

        try {
            // 用于保存异常信息，此方法如果没有得到任何有效的结果，那么我们可以抛出最后得到的一个异常
            ExecutionException ee = null;
            final long deadline = timed ? System.nanoTime() + nanos : 0L;
            Iterator<? extends Callable<T>> it = tasks.iterator();

            // 首先先提交一个任务，后面的任务到下面的 for 循环一个个提交
            futures.add(ecs.submit(it.next()));
            --ntasks;   // 提交了一个任务，所以任务数量减 1
            int active = 1;  // 正在执行的任务数(提交的时候 +1，任务结束的时候 -1)

            for (; ; ) {
                // ecs 上面说了，其内部有一个 completionQueue 用于保存执行完成的结果
                // BlockingQueue的poll方法不阻塞，返回 null 代表队列为空
                Future<T> f = ecs.poll();  // 非阻塞

                // 为 null，说明刚刚提交的第一个线程还没有执行完成
                // 在前面先提交一个任务，加上这里做一次检查，也是为了提高性能
                if (f == null) {
                    if (ntasks > 0) { // 再提交一个任务
                        --ntasks;
                        futures.add(ecs.submit(it.next()));
                        ++active;
                    } else if (active == 0) {  // 没有任务了，同时active为0,说明 任务都执行完成了
                        break;
                    } else if (timed) {
                        f = ecs.poll(nanos, TimeUnit.NANOSECONDS);  // 带等待时间的poll方法
                        if (f == null) {
                            throw new TimeoutException();  // 如果已经超时，抛出 TimeoutException 异常，这整个方法就结束了
                        }
                        nanos = deadline - System.nanoTime();
                    } else {
                        f = ecs.take();  // 没有任务了，有一个在运行中，再获取一次结果，阻塞方法，直到任务结束
                    }
                }

                 /*
                 * 我感觉上面这一段并不是很好理解，这里简单说下：
                 * 1. 首先，这在一个 for 循环中，我们设想每一个任务都没那么快结束，
                 *     那么，每一次都会进到第一个分支，进行提交任务，直到将所有的任务都提交了
                 * 2. 任务都提交完成后，如果设置了超时，那么 for 循环其实进入了“一直检测是否超时”
                       这件事情上
                 * 3. 如果没有设置超时机制，那么不必要检测超时，那就会阻塞在 ecs.take() 方法上，
                       等待获取第一个执行结果
                 * ?. 这里我还没理解 active == 0 这个分支的到底是干嘛的？
                 */

                if (f != null) {  // 有任务结束了
                    --active;
                    try {
                        return f.get();  // 阻塞获取执行结果，如果有异常，都包装成 ExecutionException
                    } catch (ExecutionException eex) {
                        ee = eex;
                    } catch (RuntimeException rex) {
                        ee = new ExecutionException(rex);
                    }
                }
            }

            if (ee == null) {
                ee = new ExecutionException();
            }
            throw ee;

        } finally {
            // 方法退出之前，取消其他的任务
            for (int i = 0, size = futures.size(); i < size; i++) {
                futures.get(i).cancel(true);
            }
        }
    }

    /**
     * 将tasks集合中的任务提交到线程池执行，任意一个线程执行完后就可以结束了，不设置超时时间
     */
    public <T> T invokeAny(Collection<? extends Callable<T>> tasks)
            throws InterruptedException, ExecutionException {
        try {
            return doInvokeAny(tasks, false, 0);
        } catch (TimeoutException cannotHappen) {
            assert false;
            return null;
        }
    }

    /**
     * 将tasks集合中的任务提交到线程池执行，任意一个线程执行完后就可以结束了，需要指定超时时间
     */
    public <T> T invokeAny(Collection<? extends Callable<T>> tasks,
                           long timeout, TimeUnit unit)
            throws InterruptedException, ExecutionException, TimeoutException {
        return doInvokeAny(tasks, true, unit.toNanos(timeout));
    }

    /**
     * 将tasks集合中的任务提交到线程池执行，全部线程执行完后才可以结束了
     * 其实我们自己提交任务到线程池，也是想要线程池执行所有的任务
     * 只不过，我们是每次 submit 一个任务，这里以一个集合作为参数提交
     */
    public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks)
            throws InterruptedException {
        if (tasks == null) {
            throw new NullPointerException();
        }
        ArrayList<Future<T>> futures = new ArrayList<Future<T>>(tasks.size());
        boolean done = false;
        try {
            for (Callable<T> t : tasks) {
                // 包装成 FutureTask
                RunnableFuture<T> f = newTaskFor(t);
                futures.add(f);
                // 提交任务
                execute(f);
            }
            for (int i = 0, size = futures.size(); i < size; i++) {
                Future<T> f = futures.get(i);
                if (!f.isDone()) {
                    try {
                        // 这是一个阻塞方法，直到获取到值，或抛出了异常
                        // 这里有个小细节，其实 get 方法签名上是会抛出 InterruptedException 的
                        // 可是这里没有进行处理，而是抛给外层去了。此异常发生于还没执行完的任务被取消了
                        f.get();
                    } catch (CancellationException ignore) {
                    } catch (ExecutionException ignore) {
                    }
                }
            }
            done = true;
            // 这个方法返回返回 List<Future>，而且是任务都结束了
            return futures;
        } finally {
            if (!done) {  // 异常情况下才会进入
                // 方法退出之前，取消其他的任务
                for (int i = 0, size = futures.size(); i < size; i++) {
                    futures.get(i).cancel(true);
                }
            }
        }
    }

    /**
     * 带超时的 invokeAll
     */
    public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks,
                                         long timeout, TimeUnit unit)
            throws InterruptedException {
        if (tasks == null) {
            throw new NullPointerException();
        }
        long nanos = unit.toNanos(timeout);
        ArrayList<Future<T>> futures = new ArrayList<Future<T>>(tasks.size());
        boolean done = false;
        try {
            for (Callable<T> t : tasks) {
                futures.add(newTaskFor(t));
            }

            final long deadline = System.nanoTime() + nanos; // 直接计算出超时时刻
            final int size = futures.size();

            // 提交一个任务，检测一次是否超时
            for (int i = 0; i < size; i++) {
                execute((Runnable) futures.get(i));
                nanos = deadline - System.nanoTime();
                if (nanos <= 0L) {
                    return futures;
                }
            }

            for (int i = 0; i < size; i++) {
                Future<T> f = futures.get(i);
                if (!f.isDone()) {
                    if (nanos <= 0L) {
                        return futures;
                    }
                    try {
                        // 调用带超时的 get 方法，这里的参数 nanos 是剩余的时间，
                        // 因为上面其实已经用掉了一些时间了
                        f.get(nanos, TimeUnit.NANOSECONDS);
                    } catch (CancellationException ignore) {
                    } catch (ExecutionException ignore) {
                    } catch (TimeoutException toe) {
                        return futures;
                    }
                    nanos = deadline - System.nanoTime(); // 更新剩余时间
                }
            }
            done = true;
            return futures;
        } finally {
            if (!done) {
                for (int i = 0, size = futures.size(); i < size; i++) {
                    futures.get(i).cancel(true);
                }
            }
        }
    }

}
