// Copyright (C) 2025 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.googlesource.gerrit.plugins.deleteproject;

import static java.util.concurrent.Executors.callable;

import com.google.common.util.concurrent.MoreExecutors;
import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.PriorityQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.Delayed;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.Ignore;

@Ignore
public class FakeScheduledExecutorService implements ScheduledExecutorService {
  private ExecutorService directExecutor = MoreExecutors.newDirectExecutorService();
  private Duration currentTime = Duration.ZERO;
  private final PriorityQueue<FakeScheduledFuture<?>> taskQueue = new PriorityQueue<>();

  public void advance(long delta, TimeUnit timeUnit) {
    currentTime = currentTime.plusNanos(timeUnit.toNanos(delta));
    runTasksInQueue();
  }

  private void runTasksInQueue() {
    while (!taskQueue.isEmpty()
        && taskQueue.peek().getDelay(TimeUnit.NANOSECONDS) <= currentTime.toNanos()) {
      FakeScheduledFuture<?> taskToRun = taskQueue.poll();

      if (!taskToRun.isCancelled()) {
        taskToRun.run(currentTime);

        if (taskToRun.getPeriod() > 0) {
          FakeScheduledFuture<?> unused = queue(taskToRun);
        }
      }
    }
  }

  private class FakeScheduledFuture<T> implements ScheduledFuture<T> {
    private Future<T> future;
    private final long period;
    private final TimeUnit delayUnit;
    private long delay;
    private Callable<T> taskToRun;

    FakeScheduledFuture(Callable<T> taskToRun, long delay, TimeUnit unit) {
      this(taskToRun, delay, 0L, unit);
    }

    FakeScheduledFuture(Callable<T> taskToRun, long delay, long period, TimeUnit unit) {
      this.taskToRun = taskToRun;
      this.delay = delay;
      this.period = period;
      this.delayUnit = unit;

      if (delay > 0 || period > 0) {
        this.future = null;
      } else {
        this.future = directExecutor.submit(taskToRun);
      }
    }

    @Override
    public long getDelay(TimeUnit unit) {
      return unit.convert(delay, delayUnit);
    }

    @Override
    public int compareTo(Delayed o) {
      throw new UnsupportedOperationException();
    }

    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
      if (future != null) {
        return future.cancel(mayInterruptIfRunning);
      }
      taskToRun = null;
      return true;
    }

    @Override
    public boolean isCancelled() {
      return future != null && future.isCancelled();
    }

    @Override
    public boolean isDone() {
      return future != null && future.isDone();
    }

    @Override
    public T get() throws InterruptedException, ExecutionException {
      return future.get();
    }

    @Override
    public T get(long timeout, TimeUnit unit)
        throws InterruptedException, ExecutionException, TimeoutException {
      return future.get(timeout, unit);
    }

    void run(Duration currentTime) {
      this.future = directExecutor.submit(taskToRun);
      this.delay = delayUnit.convert(currentTime) + period;
    }

    public long getPeriod() {
      return period;
    }
  }

  @Override
  public ScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit unit) {
    return queue(new FakeScheduledFuture<>(callable(command), delay, unit));
  }

  @Override
  public <V> ScheduledFuture<V> schedule(Callable<V> callable, long delay, TimeUnit unit) {
    return queue(new FakeScheduledFuture<>(callable, delay, unit));
  }

  @Override
  public ScheduledFuture<?> scheduleAtFixedRate(
      Runnable command, long initialDelay, long period, TimeUnit unit) {
    return queue(new FakeScheduledFuture<>(callable(command), initialDelay, period, unit));
  }

  @Override
  public ScheduledFuture<?> scheduleWithFixedDelay(
      Runnable command, long initialDelay, long delay, TimeUnit unit) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void shutdown() {
    throw new UnsupportedOperationException();
  }

  @Override
  public List<Runnable> shutdownNow() {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean isShutdown() {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean isTerminated() {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
    throw new UnsupportedOperationException();
  }

  @Override
  public <T> Future<T> submit(Callable<T> task) {
    return directExecutor.submit(task);
  }

  @Override
  public <T> Future<T> submit(Runnable task, T result) {
    return directExecutor.submit(task, result);
  }

  @Override
  public Future<?> submit(Runnable task) {
    return directExecutor.submit(task);
  }

  @Override
  public void close() {
    directExecutor.close();
  }

  @Override
  public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks)
      throws InterruptedException {
    throw new UnsupportedOperationException();
  }

  @Override
  public <T> List<Future<T>> invokeAll(
      Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit)
      throws InterruptedException {
    throw new UnsupportedOperationException();
  }

  @Override
  public <T> T invokeAny(Collection<? extends Callable<T>> tasks)
      throws InterruptedException, ExecutionException {
    throw new UnsupportedOperationException();
  }

  @Override
  public <T> T invokeAny(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit)
      throws InterruptedException, ExecutionException, TimeoutException {
    throw new UnsupportedOperationException();
  }

  @Override
  public void execute(Runnable command) {
    directExecutor.execute(command);
  }

  private <T> FakeScheduledFuture<T> queue(FakeScheduledFuture<T> scheduledFuture) {
    if (scheduledFuture.getDelay(TimeUnit.NANOSECONDS) > 0) {
      taskQueue.add(scheduledFuture);
    }
    return scheduledFuture;
  }
}
