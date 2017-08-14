/**
 * Copyright (C) 2017 Red Hat, Inc.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.atlasmap.converters;

import io.atlasmap.spi.AtlasConversionConcern;
import io.atlasmap.api.AtlasConversionException;
import io.atlasmap.converters.FloatConverter;
import io.atlasmap.spi.AtlasConversionInfo;
import io.atlasmap.spi.AtlasPrimitiveConverter;
import io.atlasmap.v2.FieldType;

import org.junit.Test;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;

import static org.junit.Assert.*;

public class FloatConverterTest {
    private AtlasPrimitiveConverter<Float> converter = new FloatConverter();

    @Test
    public void convertToBoolean() throws Exception {
        Float df = 0.0f;
        Float dt = 1.0f;

        Boolean b = converter.convertToBoolean(dt);
        assertNotNull(b);
        assertTrue(b);

        b = converter.convertToBoolean(df);
        assertNotNull(b);
        assertFalse(b);

    }

    @Test
    public void convertToBoolean_Null() throws Exception {
        Float df = null;
        Boolean b = converter.convertToBoolean(df);
        assertNull(b);
    }

    @Test(expected = AtlasConversionException.class)
    public void convertToBoolean_Exception() throws Exception {
        Float dt = -1.0f;
        Boolean b = converter.convertToBoolean(dt);
    }

    @Test(expected = AtlasConversionException.class)
    public void convertToByte() throws Exception {
        Float df = 0.0f;
        Byte b = converter.convertToByte(df);
    }

    @Test
    public void convertToByte_Null() throws Exception {
        assertNull(converter.convertToByte(null));
    }

    @Test
    public void convertToCharacter() throws Exception {
        Float df = 0.0f;
        Character c = converter.convertToCharacter(df);
        assertNotNull(c);
        assertEquals(0, c.charValue());
    }

    @Test
    public void convertToCharacter_Null() throws Exception {
        Float df = null;
        Character c = converter.convertToCharacter(df);
        assertNull(c);
    }

    @Test(expected = AtlasConversionException.class)
    public void convertToCharacter_MAX() throws Exception {
        Float df = Float.MAX_VALUE;
        Character c = converter.convertToCharacter(df);
    }

    @Test(expected = AtlasConversionException.class)
    public void convertToCharacter_MIN() throws Exception {
        Float df = -1.00f;
        Character c = converter.convertToCharacter(df);
    }

    @Test
    public void convertToDouble() throws Exception {
        Float df = 0.0f;
        Double d = converter.convertToDouble(df);
        assertNotNull(d);
        assertNotSame(df, d);
        assertEquals(0.0, d.floatValue(), 0.0);

        df = 1.0f;
        d = converter.convertToDouble(df);
        assertNotNull(d);
        assertNotSame(df, d);
        assertEquals(1.0, d.floatValue(), 0.0);
    }

    @Test
    public void convertToDouble_Null() throws Exception {
        Float df = null;
        Double d = converter.convertToDouble(df);
        assertNull(d);
    }

    @Test
    public void convertToFloat() throws Exception {
        Float df = 0.0f;
        Float d = converter.convertToFloat(df);
        assertNotNull(d);
        assertNotSame(df, d);
        assertEquals(0.0, d, 0.0);
    }

    @Test
    public void convertToFloat_Null() throws Exception {
        Float df = null;
        Float d = converter.convertToFloat(df);
        assertNull(d);
    }

    @Test
    public void convertToInteger() throws Exception {
        Float df = 0.15f;
        Integer i = converter.convertToInteger(df);
        assertNotNull(i);
        assertEquals(0, i, 0.0);
    }

    @Test
    public void convertToInteger_Null() throws Exception {
        Float df = null;
        Integer i = converter.convertToInteger(df);
        assertNull(i);
    }

    @Test(expected = AtlasConversionException.class)
    public void convertToInteger_ExceptionMAX() throws Exception {
        Float df = Float.MAX_VALUE;
        Integer i = converter.convertToInteger(df);
    }

    @Test
    public void convertToLong() throws Exception {
        Float df = 0.0f;
        Long l = converter.convertToLong(df);
        assertNotNull(l);
        assertEquals(0, l, 0.0);
    }

    @Test
    public void convertToLong_Null() throws Exception {
        Float df = null;
        Long l = converter.convertToLong(df);
        assertNull(l);
    }

    @Test(expected = AtlasConversionException.class)
    public void convertToLong_ExceptionMAX() throws Exception {
        Float df = Float.MAX_VALUE;
        Long l = converter.convertToLong(df);
    }

    @Test
    public void convertToShort() throws Exception {
        Float df = 0.0f;
        Short s = converter.convertToShort(df);
        assertNotNull(s);
        assertEquals(0, s, 0.0);
    }

    @Test
    public void convertToShort_Null() throws Exception {
        Float df = null;
        Short s = converter.convertToShort(df);
        assertNull(s);
    }

    @Test(expected = AtlasConversionException.class)
    public void convertToShort_ExceptionMAX() throws Exception {
        Float df = Float.MAX_VALUE;
        Short s = converter.convertToShort(df);
    }

    @Test
    public void convertToString() throws Exception {
        Float df = 0.0f;
        String s = converter.convertToString(df);
        assertNotNull(s);
        assertTrue("0.0".equals(s));
    }

    @Test
    public void convertToString_Null() throws Exception {
        Float df = null;
        String s = converter.convertToString(df);
        assertNull(s);
    }

    @Test
    public void checkAnnotations() throws Exception {
        Class aClass = FloatConverter.class;
        Method[] methods = aClass.getMethods();
        for (Method method : methods) {
            if (method.isSynthetic()) {
                // We are running in Eclipse or jacoco
                continue;
            }
            if (method.getName().startsWith("convert")) {
                Annotation[] annotations = method.getDeclaredAnnotations();
                assertNotNull(annotations);
                assertTrue(annotations.length > 0);
                for (Annotation annotation : annotations) {
                    assertTrue(AtlasConversionInfo.class.isAssignableFrom(annotation.annotationType()));
                    AtlasConversionInfo atlasConversionInfo = (AtlasConversionInfo) annotation;
                    assertNotNull(atlasConversionInfo.sourceType());
                    assertTrue(atlasConversionInfo.sourceType().compareTo(FieldType.FLOAT) == 0);
                    assertNotNull(atlasConversionInfo.targetType());
                    for (AtlasConversionConcern atlasConversionConcern : atlasConversionInfo.concerns()) {
                        assertNotNull(atlasConversionConcern.getMessage());
                        assertNotNull(atlasConversionConcern.value());
                    }
                }
            }
        }
    }
}