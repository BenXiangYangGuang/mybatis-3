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
package org.apache.ibatis.builder.xml;

import java.io.InputStream;
import java.io.Reader;
import java.util.Properties;
import javax.sql.DataSource;

import org.apache.ibatis.builder.BaseBuilder;
import org.apache.ibatis.builder.BuilderException;
import org.apache.ibatis.datasource.DataSourceFactory;
import org.apache.ibatis.executor.ErrorContext;
import org.apache.ibatis.executor.loader.ProxyFactory;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.io.VFS;
import org.apache.ibatis.logging.Log;
import org.apache.ibatis.mapping.DatabaseIdProvider;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.parsing.XNode;
import org.apache.ibatis.parsing.XPathParser;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.reflection.DefaultReflectorFactory;
import org.apache.ibatis.reflection.MetaClass;
import org.apache.ibatis.reflection.ReflectorFactory;
import org.apache.ibatis.reflection.factory.ObjectFactory;
import org.apache.ibatis.reflection.wrapper.ObjectWrapperFactory;
import org.apache.ibatis.session.AutoMappingBehavior;
import org.apache.ibatis.session.AutoMappingUnknownColumnBehavior;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.ExecutorType;
import org.apache.ibatis.session.LocalCacheScope;
import org.apache.ibatis.transaction.TransactionFactory;
import org.apache.ibatis.type.JdbcType;

/**
 * @author Clinton Begin
 * @author Kazuki Shimizu
 * `XMLConfigBuilder` 是一个读取并解析 mybats 配置的文件的类；
 */
public class XMLConfigBuilder extends BaseBuilder {

  private boolean parsed;  // 标识是否已经解析 mybats-config.xml 配置丈件
  private final XPathParser parser;  //用于解析 mybatis config xml 配置文件的 XPathParser 对象
  private String environment;   //标识＜environment＞配置的名称，默认读取＜environment> 标签的 default 属性
  //ReflectorFactory 负责创建和缓存 Reflector 对象
  private final ReflectorFactory localReflectorFactory = new DefaultReflectorFactory();

  public XMLConfigBuilder(Reader reader) {
    this(reader, null, null);
  }

  public XMLConfigBuilder(Reader reader, String environment) {
    this(reader, environment, null);
  }

  public XMLConfigBuilder(Reader reader, String environment, Properties props) {
    this(new XPathParser(reader, true, props, new XMLMapperEntityResolver()), environment, props);
  }

  public XMLConfigBuilder(InputStream inputStream) {
    this(inputStream, null, null);
  }

  public XMLConfigBuilder(InputStream inputStream, String environment) {
    this(inputStream, environment, null);
  }

  public XMLConfigBuilder(InputStream inputStream, String environment, Properties props) {
    this(new XPathParser(inputStream, true, props, new XMLMapperEntityResolver()), environment, props);
  }

  private XMLConfigBuilder(XPathParser parser, String environment, Properties props) {
    //configuration 初始值是 new Configuration Object
    super(new Configuration());
    ErrorContext.instance().resource("SQL Mapper Configuration");
    this.configuration.setVariables(props);
    this.parsed = false;
    this.environment = environment;
    this.parser = parser;
  }
  // 解析 mybatis-config 文件，并返回全局 Configuration 对象
  public Configuration parse() {
    if (parsed) {
      throw new BuilderException("Each XMLConfigBuilder can only be used once.");
    }
    parsed = true;
    //在 mybatis-config xml 配置文件中查找＜configuration＞节点，并开始解析
    parseConfiguration(parser.evalNode("/configuration"));
    return configuration;
  }

