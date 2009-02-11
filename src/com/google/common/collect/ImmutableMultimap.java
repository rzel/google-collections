/*
 * Copyright (C) 2008 Google Inc.
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

package com.google.common.collect;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.base.Nullable;
import com.google.common.collect.Serialization.FieldSetter;

import java.io.IOException;
import java.io.InvalidObjectException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * An immutable {@link ListMultimap} with reliable user-specified key and value
 * iteration order. Does not permit null keys or values.
 *
 * <p>Unlike {@link Multimaps#unmodifiableListMultimap(ListMultimap)}, which is
 * a <i>view</i> of a separate map which can still change, an instance of
 * {@code ImmutableMultimap} contains its own data and will <i>never</i> change.
 * {@code ImmutableMultimap} is convenient for {@code public static final}
 * multimaps ("constant multimaps") and also lets you easily make a "defensive
 * copy" of a multimap provided to your class by a caller.
 *
 * <p><b>Note</b>: Although this class is not final, it cannot be subclassed as
 * it has no public or protected constructors. Thus, instances of this class are
 * guaranteed to be immutable.
 *
 * @author Jared Levy
 */
public class ImmutableMultimap<K, V>
    implements ListMultimap<K, V>, Serializable {

  private static ImmutableMultimap<Object, Object> EMPTY_MULTIMAP
      = new EmptyMultimap();

  private static class EmptyMultimap extends ImmutableMultimap<Object, Object> {
    EmptyMultimap() {
      super(ImmutableMap.<Object, ImmutableList<Object>>of(), 0);
    }
    @Override public boolean isEmpty() {
      return true;
    }
    Object readResolve() {
      return EMPTY_MULTIMAP; // preserve singleton property
    }
    private static final long serialVersionUID = 0;
  }

  /** Returns the empty multimap. */
  // Casting is safe because the multimap will never hold any elements.
  @SuppressWarnings("unchecked")
  public static <K, V> ImmutableMultimap<K, V> empty() {
    return (ImmutableMultimap<K, V>) EMPTY_MULTIMAP;
  }

  /**
   * Returns a new builder. The generated builder is equivalent to the builder
   * created by the {@link Builder} constructor.
   */
  public static <K, V> Builder<K, V> builder() {
    return new Builder<K, V>();
  }

  /**
   * Multimap for {@link ImmutableMultimap.Builder} that maintains key and value
   * orderings, allows duplicate values, and performs better than
   * {@link LinkedListMultimap}.
   */
  private static class BuilderMultimap<K, V> extends StandardMultimap<K, V> {
    BuilderMultimap() {
      super(new LinkedHashMap<K, Collection<V>>());
    }
    @Override Collection<V> createCollection() {
      return Lists.newArrayList();
    }
    private static final long serialVersionUID = 0;
  }

  /**
   * A builder for creating immutable multimap instances, especially
   * {@code public static final} multimaps ("constant multimaps"). Example:
   * <pre>   {@code
   *
   *   static final Multimap<String,Integer> STRING_TO_INTEGER_MULTIMAP =
   *       new ImmutableMultimap.Builder<String, Integer>()
   *           .put("one", 1)
   *           .putAll("several", 1, 2, 3)
   *           .putAll("many", 1, 2, 3, 4, 5)
   *           .build();}</pre>
   *
   * <p>Builder instances can be reused - it is safe to call {@link #build}
   * multiple times to build multiple multimaps in series. Each multimap
   * contains the key-value mappings in the previously created multimaps.
   */
  public static class Builder<K, V> {
    private final Multimap<K, V> builderMultimap = new BuilderMultimap<K, V>();

    /**
     * Creates a new builder. The returned builder is equivalent to the builder
     * generated by {@link ImmutableMultimap#builder}.
     */
    public Builder() {}

    /**
     * Adds a key-value mapping to the built multimap.
     */
    public Builder<K, V> put(K key, V value) {
      builderMultimap.put(checkNotNull(key), checkNotNull(value));
      return this;
    }

    /**
     * Stores a collection of values with the same key in the built multimap.
     *
     * @throws NullPointerException if {@code key}, {@code values}, or any
     *     element in {@code values} is null. The builder is left in an invalid
     *     state.
     */
    public Builder<K, V> putAll(K key, Iterable<? extends V> values) {
      Collection<V> valueList = builderMultimap.get(checkNotNull(key));
      for (V value : values) {
        valueList.add(checkNotNull(value));
      }
      return this;
    }

    /**
     * Stores an array of values with the same key in the built multimap.
     *
     * @throws NullPointerException if the key or any value is null. If a later
     *     value is null, earlier values may be added to the builder.
     */
    public Builder<K, V> putAll(K key, V... values) {
      Collection<V> valueList = builderMultimap.get(checkNotNull(key));
      for (V value : values) {
        valueList.add(checkNotNull(value));
      }
      return this;
    }

    public ImmutableMultimap<K, V> build() {
      return copyOf(builderMultimap);
    }
  }

  /**
   * Returns an immutable multimap containing the same mappings as
   * {@code multimap}. The generated multimap's key and value orderings
   * correspond to the iteration ordering of the {@code multimap.asMap()} view.
   *
   * <p><b>Note:</b> Despite what the method name suggests, if
   * {@code multimap} is an {@code ImmutableMultimap}, no copy will actually be
   * performed, and the given map itself will be returned.
   *
   * @throws NullPointerException if any key or value in {@code multimap} is
   *     null
   */
  public static <K, V> ImmutableMultimap<K, V> copyOf(
      Multimap<? extends K, ? extends V> multimap) {
    if (multimap.isEmpty()) {
      return empty();
    }

    if (multimap instanceof ImmutableMultimap) {
      @SuppressWarnings("unchecked") // safe since multimap is not writable
      ImmutableMultimap<K, V> kvMultimap = (ImmutableMultimap<K, V>) multimap;
      return kvMultimap;
    }

    ImmutableMap.Builder<K, ImmutableList<V>> builder = ImmutableMap.builder();
    int size = 0;

    for (Map.Entry<? extends K, ? extends Collection<? extends V>> entry
        : multimap.asMap().entrySet()) {
      ImmutableList<V> list = ImmutableList.copyOf(entry.getValue());
      if (!list.isEmpty()) {
        builder.put(entry.getKey(), list);
        size += list.size();
      }
    }

    return new ImmutableMultimap<K, V>(builder.build(), size);
  }

  private final transient ImmutableMap<K, ImmutableList<V>> map;
  private final transient int size;

  // These constants allow the deserialization code to set final fields. This
  // holder class makes sure they are not initialized unless an instance is
  // deserialized.
  private static class FieldSettersHolder {
    @SuppressWarnings("unchecked") // ImmutableMultimap raw type
    static final FieldSetter<ImmutableMultimap> MAP_FIELD_SETTER
        = Serialization.getFieldSetter(ImmutableMultimap.class, "map");
    @SuppressWarnings("unchecked") // ImmutableMultimap raw type
    static final FieldSetter<ImmutableMultimap> SIZE_FIELD_SETTER
        = Serialization.getFieldSetter(ImmutableMultimap.class, "size");
  }

  private ImmutableMultimap(ImmutableMap<K, ImmutableList<V>> map, int size) {
    this.map = map;
    this.size = size;
  }

  // mutators (not supported)

  /**
   * Guaranteed to throw an exception and leave the multimap unmodified.
   *
   * @throws UnsupportedOperationException always
   */
  public List<V> removeAll(Object key) {
    throw new UnsupportedOperationException();
  }

  /**
   * Guaranteed to throw an exception and leave the multimap unmodified.
   *
   * @throws UnsupportedOperationException always
   */
  public List<V> replaceValues(K key, Iterable<? extends V> values) {
    throw new UnsupportedOperationException();
  }

  /**
   * Guaranteed to throw an exception and leave the multimap unmodified.
   *
   * @throws UnsupportedOperationException always
   */
  public void clear() {
    throw new UnsupportedOperationException();
  }

  /**
   * Guaranteed to throw an exception and leave the multimap unmodified.
   *
   * @throws UnsupportedOperationException always
   */
  public boolean put(K key, V value) {
    throw new UnsupportedOperationException();
  }

  /**
   * Guaranteed to throw an exception and leave the multimap unmodified.
   *
   * @throws UnsupportedOperationException always
   */
  public boolean putAll(K key, Iterable<? extends V> values) {
    throw new UnsupportedOperationException();
  }

  /**
   * Guaranteed to throw an exception and leave the multimap unmodified.
   *
   * @throws UnsupportedOperationException always
   */
  public boolean putAll(Multimap<? extends K, ? extends V> multimap) {
    throw new UnsupportedOperationException();
  }

  /**
   * Guaranteed to throw an exception and leave the multimap unmodified.
   *
   * @throws UnsupportedOperationException always
   */
  public boolean remove(Object key, Object value) {
    throw new UnsupportedOperationException();
  }

  // accessors

  public boolean containsEntry(@Nullable Object key, @Nullable Object value) {
    Collection<V> valueList = map.get(key);
    return (valueList != null) && valueList.contains(value);
  }

  public boolean containsKey(@Nullable Object key) {
    return map.containsKey(key);
  }

  public boolean containsValue(@Nullable Object value) {
    for (Collection<V> valueList : map.values()) {
      if (valueList.contains(value)) {
        return true;
      }
    }
    return false;
  }

  public boolean isEmpty() {
    return false;
  }

  public int size() {
    return size;
  }

  @Override public boolean equals(@Nullable Object object) {
    if (object == this) {
      return true;
    }
    if (object instanceof Multimap) {
      Multimap<?, ?> that = (Multimap<?, ?>) object;
      return this.map.equals(that.asMap());
    }
    return false;
  }

  @Override public int hashCode() {
    return map.hashCode();
  }

  @Override public String toString() {
    return map.toString();
  }

  // views

  /**
   * Returns an immutable list of the values for the given key.  If no mappings
   * in the multimap have the provided key, an empty immutable list is returned.
   * The values are in the same order as the parameters used to build this
   * multimap.
   */
  public ImmutableList<V> get(@Nullable K key) {
    ImmutableList<V> list = map.get(key);
    return (list == null) ? ImmutableList.<V>of() : list;
  }

  /**
   * Returns an immutable set of the distinct keys in this multimap. These keys
   * are ordered according to when they first appeared during the construction
   * of this multimap.
   */
  public ImmutableSet<K> keySet() {
    return map.keySet();
  }

  /**
   * Returns an immutable map that associates each key with its corresponding
   * values in the multimap. Though the method signature doesn't say so
   * explicitly, the returned map has {@link ImmutableList} values.
   */
  @SuppressWarnings("unchecked") // a widening cast
  public ImmutableMap<K, Collection<V>> asMap() {
    return (ImmutableMap) map;
  }

  private transient ImmutableCollection<Map.Entry<K, V>> entries;

  /**
   * Returns an immutable collection of all key-value pairs in the multimap. Its
   * iterator traverses the values for the first key, the values for the second
   * key, and so on.
   */
  public ImmutableCollection<Map.Entry<K, V>> entries() {
    ImmutableCollection<Map.Entry<K, V>> result = entries;
    return (result == null) ? (entries = new Entries<K, V>(this)) : result;
  }

  private static class Entries<K, V>
      extends ImmutableCollection<Map.Entry<K, V>> {
    final ImmutableMultimap<K, V> multimap;

    Entries(ImmutableMultimap<K, V> multimap) {
      this.multimap = multimap;
    }

    @Override public UnmodifiableIterator<Map.Entry<K, V>> iterator() {
      final Iterator<Map.Entry<K, ImmutableList<V>>> mapIterator
          = multimap.map.entrySet().iterator();

      return new UnmodifiableIterator<Map.Entry<K, V>>() {
        Map.Entry<K, ImmutableList<V>> mapEntry;
        int index;

        public boolean hasNext() {
          return ((mapEntry != null) && (index < mapEntry.getValue().size()))
              || mapIterator.hasNext();
        }

        public Map.Entry<K, V> next() {
          if ((mapEntry == null) || (index >= mapEntry.getValue().size())) {
            mapEntry = mapIterator.next();
            index = 0;
          }
          V value = mapEntry.getValue().get(index);
          index++;
          return Maps.immutableEntry(mapEntry.getKey(), value);
        }
      };
    }

    public int size() {
      return multimap.size();
    }

    @Override public boolean contains(Object object) {
      if (object instanceof Map.Entry) {
        Map.Entry<?, ?> entry = (Map.Entry<?, ?>) object;
        return multimap.containsEntry(entry.getKey(), entry.getValue());
      }
      return false;
    }

    private static final long serialVersionUID = 0;
  }

  private transient ImmutableMultiset<K> keys;

  /**
   * Returns a collection, which may contain duplicates, of all keys. The number
   * of times of key appears in the returned multiset equals the number of
   * mappings the key has in the multimap. Duplicate keys appear consecutively
   * in the multiset's iteration order.
   */
  public ImmutableMultiset<K> keys() {
    ImmutableMultiset<K> result = keys;
    return (result == null)
        ? (keys = new ImmutableMultiset<K>(new CountMap<K, V>(this), size))
        : result;
  }

  /*
   * Map from key to value count, used to create the keys() multiset.
   * Methods that ImmutableMultiset doesn't require are unsupported.
   */
  private static class CountMap<K, V> extends ImmutableMap<K, Integer> {
    final ImmutableMultimap<K, V> multimap;

    CountMap(ImmutableMultimap<K, V> multimap) {
      this.multimap = multimap;
    }

    @Override public boolean containsKey(Object key) {
      return multimap.containsKey(key);
    }

    @Override public boolean containsValue(Object value) {
      throw new UnsupportedOperationException();
    }

    @Override public Integer get(Object key) {
      Collection<?> valueList = multimap.map.get(key);
      return (valueList == null) ? 0 : valueList.size();
    }

    @Override public ImmutableSet<K> keySet() {
      return multimap.keySet();
    }

    @Override public ImmutableCollection<Integer> values() {
      throw new UnsupportedOperationException();
    }

    public boolean isEmpty() {
      return multimap.isEmpty();
    }

    public int size() {
      return multimap.map.size();
    }

    transient ImmutableSet<Entry<K, Integer>> entrySet;

    @Override public ImmutableSet<Entry<K, Integer>> entrySet() {
      ImmutableSet<Entry<K, Integer>> result = entrySet;
      return (result == null)
          ? entrySet = new EntrySet<K, V>(multimap) : result;
    }

    private static class EntrySet<K, V>
        extends ImmutableSet<Entry<K, Integer>> {
      final ImmutableMultimap<K, V> multimap;

      EntrySet(ImmutableMultimap<K, V> multimap) {
        this.multimap = multimap;
      }

      @Override public UnmodifiableIterator<Entry<K, Integer>> iterator() {
        final Iterator<Entry<K, ImmutableList<V>>> mapIterator
            = multimap.map.entrySet().iterator();
        return new UnmodifiableIterator<Entry<K, Integer>>() {
          public boolean hasNext() {
            return mapIterator.hasNext();
          }
          public Entry<K, Integer> next() {
            Entry<K, ImmutableList<V>> entry = mapIterator.next();
            return Maps.immutableEntry(entry.getKey(), entry.getValue().size());
          }
        };
      }

      public int size() {
        return multimap.map.size();
      }

      private static final long serialVersionUID = 0;
    }

    private static final long serialVersionUID = 0;
  }

  private transient ImmutableCollection<V> values;

  /**
   * Returns an immutable collection of the values in this multimap. Its
   * iterator traverses the values for the first key, the values for the second
   * key, and so on.
   */
  public ImmutableCollection<V> values() {
    ImmutableCollection<V> v = values;
    return (v == null) ? (values = new Values<V>(this)) : v;
  }

  private static class Values<V> extends ImmutableCollection<V>  {
    final Multimap<?, V> multimap;

    Values(Multimap<?, V> multimap) {
      this.multimap = multimap;
    }

    @Override public UnmodifiableIterator<V> iterator() {
      final Iterator<? extends Map.Entry<?, V>> entryIterator
          = multimap.entries().iterator();
      return new UnmodifiableIterator<V>() {
        public boolean hasNext() {
          return entryIterator.hasNext();
        }
        public V next() {
          return entryIterator.next().getValue();
        }
      };
    }

    public int size() {
      return multimap.size();
    }

    private static final long serialVersionUID = 0;
  }

  /**
   * @serialData number of distinct keys, and then for each distinct key: the
   *     key, the number of values for that key, and the key's values
   */
  private void writeObject(ObjectOutputStream stream) throws IOException {
    stream.defaultWriteObject();
    Serialization.writeMultimap(this, stream);
  }

  private void readObject(ObjectInputStream stream)
      throws IOException, ClassNotFoundException {
    stream.defaultReadObject();
    int keyCount = stream.readInt();
    if (keyCount < 0) {
      throw new InvalidObjectException("Invalid key count " + keyCount);
    }
    ImmutableMap.Builder<Object, ImmutableList<Object>> builder
        = ImmutableMap.builder();
    int tmpSize = 0;

    for (int i = 0; i < keyCount; i++) {
      Object key = stream.readObject();
      int valueCount = stream.readInt();
      if (valueCount <= 0) {
        throw new InvalidObjectException("Invalid value count " + valueCount);
      }

      Object[] array = new Object[valueCount];
      for (int j = 0; j < valueCount; j++) {
        array[j] = stream.readObject();
        tmpSize += valueCount;
      }
      builder.put(key, ImmutableList.of(array));
    }

    ImmutableMap<Object, ImmutableList<Object>> tmpMap;
    try {
      tmpMap = builder.build();
    } catch (IllegalArgumentException e) {
      throw (InvalidObjectException)
          new InvalidObjectException(e.getMessage()).initCause(e);
    }

    FieldSettersHolder.MAP_FIELD_SETTER.set(this, tmpMap);
    FieldSettersHolder.SIZE_FIELD_SETTER.set(this, tmpSize);
  }

  private static final long serialVersionUID = 0;
}
