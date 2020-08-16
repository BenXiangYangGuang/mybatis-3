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
package org.apache.ibatis.builder;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.ibatis.mapping.ParameterMapping;
import org.apache.ibatis.mapping.SqlSource;
import org.apache.ibatis.parsing.GenericTokenParser;
import org.apache.ibatis.parsing.TokenHandler;
import org.apache.ibatis.reflection.MetaClass;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.type.JdbcType;

/**
 * 在经过 SqlNode.apply() 方法的解析之后， SQL 语句会被传递到 SqlSourceBuilder 进行进一步的解析。
 * SqlSourceBuilder 主要完成了两方面的操作， 方面是解析 SQL 语句中的"#{}"占位符中定义的属性，格式类似于#{item_0, javaType=int, jdbcType=NUMERIC,
 * typeHandler=MyTypeHandler}， 另一方面是将 SQL 语句中的"${}"占位符替换成 "?" 占位符。
 *
 * SqlSourceBuilder 也是 BaseBuilder 子类之一，其核 逻辑位于 parse() 方法中
 * @author Clinton Begin
 */
public class SqlSourceBuilder extends BaseBuilder {

  private static final String PARAMETER_PROPERTIES = "javaType,jdbcType,mode,numericScale,resultMap,typeHandler,jdbcTypeName";

  public SqlSourceBuilder(Configuration configuration) {
    super(configuration);
  }

  /**
   * SqlSourceBuilder 会将上述 SQL 语句以及 parameterMappings 集合封装成 StaticSqlSource 对象。
   * StaticSqlSource.getBoundSql() 方法的实现比较简单,他直接创建并返回 BoundSql 对象，该 BoundSql 对象也就是 DynamicSqlSource 返回的 BoundSql 对象。
   *
   * @param originalSql 第一个参数是经过 SqlNode.apply() 方法处理之后的 SQL 语句
   * @param parameterType 第二个参数是用户传入的实参类型
   * @param additionalParameters 第三个参数记录 形参与实参的对应关系，其实就是经过 SqlNode.apply()方法处理后的 DynamicContext.bindings 集合
   * @return
   */
  public SqlSource parse(String originalSql, Class<?> parameterType, Map<String, Object> additionalParameters) {
    //创建 ParameterMappingTokenHandler 对象，它是解析 "#{}" 占位符 中的参数属性以及替换占位符的核心
    ParameterMappingTokenHandler handler = new ParameterMappingTokenHandler(configuration, parameterType, additionalParameters);
    // GenericTokenParser 与 ParameterMappingTokenHandler 配合解析 "#{}" 占位符
    GenericTokenParser parser = new GenericTokenParser("#{", "}", handler);
    String sql = parser.parse(originalSql);
    // 创建 StaticSqlSource ，其中封装了占位符被替换成 "?" 的 SQL 语句以及参数对应 ParameterMapping 集合
    return new StaticSqlSource(configuration, sql, handler.getParameterMappings());
  }
  // ParameterMappingTokenHandler 继承了 BaseBuilder
  private static class ParameterMappingTokenHandler extends BaseBuilder implements TokenHandler {
    // 用于记录解析得到 ParameterMapping 集合
    private List<ParameterMapping> parameterMappings = new ArrayList<>();
    private Class<?> parameterType;  // 参数类型
    // DynamicContext.bindings 集合对应的 MetaObject 对象
    private MetaObject metaParameters;

    public ParameterMappingTokenHandler(Configuration configuration, Class<?> parameterType, Map<String, Object> additionalParameters) {
      super(configuration);
      this.parameterType = parameterType;
      this.metaParameters = configuration.newMetaObject(additionalParameters);
    }

    public List<ParameterMapping> getParameterMappings() {
      return parameterMappings;
    }

    @Override
    public String handleToken(String content) {
      // 创建一个 ParameterMapping 对象，并添加到 parameterMappings 集合中保存
      parameterMappings.add(buildParameterMapping(content));
      return "?"; // 返回 ？ 占位符
    }

