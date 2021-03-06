/*
 * Copyright (C) 2015 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.google.cloud.dataflow.sdk.util;

import com.google.cloud.dataflow.sdk.transforms.windowing.BoundedWindow;
import com.google.cloud.dataflow.sdk.transforms.windowing.DefaultTrigger;
import com.google.cloud.dataflow.sdk.transforms.windowing.PaneInfo;
import com.google.cloud.dataflow.sdk.util.WindowingStrategy.AccumulationMode;
import com.google.cloud.dataflow.sdk.util.common.PeekingReiterator;
import com.google.cloud.dataflow.sdk.util.common.Reiterable;
import com.google.cloud.dataflow.sdk.util.common.Reiterator;
import com.google.cloud.dataflow.sdk.values.KV;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ListMultimap;

import org.joda.time.Instant;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

/**
 * {@link GroupAlsoByWindowsDoFn} that uses reiterators to handle non-merging window functions with
 * the default triggering strategy.
 *
 * @param <K> key type
 * @param <V> value element type
 * @param <W> window type
 */
@SystemDoFnInternal
@SuppressWarnings("serial")
class GroupAlsoByWindowsViaIteratorsDoFn<K, V, W extends BoundedWindow>
    extends GroupAlsoByWindowsDoFn<K, V, Iterable<V>, W> {

  public static boolean isSupported(WindowingStrategy<?, ?> strategy) {
    if (!strategy.getWindowFn().isNonMerging()) {
      return false;
    }

    // TODO: Add support for other triggers.
    if (!(strategy.getTrigger().getSpec() instanceof DefaultTrigger)) {
      return false;
    }

    // Right now, we support ACCUMULATING_FIRED_PANES because it is the same as
    // DISCARDING_FIRED_PANES. In Batch mode there is no late data so the default
    // trigger (after watermark) will only fire once.
    if (!strategy.getMode().equals(AccumulationMode.DISCARDING_FIRED_PANES)
        && !strategy.getMode().equals(AccumulationMode.ACCUMULATING_FIRED_PANES)) {
      return false;
    }

    return true;
  }

  @Override
  public void processElement(ProcessContext c) throws Exception {
    K key = c.element().getKey();
    Iterable<WindowedValue<V>> value = c.element().getValue();
    PeekingReiterator<WindowedValue<V>> iterator;

    if (value instanceof Collection) {
      iterator = new PeekingReiterator<>(new ListReiterator<WindowedValue<V>>(
          new ArrayList<WindowedValue<V>>((Collection<WindowedValue<V>>) value), 0));
    } else if (value instanceof Reiterable) {
      iterator = new PeekingReiterator<>(((Reiterable<WindowedValue<V>>) value).iterator());
    } else {
      throw new IllegalArgumentException(
          "Input to GroupAlsoByWindowsDoFn must be a Collection or Reiterable");
    }

    // This ListMultimap is a map of window maxTimestamps to the list of active
    // windows with that maxTimestamp.
    ListMultimap<Instant, BoundedWindow> windows = ArrayListMultimap.create();

    while (iterator.hasNext()) {
      WindowedValue<V> e = iterator.peek();
      for (BoundedWindow window : e.getWindows()) {
        // If this window is not already in the active set, emit a new WindowReiterable
        // corresponding to this window, starting at this element in the input Reiterable.
        if (!windows.containsEntry(window.maxTimestamp(), window)) {
          // Iterating through the WindowReiterable may advance iterator as an optimization
          // for as long as it detects that there are no new windows.
          windows.put(window.maxTimestamp(), window);
          c.windowingInternals().outputWindowedValue(
              KV.of(key, (Iterable<V>) new WindowReiterable<V>(iterator, window)),
              e.getTimestamp(),
              Arrays.asList(window),
              PaneInfo.ON_TIME_AND_ONLY_FIRING);
        }
      }
      // Copy the iterator in case the next DoFn cached its version of the iterator instead
      // of immediately iterating through it.
      // And, only advance the iterator if the consuming operation hasn't done so.
      iterator = iterator.copy();
      if (iterator.hasNext() && iterator.peek() == e) {
        iterator.next();
      }

      // Remove all windows with maxTimestamp behind the current timestamp.
      Iterator<Instant> windowIterator = windows.keys().iterator();
      while (windowIterator.hasNext()
          && windowIterator.next().isBefore(e.getTimestamp())) {
        windowIterator.remove();
      }
    }
  }

  /**
   * {@link Reiterable} representing a view of all elements in a base
   * {@link Reiterator} that are in a given window.
   */
  private static class WindowReiterable<V> implements Reiterable<V> {
    private PeekingReiterator<WindowedValue<V>> baseIterator;
    private BoundedWindow window;

    public WindowReiterable(
        PeekingReiterator<WindowedValue<V>> baseIterator, BoundedWindow window) {
      this.baseIterator = baseIterator;
      this.window = window;
    }

    @Override
    public Reiterator<V> iterator() {
      // We don't copy the baseIterator when creating the first WindowReiterator
      // so that the WindowReiterator can advance the baseIterator.  We have to
      // make a copy afterwards so that future calls to iterator() will start
      // at the right spot.
      Reiterator<V> result = new WindowReiterator<V>(baseIterator, window);
      baseIterator = baseIterator.copy();
      return result;
    }

    @Override
    public String toString() {
      StringBuilder result = new StringBuilder();
      result.append("WR{");
      for (V v : this) {
        result.append(v.toString()).append(',');
      }
      result.append("}");
      return result.toString();
    }
  }

  /**
   * The {@link Reiterator} used by
   * {@link com.google.cloud.dataflow.sdk.util.GroupAlsoByWindowsViaIteratorsDoFn.WindowReiterable}.
   */
  private static class WindowReiterator<V> implements Reiterator<V> {
    private PeekingReiterator<WindowedValue<V>> iterator;
    private BoundedWindow window;

    public WindowReiterator(PeekingReiterator<WindowedValue<V>> iterator, BoundedWindow window) {
      this.iterator = iterator;
      this.window = window;
    }

    @Override
    public Reiterator<V> copy() {
      return new WindowReiterator<V>(iterator.copy(), window);
    }

    @Override
    public boolean hasNext() {
      skipToValidElement();
      return (iterator.hasNext() && iterator.peek().getWindows().contains(window));
    }

    @Override
    public V next() {
      skipToValidElement();
      WindowedValue<V> next = iterator.next();
      if (!next.getWindows().contains(window)) {
        throw new NoSuchElementException("No next item in window");
      }
      return next.getValue();
    }

    @Override
    public void remove() {
      throw new UnsupportedOperationException();
    }

    /**
     * Moves the underlying iterator forward until it either points to the next
     * element in the correct window, or is past the end of the window.
     */
    private void skipToValidElement() {
      while (iterator.hasNext()) {
        WindowedValue<V> peek = iterator.peek();
        if (peek.getTimestamp().isAfter(window.maxTimestamp())) {
          // We are past the end of this window, so there can't be any more
          // elements in this iterator.
          break;
        }
        if (!(peek.getWindows().size() == 1 && peek.getWindows().contains(window))) {
          // We have reached new windows; we need to copy the iterator so we don't
          // keep advancing the outer loop in processElement.
          iterator = iterator.copy();
        }
        if (!peek.getWindows().contains(window)) {
          // The next element is not in the right window: skip it.
          iterator.next();
        } else {
          // The next element is in the right window.
          break;
        }
      }
    }
  }

  /**
   * {@link Reiterator} that wraps a {@link List}.
   */
  private static class ListReiterator<T> implements Reiterator<T> {
    private List<T> list;
    private int index;

    public ListReiterator(List<T> list, int index) {
      this.list = list;
      this.index = index;
    }

    @Override
    public T next() {
      return list.get(index++);
    }

    @Override
    public boolean hasNext() {
      return index < list.size();
    }

    @Override
    public void remove() {
      throw new UnsupportedOperationException();
    }

    @Override
    public Reiterator<T> copy() {
      return new ListReiterator<T>(list, index);
    }
  }
}
