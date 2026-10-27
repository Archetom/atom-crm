package io.github.archetom.crm.infra.persistence.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import org.apache.ibatis.reflection.MetaObject;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * @author hanfeng
 */
@Configuration
@MapperScan(basePackages ="io.github.archetom.crm.infra.persistence.mysql.mapper", sqlSessionTemplateRef = "sqlSessionTemplate")
public class MyBatisPlusConfig {

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new OptimisticLockerInnerInterceptor());
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        return interceptor;
    }

    @Component
    static class MyMetaObjectHandler implements MetaObjectHandler {
        //mp implementation add, method execute
        @Override
        public void insertFill(MetaObject metaObject) {
            LocalDateTime now = now();
            // Aggregates that participate in a fenced automation effect already
            // carry database UTC. Never replace that evidence with node-local time.
            fillIfMissing(metaObject, "createdTime", now);
            fillIfMissing(metaObject, "updatedTime", now);
        }

        //mp implementation, method execute
        @Override
        public void updateFill(MetaObject metaObject) {
            fillIfMissing(metaObject, "updatedTime", now());
        }

        private void fillIfMissing(MetaObject metaObject, String fieldName, LocalDateTime value) {
            if (metaObject.hasSetter(fieldName) && metaObject.getValue(fieldName) == null) {
                metaObject.setValue(fieldName, value);
            }
        }

        private LocalDateTime now() {
            LocalDateTime value = LocalDateTime.now();
            return value.withNano(value.getNano() / 1_000 * 1_000);
        }
    }
}