  private void parseConfiguration(XNode root) {
    try {
      //issue #117 read properties first

      //<properties resource="org/apache/ibatis/databases/blog/blog-derby.properties"/>
      //解析＜properties＞节
      propertiesElement(root.evalNode("properties"));
      //  <settings>
      //    <setting name="cacheEnabled" value="true"/>
      //    <setting name="lazyLoadingEnabled" value="false"/>
      //  </settings>
      //解析＜settings＞节点
      Properties settings = settingsAsProperties(root.evalNode("settings"));
      // TODO: 2020/8/4  vfs 虚拟文件系统
      loadCustomVfs(settings);    // 设置 vfsimpl 字段
      // 加载 <setting> 节点中配置的 第三方日志实现
      loadCustomLogImpl(settings);
      //解析＜typeAliases＞节点
      typeAliasesElement(root.evalNode("typeAliases"));
      //解析＜plugins＞节
      pluginElement(root.evalNode("plugins"));
      //解析＜objectFactory＞节点
      objectFactoryElement(root.evalNode("objectFactory"));
      //解析 <objectWrapperFactory> 节点
      objectWrapperFactoryElement(root.evalNode("objectWrapperFactory"));
      //解析＜reflectorFactory>节点
      reflectorFactoryElement(root.evalNode("reflectorFactory"));
      // 解析各种设置，在 mybatis 环境中生效
      settingsElement(settings);
      // read it after objectFactory and objectWrapperFactory issue #631
      //解析＜environments>节点
      environmentsElement(root.evalNode("environments"));
      //解析＜databaseIdProvider＞节点
      databaseIdProviderElement(root.evalNode("databaseIdProvider"));
      //解析＜typeHandlers＞节点
      typeHandlerElement(root.evalNode("typeHandlers"));
      //解析<mappers＞节点
      mapperElement(root.evalNode("mappers"));
    } catch (Exception e) {
      throw new BuilderException("Error parsing SQL Mapper Configuration. Cause: " + e, e);
    }
  }
  // 检测 setting 属性是否是 Configuration 中指定的，包含 setter 方法， 就是指定的 Configuration 属性
  // 使用 MetaClass 检测 key 定的属性在 configuration 类中是否有对应 setter 方法的步骤。
  private Properties settingsAsProperties(XNode context) {
    if (context == null) {
      return new Properties();
    }
    Properties props = context.getChildrenAsProperties();
    // Check that all settings are known to the configuration class
    // 检查 setting 设置的参数，是否是指定的，并可被解析的
    // 创建 Configuration 类的元信息包装类，localReflectorFactory 为 DefaultReflectorFactory 新建的对象；
    // metaConfig 为短暂的方法生命周期对象，近在此方法中临时创建，并近存活于此方法中；
    // 检测 Configuration 是否定义了 key 指定属性相应的 setter 方法
    MetaClass metaConfig = MetaClass.forClass(Configuration.class, localReflectorFactory);
    for (Object key : props.keySet()) {
      if (!metaConfig.hasSetter(String.valueOf(key))) {
        throw new BuilderException("The setting " + key + " is not known.  Make sure you spelled it correctly (case sensitive).");
      }
    }
    return props;
  }

  private void loadCustomVfs(Properties props) throws ClassNotFoundException {
    String value = props.getProperty("vfsImpl");
    if (value != null) {
      String[] clazzes = value.split(",");
      for (String clazz : clazzes) {
        if (!clazz.isEmpty()) {
          @SuppressWarnings("unchecked")
          Class<? extends VFS> vfsImpl = (Class<? extends VFS>)Resources.classForName(clazz);
          configuration.setVfsImpl(vfsImpl);
        }
      }
    }
  }

