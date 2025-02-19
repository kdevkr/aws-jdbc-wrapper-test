package kr.kdev.demo;

import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.DatabaseMetaData;

@Slf4j
@Component
@RequiredArgsConstructor
public class JdbcChecker implements ApplicationListener<ApplicationReadyEvent> {
    private final JdbcTemplate jdbcTemplate;

    @SneakyThrows
    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        DatabaseMetaData metaData = jdbcTemplate.getDataSource().getConnection().getMetaData();
        log.info("Use JDBC Driver: {}", metaData.getDriverName());
        log.info("Postgres Version: {}", jdbcTemplate.queryForObject("select version()", String.class));
    }
}
