/*
 * Copyright (c) 2004-2025 The mzmine Development Team
 *
 * Permission is hereby granted, free of charge, to any person
 * obtaining a copy of this software and associated documentation
 * files (the "Software"), to deal in the Software without
 * restriction, including without limitation the rights to use,
 * copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the
 * Software is furnished to do so, subject to the following
 * conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES
 * OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT
 * HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR
 * OTHER DEALINGS IN THE SOFTWARE.
 */

package io.github.mzmine.datamodel.features.types.graphicalnodes;

import io.github.mzmine.javafx.mvci.FxUpdateTask;
import io.github.mzmine.taskcontrol.TaskStatus;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.jetbrains.annotations.NotNull;

/**
 * CURRENTLY NOT USED MAY BE INTERESTING WITH THREAD POOLS.
 *
 * @param <REQUEST_MODEL>
 */
public abstract class RequestAccumulationThreadPoolTask<REQUEST_MODEL> extends
    FxUpdateTask<REQUEST_MODEL> implements ThreadFactory {

  private static final Logger logger = Logger.getLogger(
      RequestAccumulationThreadPoolTask.class.getName());
  private final ConcurrentLinkedQueue<REQUEST_MODEL> workQueue;
  private final AtomicInteger processedItems;
  private final List<Future<?>> submittedTasks;
  private final int numThreads;
  private final long POLL_TIMEOUT_MS = 30;
  private final long shutdownTimeout;
  private volatile boolean acceptsWork = true;

  protected RequestAccumulationThreadPoolTask(@NotNull String taskName, int numThreads,
      long shutdownTimeout, REQUEST_MODEL request) {
    super(taskName, request);
    this.numThreads = numThreads;
    this.shutdownTimeout = shutdownTimeout;
    this.workQueue = new ConcurrentLinkedQueue<>();
    this.processedItems = new AtomicInteger(0);
    this.submittedTasks = new ArrayList<>();

    // auto quit all if cancel
    addTaskStatusListener((_, status, _) -> {
      if (status == TaskStatus.CANCELED || status == TaskStatus.ERROR) {
        cancelAll();
        acceptsWork = false;
      } else if (status == TaskStatus.FINISHED) {
        acceptsWork = false;
      }
    });
  }

  @Override
  public Thread newThread(Runnable r) {
    Thread t = new Thread(r, getName() + "-Worker");
    t.setDaemon(true);
    return t;
  }

  /**
   * Adds work to the queue to be processed
   *
   * @param workItem the work item to add to the queue
   */
  public boolean addWork(REQUEST_MODEL workItem) {
    if (acceptsWork) {
      workQueue.offer(workItem);
      return true;
    }
    return false;
  }

  /**
   * Returns the current size of the work queue
   *
   * @return number of items waiting to be processed
   */
  public int getQueueSize() {
    return workQueue.size();
  }

  /**
   * Returns the number of items processed so far
   *
   * @return number of processed items
   */
  public int getProcessedCount() {
    return processedItems.get();
  }

  /**
   * Returns the number of currently submitted/running tasks
   *
   * @return number of active tasks
   */
  public int getActiveTasks() {
    return (int) submittedTasks.stream().filter(future -> !future.isDone()).count();
  }

  @Override
  protected void process() {
    try (ExecutorService threadPool = Executors.newFixedThreadPool(numThreads, this)) {
      // Main polling loop on the task thread
      while (!isCanceled()) {
        // Poll for work items and submit them to the thread pool
        REQUEST_MODEL workItem;
        while ((workItem = workQueue.poll()) != null && !isCanceled()) {
          final REQUEST_MODEL finalWorkItem = workItem;

          // Submit each work item as a separate task to the thread pool
          Future<?> future = threadPool.submit(() -> {
            try {
              processRequest(finalWorkItem);
              processedItems.incrementAndGet();
            } catch (Exception e) {
              logger.log(Level.WARNING, "Error processing work item in " + getName(), e);
            }
          });

          submittedTasks.add(future);
        }

        // Clean up completed tasks from our tracking list
        submittedTasks.removeIf(Future::isDone);

        // If no more work and no active tasks, we're done
        if (workQueue.isEmpty() && getActiveTasks() == 0) {
          break;
        }

        // Brief pause before polling again
        try {
          Thread.sleep(POLL_TIMEOUT_MS);
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          break;
        }
      }

      // Wait for any remaining submitted tasks to complete
      waitForSubmittedTasks();
    }
  }

  /**
   * Wait for all submitted tasks to complete
   */
  private void waitForSubmittedTasks() {
    for (Future<?> future : submittedTasks) {
      if (!future.isDone() && !future.isCancelled()) {
        try {
          future.get();
        } catch (Exception e) {
          logger.log(Level.WARNING, "Task did not complete within timeout", e);
          future.cancel(true);
        }
      }
    }
  }


  /**
   * Gracefully shutdown the thread pool
   */
  private void cancelAll() {
    if (isCanceled()) {
      submittedTasks.forEach(future -> future.cancel(true));
    }
  }

  /**
   * Process a single work item - this will be called by worker threads in the thread pool
   *
   * @param workItem the work item to process
   */
  public abstract void processRequest(REQUEST_MODEL workItem);

  @Override
  protected void updateGuiModel() {
    // Override if GUI updates are needed after processing
  }

  @Override
  public String getTaskDescription() {
    return getName() + " (Queue: " + getQueueSize() + ", Processed: " + getProcessedCount()
        + ", Active: " + getActiveTasks() + ")";
  }

  @Override
  public double getFinishedPercentage() {
    int total = processedItems.get() + workQueue.size() + getActiveTasks();
    if (total == 0) {
      return 1.0;
    }
    return (double) processedItems.get() / total;
  }

}