  /**
   * 加载 <setting> 配置的 log 日志实现
   * @param props
   */
  private void loadCustomLogImpl(Properties props) {
    // logImpl 全路径名称
    Class<? extends Log> logImpl = resolveClass(props.getProperty("logImpl"));
    configuration.setLogImpl(logImpl);
  }
 /**
  * 解析类的别名
   <typeAliases>
    <typeAlias alias="BlogAuthor" type="org.apache.ibatis.domain.blog.Author"/>
    <typeAlias type="org.apache.ibatis.domain.blog.Blog"/>
    <typeAlias type="org.apache.ibatis.domain.blog.Post"/>
    <package name="org.apache.ibatis.domain.jpetstore"/>
  </typeAliases>
  */
  private void typeAliasesElement(XNode parent) {
    if (parent != null) {
      for (XNode child : parent.getChildren()) {
        // 如果包含 package,解析 package 下类,进行别名注册
        // 处理＜package ＞节点
        if ("package".equals(child.getName())) {
          String typeAliasPackage = child.getStringAttribute("name");
          // 通过 TypeAliasRegistry 扫描指定包中所有的类，并解析＠Alias 注解，完成别名注册
          configuration.getTypeAliasRegistry().registerAliases(typeAliasPackage);
        } else {
          String alias = child.getStringAttribute("alias");  // 获取指定的别名
          String type = child.getStringAttribute("type");    // 获取别名对应的类型
          try {
            Class<?> clazz = Resources.classForName(type);
            if (alias == null) {
              typeAliasRegistry.registerAlias(clazz); // 扫描 @Alias 完成注册 ，完成注册
            } else {
              typeAliasRegistry.registerAlias(alias, clazz);
            }
          } catch (ClassNotFoundException e) {
            throw new BuilderException("Error registering typeAlias for '" + alias + "'. Cause: " + e, e);
          }
        }
      }
    }
  }

  // 以通过添加自定义插件在 SQL 语句执行过程中的某点进行拦截。 MyBatis 中的自定义插件只需实现 Interceptor 接口，并通过注解指定想
  // 要拦截的方法签名即可

  /**
   * <plugins>
   *   <plugin interceptor="org.apache.ibatis.builder.ExamplePlugin">
   *      <property name="pluginProperty" value="100"/>
   *   </plugin>
   *  </plugins>
   * @param parent
   * @throws Exception
   */
  private void pluginElement(XNode parent) throws Exception {
    if (parent != null) {
      for (XNode child : parent.getChildren()) {
        String interceptor = child.getStringAttribute("interceptor");
        Properties properties = child.getChildrenAsProperties();
        // 创建插件实例,插件是 Interceptor 的具体实现
        Interceptor interceptorInstance = (Interceptor) resolveClass(interceptor).getDeclaredConstructor().newInstance();
        // 设置插件的属性
        interceptorInstance.setProperties(properties);
        // 设置插件到拦截器链中
        configuration.addInterceptor(interceptorInstance);
      }
    }
  }

  /**
   *  <objectFactory type="org.apache.ibatis.builder.ExampleObjectFactory">
   *    <property name="objectFactoryProperty" value="100"/>
   *  </objectFactory>
   * @param context
   * @throws Exception
   */
  // 解析 objectFactory 节点, objectFactory 创建对象的工厂
  // 可以通过添加自定义 ObjectFactory 实现类、ObjectWrapperFactory 实现类、ReflectorFactory 实现类对 Mybatis 进行扩展
  private void objectFactoryElement(XNode context) throws Exception {
    if (context != null) {
      String type = context.getStringAttribute("type");
      Properties properties = context.getChildrenAsProperties();
      ObjectFactory factory = (ObjectFactory) resolveClass(type).getDeclaredConstructor().newInstance(); // 创建 objectFactory 对象
      factory.setProperties(properties);  // 设置对象属性
      configuration.setObjectFactory(factory); // 反写 configuration 对象中的 objectFactory 属性
    }
  }

  private void objectWrapperFactoryElement(XNode context) throws Exception {
    if (context != null) {
      String type = context.getStringAttribute("type");
      ObjectWrapperFactory factory = (ObjectWrapperFactory) resolveClass(type).getDeclaredConstructor().newInstance();
      configuration.setObjectWrapperFactory(factory);
    }
  }

