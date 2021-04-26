/**
 *    Copyright 2009-2020 the original author or authors.
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
package org.apache.ibatis.cache;

import java.util.HashMap;
import java.util.Map;

import org.apache.ibatis.cache.decorators.TransactionalCache;

/**
 * TransactionalCacheManager 用于管理 CachingExecutor 使用的二级缓存对象，其中只定义了一个 transactionalCaches，
 * 它的 key 是对应的 CachingExecutor 使用的二级缓存对象，value 是相应的 TransactionalCache 对象，在该 TransactionalCache 中封装了对应的二级缓存对象，也就是这里的 key。
 * @author Clinton Begin
 */
public class TransactionalCacheManager {
  // cache 为：MappedStatement.cache 作为 key，实现了单一性，作为多个 CachingExecutor 多个 TransactionalCacheManager 的多个 transactionalCaches；而 key 是唯一的。
  private final Map<Cache, TransactionalCache> transactionalCaches = new HashMap<>();

  /**
   * 调用指定二级缓存对应的 TransactionalCache 对象对应的方法
   * @param cache
   */
  public void clear(Cache cache) {
    getTransactionalCache(cache).clear();
  }

  /**
   * 调用指定二级缓存对应的 TransactionalCache 对象对应的方法
   * @param cache
   */
  public Object getObject(Cache cache, CacheKey key) {
    return getTransactionalCache(cache).getObject(key);
  }

  /**
   * 调用指定二级缓存对应的 TransactionalCache 对象对应的方法
   * @param cache
   * @param key
   * @param value
   */
  public void putObject(Cache cache, CacheKey key, Object value) {
    getTransactionalCache(cache).putObject(key, value);
  }

  /**
   * 遍历 transactionalCaches 集合，调用 TransactionalCache 相应的方法
   */
  public void commit() {
    for (TransactionalCache txCache : transactionalCaches.values()) {
      txCache.commit();
    }
  }
  /**
   * 遍历 transactionalCaches 集合，调用 TransactionalCache 相应的方法
   */
  public void rollback() {
    for (TransactionalCache txCache : transactionalCaches.values()) {
      txCache.rollback();
    }
  }

  /**
   * 如果集合中不包含 TransactionalCache，则创建一个，并放入 transactionalCaches 中
   * @param cache
   * @return
   */
  private TransactionalCache getTransactionalCache(Cache cache) {
    return transactionalCaches.computeIfAbsent(cache, TransactionalCache::new);
  }

}
