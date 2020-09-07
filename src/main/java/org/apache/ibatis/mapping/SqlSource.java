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
package org.apache.ibatis.mapping;

/**
 * Represents the content of a mapped statement read from an XML file or an annotation.
 * It creates the SQL that will be passed to the database out of the input parameter received from the user.
 *
 * mapper.xml 配置文件中定义的 SQL 节点会被解析成 MappedStatement 对象，
 * 其中 SQL 语句会被解析成 SqlSource 对象，
 * SQL 语句中定义的动态 SQL 节点、文本节点等，则由 SqlNode 接口的相应的表示
 *
 * SqlSource 接口表示映射文件或注解中定义的 SQL 语句;
 * 但它表示的 SQL 语句是不能直接被数据库执行的，因为其中可能含有动态的 SQL 语句相关的节点 或是占位符等需要解析的元素。
 *
 * DynamicSqlSource 负责处理动态 SQL 语句， RawSqlSource 负责处理静态语句;
 *
 * DynamicSqlSource and RawSqlSource 都会最终 封装成为 StaticSqlSource
 *
 * DynamicSqlSource 与 StaticSqlSource 的主要区别：
 * StaticSqlSource 中记录的 SQL 语句可能包含 "?" 占位符，但是可以直接交给数据库执行；
 * DynamicSqlSource 中封装的 SQL 语句还需要一系列解析，才会最终形成数据库可执行的 SQL 语句。
 *
 * 无论是 StaticSqlSource 、DynamicSqlSource 还是 RawSq!Source ，最终都会统一生成 BoundSql 对象，其中封装了完整的 SQL 语句（可能包含“？”占位符）、
 * 参数映射关系（parameterMappings 集合）以及用户传入的参数（ additionalParameters集合）。另 外， DynamicSqlSource 负责处理动态 SQL 吾句，
 * RawSqlSource 负责处理静态 SQL。除此之外，两者解析 SQL 语句的时机也不样，前者的解析时机是在实际执行 SQL 语句之前，
 * 而后者则是在 MyBatis 初始化时完成 SQL 语句的解析。
 * @author Clinton Begin
 */
public interface SqlSource {

  // BoundSql其中封装了包含 ？”占位符的 SQL 语句，以及绑定的实参，是一个可执行 Sql 语句

  //getBoundSql() 方法会根据映射文件或注解描述的 SQL 语句，以及传入的参敛，返回可执行的 SQL
  BoundSql getBoundSql(Object parameterObject);


}
