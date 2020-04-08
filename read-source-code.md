#### 解析器
##### XPath
##### XPathParser
- DOM(Document Object Model)解析方式  
- SAX (Simple API for XML)解析方式 推模式 解析部分XML  
- JDK 支持的 StAX (Streaming API for XML)解析方式 拉模式 解析部分XML 
#### 反射
##### Reflector
##### ReflectorFactory
##### TypeParameterResolver
##### ObjectFactory
##### Property 工具集
##### MetaClass
##### ObjectWrapper
##### MetaObject

#### 类型转换
##### JdbcType
这个枚举类型代表 java.sql.Types 数据类型；
##### TypeHandler
My Batis 中所有的类型转换器都继承了 TypeHandler 接口，规定了javaType和JdbcType类型转换的规范；
##### TypeReference
##### TypeHandlerRegistry
TypeHandlerRegistry负责管理这些TypeHandler，mybatis系统初始化，会为已知的TypeHandler创建对象，并注册到TypeHandlerRegistry中