  private void reflectorFactoryElement(XNode context) throws Exception {
    if (context != null) {
      String type = context.getStringAttribute("type");
      ReflectorFactory factory = (ReflectorFactory) resolveClass(type).getDeclaredConstructor().newInstance();
      configuration.setReflectorFactory(factory);
    }
  }
  //解析properties节点
  private void propertiesElement(XNode context) throws Exception {
    if (context != null) {
      //得到所有的孩子属性；
      Properties defaults = context.getChildrenAsProperties();
      //parse only one resource element ： resource or url
      String resource = context.getStringAttribute("resource");
      String url = context.getStringAttribute("url");
      if (resource != null && url != null) {
        throw new BuilderException("The properties element cannot specify both a URL and a resource based property file reference.  Please specify one or the other.");
      }
      if (resource != null) {
        defaults.putAll(Resources.getResourceAsProperties(resource));
      } else if (url != null) {
        defaults.putAll(Resources.getUrlAsProperties(url));
      }

      // ．．．与 Configurat on 对象中 variables 集合合并（略）
      Properties vars = configuration.getVariables();
      if (vars != null) {
        defaults.putAll(vars);
      }
      //设置 解析器 variables; 在后面的解析过程中，会使用该 Properties 对象中的信息替换占位符。
      parser.setVariables(defaults);
      //更新 configuration 的 variables 变量
      configuration.setVariables(defaults);
    }
  }

  /**
   * 解析全局配置，并应用到 mybatis 全局变量，使配置生效
   * @param props
   */
  private void settingsElement(Properties props) {
    configuration.setAutoMappingBehavior(AutoMappingBehavior.valueOf(props.getProperty("autoMappingBehavior", "PARTIAL")));
    configuration.setAutoMappingUnknownColumnBehavior(AutoMappingUnknownColumnBehavior.valueOf(props.getProperty("autoMappingUnknownColumnBehavior", "NONE")));
    configuration.setCacheEnabled(booleanValueOf(props.getProperty("cacheEnabled"), true));
    configuration.setProxyFactory((ProxyFactory) createInstance(props.getProperty("proxyFactory")));
    configuration.setLazyLoadingEnabled(booleanValueOf(props.getProperty("lazyLoadingEnabled"), false));
    configuration.setAggressiveLazyLoading(booleanValueOf(props.getProperty("aggressiveLazyLoading"), false));
    configuration.setMultipleResultSetsEnabled(booleanValueOf(props.getProperty("multipleResultSetsEnabled"), true));
    configuration.setUseColumnLabel(booleanValueOf(props.getProperty("useColumnLabel"), true));
    configuration.setUseGeneratedKeys(booleanValueOf(props.getProperty("useGeneratedKeys"), false));
    configuration.setDefaultExecutorType(ExecutorType.valueOf(props.getProperty("defaultExecutorType", "SIMPLE")));
    configuration.setDefaultStatementTimeout(integerValueOf(props.getProperty("defaultStatementTimeout"), null));
    configuration.setDefaultFetchSize(integerValueOf(props.getProperty("defaultFetchSize"), null));
    configuration.setDefaultResultSetType(resolveResultSetType(props.getProperty("defaultResultSetType")));
    configuration.setMapUnderscoreToCamelCase(booleanValueOf(props.getProperty("mapUnderscoreToCamelCase"), false));
    configuration.setSafeRowBoundsEnabled(booleanValueOf(props.getProperty("safeRowBoundsEnabled"), false));
    configuration.setLocalCacheScope(LocalCacheScope.valueOf(props.getProperty("localCacheScope", "SESSION")));
    configuration.setJdbcTypeForNull(JdbcType.valueOf(props.getProperty("jdbcTypeForNull", "OTHER")));
    configuration.setLazyLoadTriggerMethods(stringSetValueOf(props.getProperty("lazyLoadTriggerMethods"), "equals,clone,hashCode,toString"));
    configuration.setSafeResultHandlerEnabled(booleanValueOf(props.getProperty("safeResultHandlerEnabled"), true));
    configuration.setDefaultScriptingLanguage(resolveClass(props.getProperty("defaultScriptingLanguage")));
    configuration.setDefaultEnumTypeHandler(resolveClass(props.getProperty("defaultEnumTypeHandler")));
    configuration.setCallSettersOnNulls(booleanValueOf(props.getProperty("callSettersOnNulls"), false));
    configuration.setUseActualParamName(booleanValueOf(props.getProperty("useActualParamName"), true));
    configuration.setReturnInstanceForEmptyRow(booleanValueOf(props.getProperty("returnInstanceForEmptyRow"), false));
    configuration.setLogPrefix(props.getProperty("logPrefix"));
    configuration.setConfigurationFactory(resolveClass(props.getProperty("configurationFactory")));
  }

