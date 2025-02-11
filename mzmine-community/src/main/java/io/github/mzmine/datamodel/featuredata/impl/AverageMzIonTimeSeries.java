package io.github.mzmine.datamodel.featuredata.impl;

import io.github.mzmine.datamodel.featuredata.IonTimeSeries;

public record AverageMzIonTimeSeries(IonTimeSeries<?> series, double mz, Float height) {

}
