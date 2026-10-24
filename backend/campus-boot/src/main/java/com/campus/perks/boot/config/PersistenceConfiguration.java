package com.campus.perks.boot.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.campus.perks.common.api.PageQuery;
import org.apache.ibatis.annotations.Mapper;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(name = "campus.database.enabled", havingValue = "true")
@MapperScan(basePackages = "com.campus.perks", annotationClass = Mapper.class)
public class PersistenceConfiguration {
  @Bean
  MybatisPlusInterceptor mybatisPlusInterceptor() {
    var interceptor = new MybatisPlusInterceptor();
    var pagination = new PaginationInnerInterceptor(DbType.MYSQL);
    pagination.setMaxLimit((long) PageQuery.MAX_SIZE);
    interceptor.addInnerInterceptor(pagination);
    return interceptor;
  }
}
