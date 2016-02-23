/**
 * Copyright 2015 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.spectator.metrics3;

import com.codahale.metrics.Gauge;
import com.netflix.spectator.api.*;

import java.util.Collection;
import java.util.Map;
import java.util.function.ToDoubleFunction;

import static com.netflix.spectator.metrics3.NameUtils.toMetricName;

/** Registry implementation that maps spectator types to the metrics3 library. */
public class MetricsRegistry extends AbstractRegistry {

  private final com.codahale.metrics.MetricRegistry impl;

  /** Create a new instance. */
  public MetricsRegistry() {
    this(Clock.SYSTEM, new com.codahale.metrics.MetricRegistry());
  }

  /** Create a new instance. */
  public MetricsRegistry(Clock clock, com.codahale.metrics.MetricRegistry impl) {
    super(clock);
    this.impl = impl;
  }

  @Override protected Counter newCounter(Id id) {
    final String name = toMetricName(id);
    return new MetricsCounter(clock(), id, impl.meter(name));
  }

  @Override protected DistributionSummary newDistributionSummary(Id id) {
    final String name = toMetricName(id);
    return new MetricsDistributionSummary(clock(), id, impl.histogram(name));
  }

  @Override protected Timer newTimer(Id id) {
    final String name = toMetricName(id);
    return new MetricsTimer(clock(), id, impl.timer(name));
  }

  @Override public <T extends Number> T gauge(Id id, T number) {
    return gauge(id, number, (ToDoubleFunction<T>) value -> number.doubleValue());
  }

  @Override public <T> T gauge(Id id, T obj, ToDoubleFunction<T> f) {
    final String name = toMetricName(id);

    Gauge aggrGauge = this.impl.getGauges().get(name);

    // Wasn't registered already
    if (aggrGauge == null) {
      aggrGauge = new MetricsGaugeAggr();
      this.impl.register(name, aggrGauge);
    }

    if (aggrGauge instanceof MetricsGaugeAggr) {
      final MetricsGauge<T> simpleGauge = new MetricsGauge<>(clock(), id, obj, f);
      register(simpleGauge);
      ((MetricsGaugeAggr) aggrGauge).addGauge(simpleGauge);
    }
    // FIXME throw exceptions with Throwables.propagate if aggrGauge is not a instance of MetricsGaugeAggr

    return obj;
  }

  @Override
  public <T> T gauge(Id id, T obj, ValueFunction<T> f) {
    return gauge(id, obj, ((ToDoubleFunction<T>) f));
  }

  @Override public <T extends Collection<?>> T collectionSize(Id id, T collection) {
    return gauge(id, collection, (ToDoubleFunction<T>) Collection::size);
  }

  @Override public <T extends Map<?, ?>> T mapSize(Id id, T collection) {
    return gauge(id, collection, (ToDoubleFunction<T>) Map::size);
  }
}
