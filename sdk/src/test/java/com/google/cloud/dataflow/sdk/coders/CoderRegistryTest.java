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

package com.google.cloud.dataflow.sdk.coders;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.google.cloud.dataflow.sdk.util.CloudObject;
import com.google.cloud.dataflow.sdk.util.common.ElementByteSizeObserver;
import com.google.cloud.dataflow.sdk.values.KV;
import com.google.cloud.dataflow.sdk.values.TypeDescriptor;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * Tests for CoderRegistry.
 */
@RunWith(JUnit4.class)
@SuppressWarnings("serial")
public class CoderRegistryTest {

  @Rule
  public ExpectedException expectedException = ExpectedException.none();

  public static CoderRegistry getStandardRegistry() {
    CoderRegistry registry = new CoderRegistry();
    registry.registerStandardCoders();
    return registry;
  }

  private static class SerializableClass implements Serializable {
  }

  private static class NotSerializableClass { }

  @Test
  public void testSerializableFallbackCoderProvider() throws Exception {
    CoderRegistry registry = getStandardRegistry();
    registry.setFallbackCoderProvider(SerializableCoder.PROVIDER);
    Coder<?> serializableCoder = registry.getDefaultCoder(SerializableClass.class);

    assertEquals(serializableCoder, SerializableCoder.of(SerializableClass.class));
  }

  @Test
  public void testAvroFallbackCoderProvider() throws Exception {
    CoderRegistry registry = getStandardRegistry();
    registry.setFallbackCoderProvider(AvroCoder.PROVIDER);
    Coder<?> avroCoder = registry.getDefaultCoder(NotSerializableClass.class);

    assertEquals(avroCoder, AvroCoder.of(NotSerializableClass.class));
  }

  @Test
  public void testRegisterInstantiatedCoder() throws Exception {
    CoderRegistry registry = new CoderRegistry();
    registry.registerCoder(MyValue.class, MyValueCoder.of());
    assertEquals(registry.getDefaultCoder(MyValue.class), MyValueCoder.of());
  }

  @SuppressWarnings("rawtypes") // this class exists to fail a test because of its rawtypes
  private class MyListCoder extends DeterministicStandardCoder<List> {
    @Override
    public void encode(List value, OutputStream outStream, Context context)
        throws CoderException, IOException {
    }

    @Override
    public List decode(InputStream inStream, Context context)
        throws CoderException, IOException {
      return Collections.emptyList();
    }

    @Override
    public List<Coder<?>> getCoderArguments() {
      return Collections.emptyList();
    }
  }

  @Test
  public void testRegisterInstantiatedCoderInvalidRawtype() throws Exception {
    expectedException.expect(IllegalArgumentException.class);
    CoderRegistry registry = new CoderRegistry();
    registry.registerCoder(List.class, new MyListCoder());
  }

  @Test
  public void testSimpleDefaultCoder() throws Exception {
    CoderRegistry registry = getStandardRegistry();
    assertEquals(StringUtf8Coder.of(), registry.getDefaultCoder(String.class));
  }

  @Test
  public void testSimpleUnknownDefaultCoder() throws Exception {
    CoderRegistry registry = getStandardRegistry();
    expectedException.expect(CannotProvideCoderException.class);
    registry.getDefaultCoder(UnknownType.class);
  }

  @Test
  public void testParameterizedDefaultCoder() throws Exception {
    CoderRegistry registry = getStandardRegistry();
    TypeDescriptor<List<Integer>> listToken = new TypeDescriptor<List<Integer>>() {};
    assertEquals(ListCoder.of(VarIntCoder.of()),
                 registry.getDefaultCoder(listToken));

    registry.registerCoder(MyValue.class, MyValueCoder.class);
    TypeDescriptor<KV<String, List<MyValue>>> kvToken =
        new TypeDescriptor<KV<String, List<MyValue>>>() {};
    assertEquals(KvCoder.of(StringUtf8Coder.of(),
                            ListCoder.of(MyValueCoder.of())),
                 registry.getDefaultCoder(kvToken));

  }

  @Test
  public void testParameterizedDefaultCoderUnknown() throws Exception {
    CoderRegistry registry = getStandardRegistry();
    TypeDescriptor<List<UnknownType>> listUnknownToken =
        new TypeDescriptor<List<UnknownType>>() {};

    expectedException.expect(CannotProvideCoderException.class);
    registry.getDefaultCoder(listUnknownToken);
  }

  @Test
  public void testParameterizedDefaultCoderWrongMethod() throws Exception {
    CoderRegistry registry = getStandardRegistry();
    expectedException.expect(IllegalArgumentException.class);
    expectedException.expectMessage("new TypeDescriptor<List<...>>(){}");
    registry.getDefaultCoder(List.class);
  }

  @Test
  public void testTypeParameterInferenceForward() throws Exception {
    CoderRegistry registry = getStandardRegistry();
    MyGenericClass<MyValue, List<MyValue>> instance =
        new MyGenericClass<MyValue, List<MyValue>>() {};

    Coder<?> bazCoder = registry.getDefaultCoder(
        instance.getClass(),
        MyGenericClass.class,
        Collections.<Type, Coder<?>>singletonMap(
            TypeDescriptor.of(MyGenericClass.class).getTypeParameter("FooT"), MyValueCoder.of()),
        TypeDescriptor.of(MyGenericClass.class).getTypeParameter("BazT"));

    assertEquals(ListCoder.of(MyValueCoder.of()), bazCoder);
  }

