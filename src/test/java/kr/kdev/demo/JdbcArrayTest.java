package kr.kdev.demo;

import com.google.gson.Gson;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import software.amazon.jdbc.wrapper.ArrayWrapper;

import javax.sql.DataSource;
import java.sql.Array;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@Slf4j
@SpringBootTest
class JdbcArrayTest {
    @Autowired
    JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("Amazon JDBC Wrapper")
    void getMetadata_thenContainsAwsJdbcWrapper() {
        assertDoesNotThrow(() -> {
            DataSource dataSource = jdbcTemplate.getDataSource();
            assertNotNull(dataSource);

            DatabaseMetaData metaData = dataSource.getConnection().getMetaData();
            assertNotNull(metaData);

            String driverName = metaData.getDriverName();
            assertTrue(driverName.contains("Advanced JDBC Wrapper"));
        });
    }

    @Test
    @DisplayName("Array using ResultSetExtractor")
    void selectArray_usingResultSetExtractor() {
        List<String> strings = jdbcTemplate.query("SELECT '{1,2,3,4}'::varchar[]", rs -> {
            if (rs.next()) {
                Array array = rs.getArray(1);
                return Arrays.asList((String[]) array.getArray());
            }
            return Collections.emptyList();
        });

        assertNotNull(strings);
        Assertions.assertFalse(strings.isEmpty());
    }

    @Test
    @DisplayName("Array using RowMapper")
    void selectArray_usingRowMapper() {
        String[] strings = jdbcTemplate.queryForObject("SELECT '{1,2,3,4}'::varchar[]", new RowMapper<String[]>() {
            @Override
            public String[] mapRow(ResultSet rs, int rowNum) throws SQLException {
                Array array = rs.getArray(1);
                Assertions.assertEquals(ArrayWrapper.class, array.getClass());
                return (String[]) array.getArray();
            }
        });
        assertNotNull(strings);
        assertEquals(4, strings.length);
    }

    @Test
    @DisplayName("JSON Array using RowMapper")
    void selectJsonArray_usingRowMapper() {
        String[] strings = jdbcTemplate.queryForObject("select '[0,1,2]'::jsonb", new RowMapper<String[]>() {
            @Override
            public String[] mapRow(ResultSet rs, int rowNum) throws SQLException {
                Array array = rs.getArray(1);
                Assertions.assertEquals(ArrayWrapper.class, array.getClass());
                return new Gson().fromJson(array.toString(), String[].class);
            }
        });
        assertNotNull(strings);
        assertEquals(3, strings.length);
    }
}