  /**
   * <environments default="development">
   *     <environment id="development">
   *       <transactionManager type="JDBC">
   *         <property name="" value="" />
   *       </transactionManager>
   *       <dataSource type="UNPOOLED">
   *         <property name="driver" value="org.hsqldb.jdbcDriver" />
   *         <property name="url" value="jdbc:hsqldb:mem:localtime" />
   *         <property name="username" value="sa" />
   *       </dataSource>
   *     </environment>
   *   </environments>
   *
   * @param context
   * @throws Exception
   */
  private void environmentsElement(XNode context) throws Exception {
    if (context != null) {
      //未指定 XMLConfigBuilder environment 字段，则使用 default 性指定的 <environment>
      if (environment == null) {
        environment = context.getStringAttribute("default");
      }
      for (XNode child : context.getChildren()) {
        String id = child.getStringAttribute("id");
        if (isSpecifiedEnvironment(id)) {
          TransactionFactory txFactory = transactionManagerElement(child.evalNode("transactionManager")); // 创建 TransactionFactory
          DataSourceFactory dsFactory = dataSourceElement(child.evalNode("dataSource")); // 创建 DataSourceFactory
          DataSource dataSource = dsFactory.getDataSource();        // 创建 DataSource
          Environment.Builder environmentBuilder = new Environment.Builder(id)
              .transactionFactory(txFactory)
              .dataSource(dataSource);
          configuration.setEnvironment(environmentBuilder.build()); // 创建 Environment,并反写 configuration 对象
        }
      }
    }
  }

  /**
   *   <databaseIdProvider type="DB_VENDOR">
   *     <property name="Apache Derby" value="derby"/>
   *   </databaseIdProvider>
   * @param context
   * @throws Exception
   */
  private void databaseIdProviderElement(XNode context) throws Exception {
    DatabaseIdProvider databaseIdProvider = null;
    if (context != null) {
      String type = context.getStringAttribute("type");
      // awful patch to keep backward compatibility
      if ("VENDOR".equals(type)) {  // 为了保证兼容性，修改 type 取值
        type = "DB_VENDOR";
      }
      Properties properties = context.getChildrenAsProperties();
      databaseIdProvider = (DatabaseIdProvider) resolveClass(type).getDeclaredConstructor().newInstance();
      databaseIdProvider.setProperties(properties);
    }
    Environment environment = configuration.getEnvironment();
    if (environment != null && databaseIdProvider != null) {
      // 根据前面确定的 DataSource 确定当前使用的数据库产品,得到不同厂商数据库Id
      String databaseId = databaseIdProvider.getDatabaseId(environment.getDataSource());
      configuration.setDatabaseId(databaseId);
    }
  }

  /**
   * 根据 配置文件中的节点，创建 TransactionFactory 对象
   * @param context
   * @return
   * @throws Exception
   */
  private TransactionFactory transactionManagerElement(XNode context) throws Exception {
    if (context != null) {
      String type = context.getStringAttribute("type");
      Properties props = context.getChildrenAsProperties();
      TransactionFactory factory = (TransactionFactory) resolveClass(type).getDeclaredConstructor().newInstance();
      factory.setProperties(props);
      return factory;
    }
    throw new BuilderException("Environment declaration requires a TransactionFactory.");
  }

  /**
   * 根据 配置文件中的节点，创建 DataSourceFactory 对象
   * @param context
   * @return
   * @throws Exception
   */
  private DataSourceFactory dataSourceElement(XNode context) throws Exception {
    if (context != null) {
      String type = context.getStringAttribute("type");
      Properties props = context.getChildrenAsProperties();
      DataSourceFactory factory = (DataSourceFactory) resolveClass(type).getDeclaredConstructor().newInstance();
      factory.setProperties(props);
      return factory;
    }
    throw new BuilderException("Environment declaration requires a DataSourceFactory.");
  }

