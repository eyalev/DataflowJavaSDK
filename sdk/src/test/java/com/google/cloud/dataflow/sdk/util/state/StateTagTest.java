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
package com.google.cloud.dataflow.sdk.util.state;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import com.google.cloud.dataflow.sdk.coders.BigEndianIntegerCoder;
import com.google.cloud.dataflow.sdk.coders.Coder;
import com.google.cloud.dataflow.sdk.coders.VarIntCoder;
import com.google.cloud.dataflow.sdk.transforms.Max;
import com.google.cloud.dataflow.sdk.transforms.Min;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Tests for {@link StateTag}.
 */
@RunWith(JUnit4.class)
public class StateTagTest {

  @Test
  public void testValueEquality() {
    StateTag<?> fooVarInt1 = StateTags.value("foo", VarIntCoder.of());
    StateTag<?> fooVarInt2 = StateTags.value("foo", VarIntCoder.of());
    StateTag<?> fooBigEndian = StateTags.value("foo", BigEndianIntegerCoder.of());
    StateTag<?> barVarInt = StateTags.value("bar", VarIntCoder.of());

    assertEquals(fooVarInt1, fooVarInt2);
    assertNotEquals(fooVarInt1, fooBigEndian);
    assertNotEquals(fooVarInt1, barVarInt);
  }

  @Test
  public void testBagEquality() {
    StateTag<?> fooVarInt1 = StateTags.bag("foo", VarIntCoder.of());
    StateTag<?> fooVarInt2 = StateTags.bag("foo", VarIntCoder.of());
    StateTag<?> fooBigEndian = StateTags.bag("foo", BigEndianIntegerCoder.of());
    StateTag<?> barVarInt = StateTags.bag("bar", VarIntCoder.of());

    assertEquals(fooVarInt1, fooVarInt2);
    assertNotEquals(fooVarInt1, fooBigEndian);
    assertNotEquals(fooVarInt1, barVarInt);
  }

  @Test
  public void testWatermarkBagEquality() {
    StateTag<?> foo1 = StateTags.watermarkStateInternal("foo");
    StateTag<?> foo2 = StateTags.watermarkStateInternal("foo");
    StateTag<?> bar = StateTags.watermarkStateInternal("bar");

    assertEquals(foo1, foo2);
    assertNotEquals(foo1, bar);
  }

  @Test
  public void testCombiningValueEquality() {
    Coder<Integer> coder1 = VarIntCoder.of();
    Coder<Integer> coder2 = BigEndianIntegerCoder.of();

    StateTag<?> fooCoder1Max1 = StateTags.combiningValue("foo", coder1, new Max.MaxIntegerFn());
    StateTag<?> fooCoder1Max2 = StateTags.combiningValue("foo", coder1, new Max.MaxIntegerFn());
    StateTag<?> fooCoder1Min = StateTags.combiningValue("foo", coder1, new Min.MinIntegerFn());

    StateTag<?> fooCoder2Max = StateTags.combiningValue("foo", coder2, new Max.MaxIntegerFn());
    StateTag<?> barCoder1Max = StateTags.combiningValue("bar", coder1, new Max.MaxIntegerFn());

    // Same name, coder and combineFn
    assertEquals(fooCoder1Max1, fooCoder1Max2);
    // Different combineFn, but we treat them as equal since we only serialize the bits.
    assertEquals(fooCoder1Max1, fooCoder1Min);

    // Different input coder coder.
    assertNotEquals(fooCoder1Max1, fooCoder2Max);

    // These StateTags have different IDs.
    assertNotEquals(fooCoder1Max1, barCoder1Max);
  }
}
