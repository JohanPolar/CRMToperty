package com.johan.crmtoperty.proceso;

import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

/**
 * Evita que dos corridas del proceso se crucen (el scheduler y el botón manual, o
 * dos instancias de la aplicación) usando un advisory lock de Postgres.
 *
 * El candado vive en una conexión dedicada que se mantiene abierta durante la
 * corrida. Si la aplicación muere, Postgres cierra la conexión y lo libera solo:
 * no hay candados "huérfanos" como con una fila en una tabla.
 */
@Component
public class CandadoProceso {

    // Número arbitrario que identifica "el proceso de aplicaciones" dentro de Postgres
    private static final long LLAVE = 20260927L;

    private final DataSource dataSource;

    public CandadoProceso(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /** Devuelve el candado si estaba libre, o vacío si otra corrida lo tiene. No espera. */
    public Optional<Candado> intentarTomar() {
        Connection conexion = null;
        try {
            conexion = dataSource.getConnection();
            if (ejecutar(conexion, "SELECT pg_try_advisory_lock(?)")) {
                return Optional.of(new Candado(conexion));
            }
            conexion.close();
            return Optional.empty();
        } catch (SQLException e) {
            cerrarSinError(conexion);
            throw new IllegalStateException("No se pudo tomar el candado del proceso", e);
        }
    }

    private static boolean ejecutar(Connection conexion, String sql) throws SQLException {
        try (PreparedStatement sentencia = conexion.prepareStatement(sql)) {
            sentencia.setLong(1, LLAVE);
            try (ResultSet resultado = sentencia.executeQuery()) {
                return resultado.next() && resultado.getBoolean(1);
            }
        }
    }

    private static void cerrarSinError(Connection conexion) {
        if (conexion != null) {
            try {
                conexion.close();
            } catch (SQLException ignorada) {
                // ya estamos reportando el error original
            }
        }
    }

    /** Se usa con try-with-resources: al cerrarlo se libera el candado y se devuelve la conexión. */
    public static final class Candado implements AutoCloseable {

        private final Connection conexion;

        private Candado(Connection conexion) {
            this.conexion = conexion;
        }

        @Override
        public void close() {
            try {
                // Hay que liberarlo explícitamente: la conexión vuelve al pool, no se cierra
                ejecutar(conexion, "SELECT pg_advisory_unlock(?)");
            } catch (SQLException e) {
                throw new IllegalStateException("No se pudo liberar el candado del proceso", e);
            } finally {
                cerrarSinError(conexion);
            }
        }
    }
}
