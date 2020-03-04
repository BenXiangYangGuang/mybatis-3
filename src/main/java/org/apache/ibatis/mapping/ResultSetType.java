/**
 *    Copyright 2009-2018 the original author or authors.
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
package org.apache.ibatis.mapping;

import java.sql.ResultSet;

/**
 * @author Clinton Begin
 * 参考：https://www.iteye.com/blog/zhenkm0507-560109
 */
public enum ResultSetType {
  /**
   * behavior with same as unset (driver dependent).
   *
   * @since 3.5.0
   */
  DEFAULT(-1),
  FORWARD_ONLY(ResultSet.TYPE_FORWARD_ONLY), //结果集数据，游标只能向前移动，不能用于向后，first、last等
  SCROLL_INSENSITIVE(ResultSet.TYPE_SCROLL_INSENSITIVE), //结果集数据,游标是可以移动的，对数据是数据库中的数据是不明感的
  SCROLL_SENSITIVE(ResultSet.TYPE_SCROLL_SENSITIVE); //结果集的数据，游标是可以移动的，结果集中，会缓存每条数据的rowid，而不是真正的数据，其他session修改数据后，这个结果集的数据，查出来是跟着修改的

  private final int value;

  ResultSetType(int value) {
    this.value = value;
  }

  public int getValue() {
    return value;
  }
}
