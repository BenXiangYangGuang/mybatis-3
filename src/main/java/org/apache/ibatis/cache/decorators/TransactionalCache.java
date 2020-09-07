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

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.apache.ibatis.cache.Cache;
import org.apache.ibatis.logging.Log;
import org.apache.ibatis.logging.LogFactory;

/**
 *
 * TransactionalCache 主要用于保存 某个 SqlSession 的 某个事务中需要向 某个二级缓存中添加的缓存 的数据。
 * The 2nd level cache transactional buffer.
 * <p>
 * This class holds all cache entries that are to be added to the 2nd level cache during a Session.
 * Entries are sent to the cache when commit is called or discarded if the Session is rolled back.
 * Blocking cache support has been added. Therefore any get() that returns a cache miss
 * will be followed by a put() so any lock associated with the key can be released.
 *
 * @author Clinton Begin
 * @author Eduardo Macarron
 */
public class TransactionalCache implements Cache {

  private static final Log log = LogFactory.getLog(TransactionalCache.class);
  // 底层封装的二级缓存所对应的 Cache 对象，以存储 namespace 为单位的 Cache 对象，默认为 PerpetualCache
  private final Cache delegate;
  // 当改字段为 true 时，则表示当前 TransactionalCache 不可查询，且提交事务时会将底层 Cache 清空
  private boolean clearOnCommit;
  // 暂时记录添加到 TransactionalCache 中的数据。在事务提交时，会将其中的数据添加到二级缓存中
  private final Map<Object, Object> entriesToAddOnCommit;
  // 记录缓存未命中的 CacheKey 对象
  private final Set<Object> entriesMissedInCache;

  public TransactionalCache(Cache delegate) {
    this.delegate = delegate;
    this.clearOnCommit = false;
    this.entriesToAddOnCommit = new HashMap<>();
    this.entriesMissedInCache = new HashSet<>();
  }

  @Override
  public String getId() {
    return delegate.getId();
  }

  @Override
  public int getSize() {
    return delegate.getSize();
  }

  /**
   * 它首先会查询底层的二级缓存，并将为命中的 key 记录到 entriesMissedInCache，之后根据 clearOnCommit 字段的值决定具体的返回值
   * @param key The key
   * @return
   */
  @Override
  public Object getObject(Object key) {
    // issue #116
    // 查询底层的 Cache 是否包含了指定的 key
    Object object = delegate.getObject(key);
    // 如果底层缓存对象中不包含改缓存项，则将该 key 记录到 entriesMissedInCache 集合中
    if (object == null) {
      entriesMissedInCache.add(key);
    }
    // issue #146
    if (clearOnCommit) {
      return null;
    } else {
      // 返回层底层 Cache 中查询到的对象
      return object;
    }
  }

  /**
   * 该方法并没有直接将结果对象记录到其封装的二级缓存中，而是暂时保存在 entriesToAddOnCommit 集合中，
   * 在事务提交时才会将这些结果对象从 entriesToAddOnCommit 集合 添加到二级缓存中。
   * @param key Can be any object but usually it is a {@link CacheKey}
   * @param object
   */
  @Override
  public void putObject(Object key, Object object) {
    entriesToAddOnCommit.put(key, object);
  }

  @Override
  public Object removeObject(Object key) {
    return null;
  }

  /**
   * 清空 entriesToAddOnCommit 集合，并设置 clearOnCommit 为 true
   */
  @Override
  public void clear() {
    clearOnCommit = true;
    entriesToAddOnCommit.clear();
  }

  /**
   * 会根据 clearOnCommit 字段的值决定是否清空二级缓存，然后调用 flushPendingEntries() 方法将 entriesToAddOnCommit 集合中记录的结果对象保存到二级缓存中
   */
  public void commit() {
    // 在事务提交前，清空二级缓存
    if (clearOnCommit) {
      delegate.clear();
    }
    // 将 entriesToAddOnCommit 集合中记录的结果对象保存到二级缓存中
    flushPendingEntries();
    // 重置 clearOnCommit 值，清空 entriesToAddOnCommit 和 entriesMissedInCache 集合
    reset();
  }

  /**
   * 将 entriesMissedInCache 集合中记录的缓存项从二级缓存中删除，并清空 entriesToAddOnCommit 和 entriesMissedInCache 集合
   */
  public void rollback() {
    // 将 entriesMissedInCache 集合中记录的缓存项从二级缓存中删除
    unlockMissedEntries();
    // 清空 entriesToAddOnCommit 和 entriesMissedInCache 集合
    reset();
  }

  /**
   * 重置 clearOnCommit 值，清空 entriesToAddOnCommit 和 entriesMissedInCache 集合
   */
  private void reset() {
    clearOnCommit = false;
    entriesToAddOnCommit.clear();
    entriesMissedInCache.clear();
  }

  /**
   * 将 entriesToAddOnCommit 集合中记录的结果对象保存到二级缓存中
   */
  private void flushPendingEntries() {
    for (Map.Entry<Object, Object> entry : entriesToAddOnCommit.entrySet()) {
      delegate.putObject(entry.getKey(), entry.getValue());
    }
    // 遍历 entriesMissedInCache 集合，缓存为命中的，且 entriesToAddOnCommit 集合中不包含的缓存项， 添加到二级缓冲中
    for (Object entry : entriesMissedInCache) {
      if (!entriesToAddOnCommit.containsKey(entry)) {
        delegate.putObject(entry, null);
      }
    }
  }

  /**
   * 将 entriesMissedInCache 集合中记录的缓存项从二级缓存中删除
   */
  private void unlockMissedEntries() {
    for (Object entry : entriesMissedInCache) {
      try {
        delegate.removeObject(entry);
      } catch (Exception e) {
        log.warn("Unexpected exception while notifiying a rollback to the cache adapter."
            + "Consider upgrading your cache adapter to the latest version.  Cause: " + e);
      }
    }
  }

}
