/**
 *    Copyright 2009-2019 the original author or authors.
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */
package org.apache.ibatis.cache.decorators;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.SoftReference;
import java.util.Deque;
import java.util.LinkedList;

import org.apache.ibatis.cache.Cache;

/**
 * Soft Reference cache decorator
 * Thanks to Dr. Heinz Kabutz for his guidance here.
   * SoftCache 是根据对象软引用，对象的生命周期，是否被 GC 了，来确实是否缓存这个对象，从而实现了 SoftCache。
 * WeakCache 和 SoftCache 原理一样
 * @author Clinton Begin
 */
public class SoftCache implements Cache {
  // 在 SoftCache 中，最近使用的一部分缓存不会被 GC 回收，这就是通过将其 value 添加到 hardLinksToAvoidGarbageCollection 集合中实现的(即有强引用指向其 value)，
  // 是一个 LinkedList 集合
  private final Deque<Object> hardLinksToAvoidGarbageCollection;
  // ReferenceQueue 引用队列，用于记录已经被 GC 回收的缓存项所对应的 SoftEntry
  private final ReferenceQueue<Object> queueOfGarbageCollectedEntries;
  // 底层 cache 对象，缓存对象 key 是 key， value 是 new SoftEntry(key, value, queueOfGarbageCollectedEntries) 对象
  private final Cache delegate;
  // 强连接个数，默认值是 256
  private int numberOfHardLinks;

  public SoftCache(Cache delegate) {
    this.delegate = delegate;
    this.numberOfHardLinks = 256;
    this.hardLinksToAvoidGarbageCollection = new LinkedList<>();
    this.queueOfGarbageCollectedEntries = new ReferenceQueue<>();
  }

  @Override
  public String getId() {
    return delegate.getId();
  }

  @Override
  public int getSize() {
    removeGarbageCollectedItems();
    return delegate.getSize();
  }


  public void setSize(int size) {
    this.numberOfHardLinks = size;
  }

  @Override
  public void putObject(Object key, Object value) {
    // 清除已经被 GC 回收的缓存项
    removeGarbageCollectedItems();
    // key 是强引用，value 是弱引用，value 关联了 引用队列
    // 添加缓存项
    delegate.putObject(key, new SoftEntry(key, value, queueOfGarbageCollectedEntries));
  }

  /**
   * 从缓存中查找对应的 value，处理被 GC 回收的 value 对应的缓存项，还会更新 hardLinksToAvoidGarbageCollection 集合
   * https://blog.csdn.net/l540675759/article/details/73733763
   * https://www.jianshu.com/p/147793693edc
   * @param key The key
   * @return
   */
  @Override
  public Object getObject(Object key) {
    Object result = null;
    @SuppressWarnings("unchecked") // assumed delegate cache is totally managed by this cache
    // 查询缓存项
    SoftReference<Object> softReference = (SoftReference<Object>) delegate.getObject(key);
    if (softReference != null) { // 检测缓存中是否有对应的缓存项
      result = softReference.get(); // 获取 SoftReference 引用的 value
      if (result == null) { // 已经被回收
        delegate.removeObject(key); // 从缓存中清除对应的缓存项
      } else {    //未被 GC 回收
        // See #586 (and #335) modifications need more than a read lock
        synchronized (hardLinksToAvoidGarbageCollection) {
          // 将缓存项的 value 添加到 hardLinksToAvoidGarbageCollection 集合
          hardLinksToAvoidGarbageCollection.addFirst(result);
          // 超过 numberOfHardLinks，则将最老的缓存项清除，先清除老的缓存项，先进先出
          if (hardLinksToAvoidGarbageCollection.size() > numberOfHardLinks) {
            hardLinksToAvoidGarbageCollection.removeLast();
          }
        }
      }
    }
    return result;
  }

  @Override
  public Object removeObject(Object key) {
    removeGarbageCollectedItems();
    return delegate.removeObject(key);
  }

  @Override
  public void clear() {
    synchronized (hardLinksToAvoidGarbageCollection) {
      hardLinksToAvoidGarbageCollection.clear();
    }
    removeGarbageCollectedItems();
    delegate.clear();
  }

  /**
   * 清除已经被 GC 回收的缓存项
   */
  private void removeGarbageCollectedItems() {
    SoftEntry sv;
    // 遍历 queueOfGarbageCollectedEntries 集合
    while ((sv = (SoftEntry) queueOfGarbageCollectedEntries.poll()) != null) {
      // 将已经被 GC 回收的 value 对象对应的缓存项清除
      delegate.removeObject(sv.key);
    }
  }

  /**
   * SoftCache 中缓存项的 value 是 SoftEntry 对象，SoftEntry 继承了 SoftReference，其中指向 key 的引用是强引用，而指向 value 的引用是软引用。
   */
  private static class SoftEntry extends SoftReference<Object> {
    private final Object key;

    SoftEntry(Object key, Object value, ReferenceQueue<Object> garbageCollectionQueue) {
      // 指向 value 的引用是软引用，且关联了引用队列
      super(value, garbageCollectionQueue);
      this.key = key; //强引用
    }
  }

}
