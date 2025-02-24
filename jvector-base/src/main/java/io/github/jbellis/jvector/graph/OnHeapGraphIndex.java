/*
 * All changes to the original code are Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */

/*
 * Original license:
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.github.jbellis.jvector.graph;

import io.github.jbellis.jvector.util.Accountable;
import io.github.jbellis.jvector.util.DenseIntMap;
import io.github.jbellis.jvector.util.RamUsageEstimator;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;
import java.util.stream.IntStream;

/**
 * An {@link GraphIndex} that offers concurrent access; for typical graphs you will get significant
 * speedups in construction and searching as you add threads.
 *
 * <p>To search this graph, you should use a View obtained from {@link #getView()} to perform `seek`
 * and `nextNeighbor` operations.
 */
public final class OnHeapGraphIndex<T> implements GraphIndex<T>, Accountable {

  // the current graph entry node on the top level. -1 if not set
  private final AtomicInteger entryPoint = new AtomicInteger(-1);

  private final DenseIntMap<ConcurrentNeighborSet> nodes;

  // max neighbors/edges per node
  final int nsize0;
  private final BiFunction<Integer, Integer, ConcurrentNeighborSet> neighborFactory;

  OnHeapGraphIndex(
      int M, BiFunction<Integer, Integer, ConcurrentNeighborSet> neighborFactory) {
    this.neighborFactory = neighborFactory;
    this.nsize0 = 2 * M;
    this.nodes = new DenseIntMap<>(1024);
  }

  /**
   * Returns the neighbors connected to the given node.
   *
   * @param node the node whose neighbors are returned, represented as an ordinal on the level 0.
   */
  public ConcurrentNeighborSet getNeighbors(int node) {
    return nodes.get(node);
  }


  @Override
  public int size() {
    return nodes.size();
  }

  /**
   * Add node on the given level with an empty set of neighbors.
   *
   * <p>Nodes can be inserted out of order, but it requires that the nodes preceded by the node
   * inserted out of order are eventually added.
   *
   * <p>Actually populating the neighbors, and establishing bidirectional links, is the
   * responsibility of the caller.
   *
   * <p>It is also the responsibility of the caller to ensure that each node is only added once.
   *
   * @param node the node to add, represented as an ordinal on the level 0.
   * @return the neighbor set for this node
   */
  public ConcurrentNeighborSet addNode(int node) {
    ConcurrentNeighborSet newNeighborSet = neighborFactory.apply(node, maxDegree());
    nodes.put(node, newNeighborSet);
    return newNeighborSet;
  }

  /** must be called after addNode once neighbors are linked in all levels. */
  void markComplete(int node) {
    entryPoint.accumulateAndGet(
        node,
        (oldEntry, newEntry) -> {
          if (oldEntry >= 0) {
            return oldEntry;
          } else {
            return newEntry;
          }
        });
  }

  public void updateEntryNode(int node) {
    entryPoint.set(node);
  }

  @Override
  public int maxDegree() {
    return nsize0;
  }

  int entry() {
    return entryPoint.get();
  }

  @Override
  public NodesIterator getNodes() {
    // We avoid the temptation to optimize this by using ArrayNodesIterator.
    // This is because, while the graph will contain sequential ordinals once the graph is complete,
    // we should not assume that that is the only time it will be called.
    int size = nodes.size();
    var keysInts = IntStream.range(0, size).iterator();
    return new NodesIterator(size) {
      @Override
      public int nextInt() {
        return keysInts.next();
      }

      @Override
      public boolean hasNext() {
        return keysInts.hasNext();
      }
    };
  }

  @Override
  public long ramBytesUsed() {
    // the main graph structure
    long total = (long) size() * RamUsageEstimator.NUM_BYTES_OBJECT_REF;
    long neighborSize = neighborsRamUsed(maxDegree()) * size();
    return total + neighborSize + RamUsageEstimator.NUM_BYTES_ARRAY_HEADER;
  }

  public long ramBytesUsedOneNode(int nodeLevel) {
    var graphBytesUsed =
              neighborsRamUsed(maxDegree())
            + nodeLevel * neighborsRamUsed(maxDegree());
    var clockBytesUsed = Integer.BYTES;
    return graphBytesUsed + clockBytesUsed;
  }

  private static long neighborsRamUsed(int count) {
    long REF_BYTES = RamUsageEstimator.NUM_BYTES_OBJECT_REF;
    long AH_BYTES = RamUsageEstimator.NUM_BYTES_ARRAY_HEADER;
    long neighborSetBytes =
        REF_BYTES // atomicreference
            + Integer.BYTES
            + Integer.BYTES
            + REF_BYTES // NeighborArray
            + AH_BYTES * 2 // NeighborArray internals
            + REF_BYTES * 2
            + Integer.BYTES
            + 1;
    return neighborSetBytes + (long) count * (Integer.BYTES + Float.BYTES);
  }


  @Override
  public String toString() {
    return String.format("OnHeapGraphIndex(size=%d, entryPoint=%d)", size(), entryPoint.get());
  }

  @Override
  public void close() {
    // no-op
  }

  /**
   * Returns a view of the graph that is safe to use concurrently with updates performed on the
   * underlying graph.
   *
   * <p>Multiple Views may be searched concurrently.
   */
  public GraphIndex.View<T> getView() {
    return new ConcurrentGraphIndexView();
  }

  void validateEntryNode() {
    if (size() == 0) {
      return;
    }
    var en = entryPoint.get();
    if (!(en >= 0 && getNeighbors(en) != null)) {
      throw new IllegalStateException("Entry node was incompletely added! " + en);
    }
  }

  private class ConcurrentGraphIndexView implements GraphIndex.View<T> {
    @Override
    public T getVector(int node) {
      throw new UnsupportedOperationException("All searches done with OnHeapGraphIndex should be exact");
    }

    public NodesIterator getNeighborsIterator(int node) {
      return getNeighbors(node).iterator();
    }

    @Override
    public int size() {
      return OnHeapGraphIndex.this.size();
    }

    @Override
    public int entryNode() {
      return (int) entryPoint.get();
    }

    @Override
    public String toString() {
      return "OnHeapGraphIndexView(size=" + size() + ", entryPoint=" + entryPoint.get();
    }

    @Override
    public void close() {
      // no-op
    }
  }
}
