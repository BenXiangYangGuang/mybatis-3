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
package org.apache.ibatis.mapping;

import java.sql.SQLException;
import java.util.Properties;

import javax.sql.DataSource;

/**
 * Should return an id to identify the type of this database.
 * That id can be used later on to build different queries for each database type
 * This mechanism enables supporting multiple vendors or versions
 *
 * vendors 厂商
 * 不通数据库厂商，提供不同数据库方言
 * MyBatis 不能像 Hibernate 那样， 直接帮助开发人员屏蔽多种数据库产品在 SQL 语言支持方
 * 面的差异。但是在 mybatis-config.xml 置文件中，通过＜databaseIdProvider＞定义所有支持的数
 * 据库产品的 databaseId ，然后在映射配置文件中定义 SQL 语句节点时，通过 databaseId 指定该
 * SQL 语句应用的数据库产品 ，这样也可以实现类似的功能。
 *
 * 提供了 VendorDatabaseldProvider DefaultDatabaseldProvider
 * 两个实现，其中 DefaultDatabaseldProvider 己过时。
 *
 * @author Eduardo Macarron
 */
public interface DatabaseIdProvider {

  default void setProperties(Properties p) {
    // NOP
  }

  String getDatabaseId(DataSource dataSource) throws SQLException;
}
