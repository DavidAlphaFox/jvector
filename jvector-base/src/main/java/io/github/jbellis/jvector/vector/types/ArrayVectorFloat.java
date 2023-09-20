package io.github.jbellis.jvector.vector.types;

import java.util.Arrays;

import io.github.jbellis.jvector.util.RamUsageEstimator;
import io.github.jbellis.jvector.vector.VectorEncoding;

final public class ArrayVectorFloat implements VectorFloat<float[]>
{
    private final float[] data;

    ArrayVectorFloat(int length)
    {
        this.data = new float[length];
    }

    ArrayVectorFloat(float[] data)
    {
        this.data = data;
    }

    @Override
    public VectorEncoding type()
    {
        return VectorEncoding.FLOAT32;
    }

    @Override
    public float[] get()
    {
        return data;
    }

    @Override
    public Float get(int n) {
        return data[n];
    }

    @Override
    public void set(int n, Float value) {
        data[n] = value;
    }

    @Override
    public int length()
    {
        return data.length;
    }

    @Override
    public int offset()
    {
        return 0;
    }

    @Override
    public VectorFloat<float[]> copy()
    {
        return new ArrayVectorFloat(Arrays.copyOf(data, data.length));
    }

    @Override
    public void copyFrom(VectorFloat<?> src, int srcOffset, int destOffset, int length)
    {
        System.arraycopy(src.array(), srcOffset, data, destOffset, length);
    }

    @Override
    public float[] array() {
        return data;
    }

    @Override
    public long ramBytesUsed()
    {
        return RamUsageEstimator.sizeOf(data) + RamUsageEstimator.shallowSizeOfInstance(ArrayVectorFloat.class);
    }
}