  @Test
  public void testTypeParameterInferenceBackward() throws Exception {
    CoderRegistry registry = getStandardRegistry();
    MyGenericClass<MyValue, List<MyValue>> instance =
        new MyGenericClass<MyValue, List<MyValue>>() {};

    Coder<?> fooCoder = registry.getDefaultCoder(
        instance.getClass(),
        MyGenericClass.class,
        Collections.<Type, Coder<?>>singletonMap(
            TypeDescriptor.of(MyGenericClass.class).getTypeParameter("BazT"),
            ListCoder.of(MyValueCoder.of())),
        TypeDescriptor.of(MyGenericClass.class).getTypeParameter("FooT"));

    assertEquals(MyValueCoder.of(), fooCoder);
  }

  @Test
  public void testGetDefaultCoderFromIntegerValue() throws Exception {
    CoderRegistry registry = getStandardRegistry();
    Integer i = 13;
    Coder<Integer> coder = registry.getDefaultCoder(i);
    assertEquals(VarIntCoder.of(), coder);
  }

  @Test
  public void testGetDefaultCoderFromNullValue() throws Exception {
    CoderRegistry registry = getStandardRegistry();
    assertEquals(VoidCoder.of(), registry.getDefaultCoder((Void) null));
  }

  @Test
  public void testGetDefaultCoderFromKvValue() throws Exception {
    CoderRegistry registry = getStandardRegistry();
    KV<Integer, String> kv = KV.of(13, "hello");
    Coder<KV<Integer, String>> coder = registry.getDefaultCoder(kv);
    assertEquals(KvCoder.of(VarIntCoder.of(), StringUtf8Coder.of()),
        coder);
  }

  @Test
  public void testGetDefaultCoderFromKvNullValue() throws Exception {
    CoderRegistry registry = getStandardRegistry();
    KV<Void, Void> kv = KV.of((Void) null, (Void) null);
    assertEquals(KvCoder.of(VoidCoder.of(), VoidCoder.of()),
        registry.getDefaultCoder(kv));
  }

  @Test
  public void testGetDefaultCoderFromNestedKvValue() throws Exception {
    CoderRegistry registry = getStandardRegistry();
    KV<Integer, KV<Long, KV<String, String>>> kv = KV.of(13, KV.of(17L, KV.of("hello", "goodbye")));
    Coder<KV<Integer, KV<Long, KV<String, String>>>> coder = registry.getDefaultCoder(kv);
    assertEquals(
        KvCoder.of(VarIntCoder.of(),
            KvCoder.of(VarLongCoder.of(),
                KvCoder.of(StringUtf8Coder.of(), StringUtf8Coder.of()))),
        coder);
  }

  @Test
  public void testTypeCompatibility() throws Exception {
    assertTrue(CoderRegistry.isCompatible(
        BigEndianIntegerCoder.of(), Integer.class));
    assertFalse(CoderRegistry.isCompatible(
        BigEndianIntegerCoder.of(), String.class));

    assertFalse(CoderRegistry.isCompatible(
        ListCoder.of(BigEndianIntegerCoder.of()), Integer.class));
    assertTrue(CoderRegistry.isCompatible(
        ListCoder.of(BigEndianIntegerCoder.of()),
        new TypeDescriptor<List<Integer>>() {}.getType()));
    assertFalse(CoderRegistry.isCompatible(
        ListCoder.of(BigEndianIntegerCoder.of()),
        new TypeDescriptor<List<String>>() {}.getType()));
  }

  static class MyGenericClass<FooT, BazT> { }

  static class MyValue { }

  static class MyValueCoder implements Coder<MyValue> {

    private static final MyValueCoder INSTANCE = new MyValueCoder();

    public static MyValueCoder of() {
      return INSTANCE;
    }

    public static List<Object> getInstanceComponents(MyValue exampleValue) {
      return Arrays.asList();
    }

    @Override
    public void encode(MyValue value, OutputStream outStream, Context context)
        throws CoderException, IOException {
    }

    @Override
    public MyValue decode(InputStream inStream, Context context)
        throws CoderException, IOException {
      return new MyValue();
    }

    @Override
    public List<? extends Coder<?>> getCoderArguments() {
      return null;
    }

    @Override
    public CloudObject asCloudObject() {
      return null;
    }

    @Override
    public void verifyDeterministic() { }

    @Override
    public boolean consistentWithEquals() {
      return true;
    }

    @Override
    public Object structuralValue(MyValue value) {
      return value;
    }

    @Override
    public boolean isRegisterByteSizeObserverCheap(MyValue value, Context context) {
      return true;
    }

    @Override
    public void registerByteSizeObserver(
        MyValue value, ElementByteSizeObserver observer, Context context)
        throws Exception {
      observer.update(0L);
    }

    @Override
    public String getEncodingId() {
      return getClass().getName();
    }

    @Override
    public Collection<String> getAllowedEncodings() {
      return Collections.singletonList(getEncodingId());
    }
  }

  static class UnknownType { }
}
