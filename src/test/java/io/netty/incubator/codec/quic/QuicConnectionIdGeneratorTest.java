/*
 * Copyright 2020 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.incubator.codec.quic;

import org.junit.Test;

import java.nio.ByteBuffer;
import java.util.concurrent.ThreadLocalRandom;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

public class QuicConnectionIdGeneratorTest {

    @Test
    public void testRandomness() {
        QuicConnectionIdGenerator idGenerator = Quic.randomGenerator();
        ByteBuffer id = idGenerator.newId();
        ByteBuffer id2 = idGenerator.newId();
        assertThat(id.remaining(), greaterThan(0));
        assertThat(id2.remaining(), greaterThan(0));
        assertNotEquals(id, id2);

        id = idGenerator.newId(10);
        id2 = idGenerator.newId(10);
        assertEquals(10, id.remaining());
        assertEquals(10, id2.remaining());
        assertNotEquals(id, id2);

        byte[] input = new byte[1024];
        ThreadLocalRandom.current().nextBytes(input);
        id = idGenerator.newId(ByteBuffer.wrap(input), 10);
        id2 = idGenerator.newId(ByteBuffer.wrap(input), 10);
        assertEquals(10, id.remaining());
        assertEquals(10, id2.remaining());
        assertNotEquals(id, id2);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testThrowsIfInputTooBig() {
        QuicConnectionIdGenerator idGenerator = Quic.randomGenerator();
        idGenerator.newId(Integer.MAX_VALUE);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testThrowsIfInputTooBig2() {
        QuicConnectionIdGenerator idGenerator = Quic.randomGenerator();
        idGenerator.newId(ByteBuffer.wrap(new byte[8]), Integer.MAX_VALUE);
    }
}
