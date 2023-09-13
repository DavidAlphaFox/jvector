/*
 * Copyright DataStax, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.github.jbellis.jvector.example.util;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.file.Path;

import com.github.jbellis.jvector.disk.RandomAccessReader;
import com.indeed.util.mmap.MMapBuffer;

public class MMapBufferReader implements RandomAccessReader {
    private final MMapBuffer buffer;
    private final Path path;
    private long position;
    private byte[] scratch = new byte[0];

    public MMapBufferReader(Path path) throws IOException {
        this.path = path;
        this.buffer = new MMapBuffer(this.path, FileChannel.MapMode.READ_ONLY, ByteOrder.BIG_ENDIAN);
    }

    @Override
    public void seek(long offset) {
        position = offset;
    }

    public int readInt() throws IOException {
        try {
            return buffer.memory().getInt(position);
        } finally {
            position += Integer.BYTES;
        }
    }

    public void readFully(byte[] bytes) throws IOException {
        try {
            buffer.memory().getBytes(position, bytes);
        } finally {
            position += bytes.length;
        }
    }

    @Override
    public void readFully(float[] floats) throws IOException {
        int bytesToRead = floats.length * Float.BYTES;
        if (scratch.length != bytesToRead) {
            scratch = new byte[bytesToRead];
        }
        readFully(scratch);
        ByteBuffer byteBuffer = ByteBuffer.wrap(scratch).order(ByteOrder.BIG_ENDIAN);
        byteBuffer.asFloatBuffer().get(floats);
    }

    @Override
    public void close() throws IOException {
        buffer.close();
    }

    public Path getPath() {
        return path;
    }
}
