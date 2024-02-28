/*
 * Copyright (c) 2004-2024 The MZmine Development Team
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

package io.github.mzmine.taskcontrol;

import io.github.mzmine.taskcontrol.operations.TaskSubProcessor;
import java.time.Instant;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Represents a basic task that can be manged by the MZmine task controller but does not require the
 * functionality of the more sophisticated {@link AbstractTask} or {@link AbstractSimpleTask}
 * classes.
 */
public class SimpleCalculationTask<T extends TaskSubProcessor> extends AbstractTask {

  private static final Logger logger = Logger.getLogger(SimpleCalculationTask.class.getName());

  private final T processor;

  public SimpleCalculationTask(T processor) {
    super(null, Instant.now());
    this.processor = processor;
  }

  public static <T extends TaskSubProcessor> SimpleCalculationTask<T> of(T task) {
    return new SimpleCalculationTask<>(task);
  }

  @Override
  public String getTaskDescription() {
    return processor.getTaskDescription();
  }

  @Override
  public double getFinishedPercentage() {
    return processor.getFinishedPercentage();
  }

  @Override
  public void run() {
    setStatus(TaskStatus.PROCESSING);
    try {
      processor.process();
    } catch (RuntimeException e) {
      logger.log(Level.SEVERE, e.getMessage(), e);
      setStatus(TaskStatus.ERROR);
      return;
    }
    setStatus(TaskStatus.FINISHED);
  }

  public T getProcessor() {
    return processor;
  }
}
