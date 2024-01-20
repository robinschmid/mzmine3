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

package testutils;/*
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

import com.google.common.collect.Comparators;
import import_data.MzMLImportTest;
import io.github.mzmine.datamodel.RawDataFile;
import io.github.mzmine.datamodel.features.FeatureList;
import io.github.mzmine.main.MZmineCore;
import io.github.mzmine.modules.MZmineRunnableModule;
import io.github.mzmine.modules.io.import_rawdata_all.AdvancedSpectraImportParameters;
import io.github.mzmine.modules.io.import_rawdata_all.AllSpectralDataImportModule;
import io.github.mzmine.modules.io.import_rawdata_all.AllSpectralDataImportParameters;
import io.github.mzmine.modules.io.import_spectral_library.SpectralLibraryImportParameters;
import io.github.mzmine.parameters.ParameterSet;
import io.github.mzmine.project.impl.MZmineProjectImpl;
import io.github.mzmine.taskcontrol.AbstractTask;
import io.github.mzmine.taskcontrol.AllTasksFinishedListener;
import io.github.mzmine.taskcontrol.Task;
import io.github.mzmine.util.FeatureListRowSorter;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Assertions;

/**
 * @author Robin Schmid (https://github.com/robinschmid)
 */
public class MZmineTestUtil {

  private static final Logger logger = Logger.getLogger(MZmineTestUtil.class.getName());

  /**
   * Call a module in MZmineCore and wait for it to finish.
   *
   * @param timeoutSeconds timeout in seconds. if the module does not finish in time, exception is
   *                       thrown
   * @param moduleClass    the module to run
   * @param parameters     the module parameters
   * @return true if module finishes
   * @throws InterruptedException if module does not finish in time
   */
  public static TaskResult callModuleWithTimeout(long timeoutSeconds,
      @NotNull Class<? extends MZmineRunnableModule> moduleClass, @NotNull ParameterSet parameters)
      throws InterruptedException {
    return callModuleWithTimeout(timeoutSeconds, TimeUnit.SECONDS, moduleClass, parameters);
  }

  /**
   * Call a module in MZmineCore and wait for it to finish.
   *
   * @param timeout     timeout in time unit. if the module does not finish in time, exception is
   *                    thrown
   * @param unit        the time unit for the timeout
   * @param moduleClass the module to run
   * @param parameters  the module parameters
   * @return true if module finishes
   * @throws InterruptedException if module does not finish in time
   */
  public static TaskResult callModuleWithTimeout(long timeout, TimeUnit unit,
      @NotNull Class<? extends MZmineRunnableModule> moduleClass, @NotNull ParameterSet parameters)
      throws InterruptedException {
    List<Task> tasks = MZmineCore.runMZmineModule(moduleClass, parameters);
    List<AbstractTask> abstractTasks = new ArrayList<>(tasks.size());
    for (Task t : tasks) {
      if (t instanceof AbstractTask at) {
        abstractTasks.add(at);
      } else {
        throw new UnsupportedOperationException(
            "Currently only abstract tasks can be tested with this method");
      }
    }

    // wait for all tasks to finish
    List<String> errorMessage = Collections.synchronizedList(new ArrayList<>());
    CountDownLatch lock = new CountDownLatch(1);
    new AllTasksFinishedListener(abstractTasks, false, at -> {
      // free lock if succeeded
      lock.countDown();
    }, errorTasks -> {
      // concat error and free lock
      errorTasks.stream().map(Task::getErrorMessage).filter(Objects::nonNull)
          .forEach(errorMessage::add);
      lock.countDown();
    });

    // wait
    if (!lock.await(timeout, unit)) {
      return TaskResult.TIMEOUT;
    }

    if (errorMessage.size() > 0) {
      throw new RuntimeException(
          "Error in task for module " + moduleClass.getName() + ".  " + errorMessage.stream()
              .collect(Collectors.joining("; ")));
    }
    if (abstractTasks.stream().allMatch(task -> task.isFinished())) {
      return TaskResult.FINISHED;
    } else {
      return TaskResult.ERROR;
    }
  }

  /**
   * Check for default sorting
   *
   * @param flist feature list
   * @return
   */
  public static boolean isSorted(FeatureList flist) {
    return Comparators.isInOrder(flist.getRows(), FeatureListRowSorter.DEFAULT_RT);
  }

  public static void cleanProject() {
    MZmineCore.getProjectManager().setCurrentProject(new MZmineProjectImpl());
  }


  public static void importFiles(final List<String> fileNames, long timeoutSeconds)
      throws InterruptedException {
    importFiles(fileNames, timeoutSeconds, null);
  }

  public static void importFiles(final List<String> fileNames, long timeoutSeconds,
      @Nullable AdvancedSpectraImportParameters advanced) throws InterruptedException {
    File[] files = fileNames.stream()
        .map(name -> new File(MzMLImportTest.class.getClassLoader().getResource(name).getFile()))
        .toArray(File[]::new);

    AllSpectralDataImportParameters paramDataImport = new AllSpectralDataImportParameters();
    paramDataImport.setParameter(AllSpectralDataImportParameters.fileNames, files);
    paramDataImport.setParameter(SpectralLibraryImportParameters.dataBaseFiles, new File[0]);
    paramDataImport.setParameter(AllSpectralDataImportParameters.advancedImport, advanced != null);
    if (advanced != null) {
      var advancedP = paramDataImport.getParameter(AllSpectralDataImportParameters.advancedImport);
      advancedP.setEmbeddedParameters(advanced);
    }

    logger.info("Testing data import of mzML and mzXML without advanced parameters");
    TaskResult finished = MZmineTestUtil.callModuleWithTimeout(timeoutSeconds,
        AllSpectralDataImportModule.class, paramDataImport);

    // should have finished by now
    Assertions.assertEquals(TaskResult.FINISHED, finished, () -> switch (finished) {
      case TaskResult.TIMEOUT -> "Timeout during data import. Not finished in time.";
      case TaskResult.ERROR -> "Error during data import.";
      case TaskResult.FINISHED -> "";
    });
  }

  @Nullable
  public static RawDataFile getRawFromProject(final String name) {
    return MZmineCore.getProject().getCurrentRawDataFiles().stream()
        .filter(r -> r.getName().equals(name)).findFirst().orElse(null);
  }

  @NotNull
  public static Stream<RawDataFile> streamDataFiles(List<String> fileNames) {
    return fileNames.stream().map(n -> new File(n).getName())
        .map(MZmineTestUtil::getRawFromProject);
  }
}