  private void typeHandlerElement(XNode parent) {
    if (parent != null) {
      for (XNode child : parent.getChildren()) {
        if ("package".equals(child.getName())) {
          String typeHandlerPackage = child.getStringAttribute("name");
          typeHandlerRegistry.register(typeHandlerPackage);
        } else {
          String javaTypeName = child.getStringAttribute("javaType");
          String jdbcTypeName = child.getStringAttribute("jdbcType");
          String handlerTypeName = child.getStringAttribute("handler");
          Class<?> javaTypeClass = resolveClass(javaTypeName);
          JdbcType jdbcType = resolveJdbcType(jdbcTypeName);
          Class<?> typeHandlerClass = resolveClass(handlerTypeName);
          if (javaTypeClass != null) {
            if (jdbcType == null) {
              typeHandlerRegistry.register(javaTypeClass, typeHandlerClass);
            } else {
              typeHandlerRegistry.register(javaTypeClass, jdbcType, typeHandlerClass);
            }
          } else {
            typeHandlerRegistry.register(typeHandlerClass);
          }
        }
      }
    }
  }

  /**
   * 解析实体 mapper 文件；
   *   <mappers>
   *     <mapper resource="org/apache/ibatis/builder/BlogMapper.xml"/>
   *     <mapper url="file:./src/test/java/org/apache/ibatis/builder/NestedBlogMapper.xml"/>
   *     <mapper class="org.apache.ibatis.builder.CachedAuthorMapper"/>
   *     <package name="org.apache.ibatis.builder.mapper"/>
   *   </mappers>
   * @param parent
   * @throws Exception
   */
  private void mapperElement(XNode parent) throws Exception {
    if (parent != null) {
      for (XNode child : parent.getChildren()) {
        if ("package".equals(child.getName())) { // <package ＞子节点
          String mapperPackage = child.getStringAttribute("name");
          configuration.addMappers(mapperPackage);
        } else {
          // 获取＜mapper ＞节点的 resource url class 属性，这三个属性互斥
          String resource = child.getStringAttribute("resource");
          String url = child.getStringAttribute("url");
          String mapperClass = child.getStringAttribute("class");
          // 如果＜mapper ＞节点指定了 resource 或是 url 属性，则创建 XMLMapperBuilder 对象
          // 并通过该对象解析 resource 或是 url 属性指定的 Mapper 配置文件
          if (resource != null && url == null && mapperClass == null) {
            ErrorContext.instance().resource(resource);
            InputStream inputStream = Resources.getResourceAsStream(resource);
            XMLMapperBuilder mapperParser = new XMLMapperBuilder(inputStream, configuration, resource, configuration.getSqlFragments());
            mapperParser.parse();
          } else if (resource == null && url != null && mapperClass == null) {
            ErrorContext.instance().resource(url);
            InputStream inputStream = Resources.getUrlAsStream(url);
            XMLMapperBuilder mapperParser = new XMLMapperBuilder(inputStream, configuration, url, configuration.getSqlFragments());
            // 解析 mapper 节点
            mapperParser.parse();
          } else if (resource == null && url == null && mapperClass != null) {
            // 如果＜mapper＞节点指定了 class 属性，则 MapperRegistry 注册该 Mapper 接口
            Class<?> mapperInterface = Resources.classForName(mapperClass);
            configuration.addMapper(mapperInterface);
          } else {
            throw new BuilderException("A mapper element may only specify a url, resource or class, but not more than one.");
          }
        }
      }
    }
  }

  /**
   * 检测是否是，指定的环境
   * @param id
   * @return
   */
  private boolean isSpecifiedEnvironment(String id) {
    if (environment == null) {
      throw new BuilderException("No environment specified.");
    } else if (id == null) {
      throw new BuilderException("Environment requires an id attribute.");
    } else if (environment.equals(id)) {
      return true;
    }
    return false;
  }

}
