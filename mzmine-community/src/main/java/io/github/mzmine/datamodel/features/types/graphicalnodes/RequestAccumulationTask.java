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

import io.github.mzmine.taskcontrol.AbstractTask;
import io.github.mzmine.taskcontrol.TaskStatus;
import io.github.mzmine.util.Timer;
import java.time.Instant;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.util.Duration;
import org.jetbrains.annotations.NotNull;

public abstract class RequestAccumulationTask<REQUEST_MODEL> extends AbstractTask implements
    ThreadFactory {

  private static final Logger logger = Logger.getLogger(RequestAccumulationTask.class.getName());
  private final ConcurrentLinkedQueue<REQUEST_MODEL> workQueue;
  private final AtomicInteger processedItems;
  private final long POLL_TIMEOUT_MS = 30;
  private final long shutdownTimeout;
  private volatile boolean acceptsWork = true;

  protected RequestAccumulationTask(@NotNull String taskName) {
    this(taskName, Duration.millis(2000));
  }

  protected RequestAccumulationTask(@NotNull String taskName, @NotNull Duration shutdownTimeout) {
    super(Instant.now(), taskName);
    this.shutdownTimeout = (long) shutdownTimeout.toMillis();
    this.workQueue = new ConcurrentLinkedQueue<>();
    this.processedItems = new AtomicInteger(0);

    // auto quit all if cancel
    addTaskStatusListener((_, status, _) -> {
      if (status == TaskStatus.CANCELED || status == TaskStatus.ERROR) {
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

  @Override
  public void run() {
    try {
      setStatus(TaskStatus.PROCESSING);

      process();

      if (!isCanceled()) {
        setStatus(TaskStatus.FINISHED);
      }
    } catch (Throwable e) {
      logger.log(Level.SEVERE, "Unhandled exception " + e.getMessage() + " while processing task "
          + getTaskDescription(), e);

      if (e instanceof Exception exception) {
        error(e.getMessage(), exception);
      } else {
        error(e.getMessage());
      }
    }
  }

  public void process() {
    final Timer waitTimer = new Timer(shutdownTimeout, () -> setStatus(TaskStatus.FINISHED));

    while (!isCanceled()) {
      // Poll for work items and submit them to the thread pool
      REQUEST_MODEL workItem;
      while ((workItem = workQueue.poll()) != null && !isCanceled()) {
        waitTimer.restart();
        final REQUEST_MODEL finalWorkItem = workItem;

        // Submit each work item as a separate task to the thread pool
        try {
          processRequest(finalWorkItem);
          processedItems.incrementAndGet();
        } catch (Exception e) {
          logger.log(Level.WARNING, "Error processing work item in " + getName(), e);
        }
      }
      try {
        TimeUnit.MILLISECONDS.sleep(POLL_TIMEOUT_MS);
      } catch (InterruptedException e) {
        logger.log(Level.WARNING, e.getMessage(), e);
        setStatus(TaskStatus.CANCELED);
        return;
      }
      waitTimer.tick();
    }
  }

  /**
   * Process a single work item - this will be called by worker threads in the thread pool
   *
   * @param workItem the work item to process
   */
  public abstract void processRequest(REQUEST_MODEL workItem);

  @Override
  public String getTaskDescription() {
    return getName() + " (Queue: " + getQueueSize() + ", Processed: " + getProcessedCount();
  }

  @Override
  public double getFinishedPercentage() {
    final int work = workQueue.size();
    final int finished = processedItems.get();
    int total = finished + work;
    if (total == 0) {
      return 1.0;
    }
    return (double) finished / total;
  }

}
