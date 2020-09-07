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
package org.apache.ibatis.executor.keygen;

import java.sql.Statement;

import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.MappedStatement;

/**
 * insert、update  语句 获取数据库自增主键 id
 * insert 语句并不会返回自动生成的主键，而是返回插入记录的条数。如果业务
 * 逻辑需要获取插入记录时产生的自增主键，则可以使用 Mybatis 提供的 KeyGenerator 接口。
 *
 * 不同的数据库产品对应的主键生成策略不一样，例如， Oracle 、DB2 等数据库产品是通过
 * sequence 实现自增 id 的，在执行 insert 语句之前必须明确指定主键的值；
 * 而 MySQL, Postgresql 等数据库在执行 insert 吾句时，可以不指定主键，在插入过程中由数据库自动生成自增主键。
 * KeyGenerator 接口针对这些不同的数据库产品提供了对应的处理方法。
 *
 * https://cofcool.github.io/tech/2017/11/06/Mybatis-insert-get-id
 * @author Clinton Begin
 */
public interface KeyGenerator {


  // 在执行 insert 之前执行，设置属性 order = "BEFORE"
  void processBefore(Executor executor, MappedStatement ms, Statement stmt, Object parameter);

  // 在执行 insert 之后执行，设置属性 order = "After"
  void processAfter(Executor executor, MappedStatement ms, Statement stmt, Object parameter);

}
