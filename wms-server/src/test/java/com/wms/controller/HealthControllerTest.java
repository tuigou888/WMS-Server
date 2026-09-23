package com.wms.controller;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import javax.sql.DataSource;
import java.sql.Connection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class HealthControllerTest {
    @Test
    void healthIsLivenessProbe() {
        HealthController controller = new HealthController(mock(DataSource.class));
        assertEquals("UP", controller.health().data().get("status"));
    }

    @Test
    void readyReturnsDatabaseUp() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.isValid(2)).thenReturn(true);

        var response = new HealthController(dataSource).ready();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("READY", response.getBody().data().get("status"));
        verify(connection).close();
    }

    @Test
    void readyReturnsUnavailableWhenDatabaseFails() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        when(dataSource.getConnection()).thenThrow(new RuntimeException("database down"));

        var response = new HealthController(dataSource).ready();

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
        assertEquals("NOT_READY", response.getBody().data().get("status"));
    }
}