    /**
     * 解析参数属性，参数是 sql ？占位符 实参的属性
     * @param content
     * @return
     */
    private ParameterMapping buildParameterMapping(String content) {
      // 解析参数的属性，并形成 Map 例如 #{_frc_item_0 , javaType=int, jdbcType=NUMERIC,type andler=MyTypeHandler ｝这个占位符,他就会被解析成如下 Map：
      // {property -> _frc_item_0，"javaType" -> "int" , "jdbcType" -> "NUMERIC","typeHandler" -> "MyTypeHandler"}
      Map<String, String> propertiesMap = parseParameterMapping(content);
      String property = propertiesMap.get("property"); // 获取参数名称
      Class<?> propertyType;
      // 确定参数 javaType 属性
      if (metaParameters.hasGetter(property)) { // issue #448 get type from additional params
        propertyType = metaParameters.getGetterType(property);
      } else if (typeHandlerRegistry.hasTypeHandler(parameterType)) {
        propertyType = parameterType;
      } else if (JdbcType.CURSOR.name().equals(propertiesMap.get("jdbcType"))) {
        propertyType = java.sql.ResultSet.class;
      } else if (property == null || Map.class.isAssignableFrom(parameterType)) {
        propertyType = Object.class;
      } else {
        MetaClass metaClass = MetaClass.forClass(parameterType, configuration.getReflectorFactory());
        if (metaClass.hasGetter(property)) {
          propertyType = metaClass.getGetterType(property);
        } else {
          propertyType = Object.class;
        }
      }
      // 创建 ParameterMapping 的建造者，并设置 ParameterMapping 相关配置
      ParameterMapping.Builder builder = new ParameterMapping.Builder(configuration, property, propertyType);
      Class<?> javaType = propertyType;
      String typeHandlerAlias = null;
      for (Map.Entry<String, String> entry : propertiesMap.entrySet()) {
        String name = entry.getKey();
        String value = entry.getValue();
        if ("javaType".equals(name)) {
          javaType = resolveClass(value);
          builder.javaType(javaType);
        } else if ("jdbcType".equals(name)) {
          builder.jdbcType(resolveJdbcType(value));
        } else if ("mode".equals(name)) {
          builder.mode(resolveParameterMode(value));
        } else if ("numericScale".equals(name)) {
          builder.numericScale(Integer.valueOf(value));
        } else if ("resultMap".equals(name)) {
          builder.resultMapId(value);
        } else if ("typeHandler".equals(name)) {
          typeHandlerAlias = value;
        } else if ("jdbcTypeName".equals(name)) {
          builder.jdbcTypeName(value);
        } else if ("property".equals(name)) {
          // Do Nothing
        } else if ("expression".equals(name)) {
          throw new BuilderException("Expression based parameters are not supported yet");
        } else {
          throw new BuilderException("An invalid property '" + name + "' was found in mapping #{" + content + "}.  Valid properties are " + PARAMETER_PROPERTIES);
        }
      }
      // 获取 TypeHandler 对象
      if (typeHandlerAlias != null) {
        builder.typeHandler(resolveTypeHandler(javaType, typeHandlerAlias));
      }
      // 创建 ParameterMapping 对象，注意，如果没有指定 TypeHandler, 则会在这里的 build() 方法中 ，根据 javaType 和 jdbcType 从 TypeHandlerRegistry 中获取对应的 TypeHandler 对象
      return builder.build();
    }

    private Map<String, String> parseParameterMapping(String content) {
      try {
        return new ParameterExpression(content);
      } catch (BuilderException ex) {
        throw ex;
      } catch (Exception ex) {
        throw new BuilderException("Parsing error was found in mapping #{" + content + "}.  Check syntax #{property|(expression), var1=value1, var2=value2, ...} ", ex);
      }
    }
  }

}
