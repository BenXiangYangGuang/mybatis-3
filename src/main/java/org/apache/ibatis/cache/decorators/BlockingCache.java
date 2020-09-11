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

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.ibatis.cache.Cache;
import org.apache.ibatis.cache.CacheException;

/**
 * Simple blocking decorator
 * 简单的、低效版本的阻塞缓存个装饰器
 * 一个 key 一个 ReentrantLock 锁，只在 getObject() 方法上加锁，不在 putObject() 方法上加锁，但是锁的释放有两种情况，
 * 第一种是: 从缓存中查找到 key 的数据，释放锁；
 * 第二种是：从缓存中查找到的数据为 Null，并不会释放锁，然后这个线程还会从数据库中 查询数据，查询到数据之后，放入缓存，才释放锁；这样只允许一个线程从数据库中查询数据，其他线程是不能从数据库中查询数据。
 *
 * 这样的设计非常好，锁的获取是在查询数据的时候，包括查询缓存和数据库，而锁的释放是已数据是否查到为基础，可以在缓存中查到释放锁，也可以在缓存内没有，从数据库查到释放锁，一个锁，针对两次锁竞争，一是在两个线程查询缓存的时候，
 * 二是在两个线程查询数据库的时候，一旦一个线程获取了锁，只有获得了数据才会释放锁；而且不用在查询数据库的时候，再去加锁，一锁两用。
 *
 * 一般的锁，是在写数据的时候，获取锁，然后可以多个线程去读数据，而这个锁，是在读数据的时候就获取锁，然后读到数据就释放锁，如果读不到数据，就一直加锁，直到从数据库中，查询出数据放入缓存中，才会释放锁。这有点意想不到，很好的设计。
 *
 * Simple and inefficient version of EhCache's BlockingCache decorator.
 * It sets a lock over a cache key when the element is not found in cache.
 * This way, other threads will wait until this element is filled instead of hitting the database.
 *
 * @author Eduardo Macarron
 *
 */
public class BlockingCache implements Cache {
  // 阻塞超时时间
  private long timeout;
  // 被装饰的底层 cache 对象
  private final Cache delegate;
  // 一个对象一个锁，ConcurrentHashMap 是线程安全的
  // ReentrantLock 可重入锁
  private final ConcurrentHashMap<Object, ReentrantLock> locks;

  public BlockingCache(Cache delegate) {
    this.delegate = delegate;
    this.locks = new ConcurrentHashMap<>();
  }

  @Override
  public String getId() {
    return delegate.getId();
  }

  @Override
  public int getSize() {
    return delegate.getSize();
  }

  @Override
  public void putObject(Object key, Object value) {
    try {
      delegate.putObject(key, value);
    } finally {
      // 从数据库中，查询到数据，释放锁，第二种情况释放锁
      releaseLock(key);
    }
  }

  @Override
  public Object getObject(Object key) {
    // 获取锁
    acquireLock(key);
    Object value = delegate.getObject(key);
    if (value != null) {
      // 缓存中查询到数据，释放锁，第一种情况
      releaseLock(key);
    }
    return value;
  }

  @Override
  public Object removeObject(Object key) {
    // despite of its name, this method is called only to release locks
    // 释放锁
    releaseLock(key);
    return null;
  }

  /**
   * 清空缓存
   */
  @Override
  public void clear() {
    delegate.clear();
  }

  /**
   * 根据 key，新建一个对象的锁，或者得到对象的锁
   * @param key
   * @return
   */
  private ReentrantLock getLockForKey(Object key) {
    return locks.computeIfAbsent(key, k -> new ReentrantLock());
  }

  /**
   * 上锁
   * @param key
   */
  private void acquireLock(Object key) {
    Lock lock = getLockForKey(key);
    if (timeout > 0) {
      try {
        // 设置一个获取锁的超时时间，没有获取锁，则会等待
        boolean acquired = lock.tryLock(timeout, TimeUnit.MILLISECONDS);
        if (!acquired) {
          throw new CacheException("Couldn't get a lock in " + timeout + " for the key " +  key + " at the cache " + delegate.getId());
        }
      } catch (InterruptedException e) {
        throw new CacheException("Got interrupted while trying to acquire lock for key " + key, e);
      }
    } else {
      lock.lock();
    }
  }

  /**
   * 释放锁
   * @param key
   */
  private void releaseLock(Object key) {
    ReentrantLock lock = locks.get(key);
    // 当前线程是否，继续持有锁
    if (lock.isHeldByCurrentThread()) {
      lock.unlock();
    }
  }

  public long getTimeout() {
    return timeout;
  }

  public void setTimeout(long timeout) {
    this.timeout = timeout;
  }
}
