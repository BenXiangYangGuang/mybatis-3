#### 解析器
##### XPath
##### XPathParser
- DOM(Document Object Model)解析方式  
- SAX (Simple API for XML)解析方式 推模式 解析部分XML  
- JDK 支持的 StAX (Streaming API for XML)解析方式 拉模式 解析部分XML 
#### 反射
Java 中的反射虽然功能强大,但是代码编写起来比较复杂且容易出错,为了简化反射操作的相关代码, MyBatis提供了专门的反射模块,该模块位于 org.apache.ibatis.reflection 包中,它对常见的反射操作做了进一步封装,提供了更加简洁方便的反射 API 。
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


#### KeyGenerator
insert 语句 id 自增主键生成策略


#### 主要类
![mybatis-component.png](https://upload-images.jianshu.io/upload_images/8128579-323324b91fb8a947.png?imageMogr2/auto-orient/strip%7CimageView2/2/w/1240)

#### 主要类的调用关系
![mybaits-component-use.png](https://upload-images.jianshu.io/upload_images/8128579-b07d52e3eae6f97e.png?imageMogr2/auto-orient/strip%7CimageView2/2/w/1240)