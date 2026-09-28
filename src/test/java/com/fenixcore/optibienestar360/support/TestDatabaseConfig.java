package com.fenixcore.optibienestar360.support;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * Fuente única de verdad para la conexión a PostgreSQL en tests.
 *
 * <h2>Precedencia (de mayor a menor)</h2>
 * <ol>
 *   <li>{@code System.getProperty("test.database.*")} — útil para
 *       {@code ./gradlew test -Dtest.database.url=...} sin tocar el entorno.</li>
 *   <li>Variables de entorno {@code DATABASE_URL}, {@code DATABASE_HOST},
 *       {@code DATABASE_PORT}, {@code DATABASE_NAME}, {@code DATABASE_USER},
 *       {@code DATABASE_PASSWORD} — son las que ya usan el CI y los
 *       entornos efímeros con Docker.</li>
 *   <li>Defaults {@code localhost:5415/optibienestar360_test} / {@code postgres}
 *       / {@code postgres} — alineados con {@code application-test.properties} y
 *       con la convención habitual de desarrollo local del equipo. Se elige
 *       el puerto 5415 (no 5432/5413) para no chocar con Postgres del sistema
 *       ni con el contenedor del perfil {@code dev}.</li>
 * </ol>
 *
 * <h2>Cómo correr los tests live</h2>
 * <pre>
 *   ./gradlew test                                  # los live se saltan (@EnabledIfSystemProperty)
 *   ./gradlew test -Djasper.liveTests=true          # se ejecutan contra la BD por defecto
 *   DATABASE_URL=jdbc:postgresql://db:5432/x ./gradlew test -Djasper.liveTests=true
 *   ./gradlew test -Djasper.liveTests=true \
 *                  -Dtest.database.url=jdbc:postgresql://localhost:5433/optibienestar360_test \
 *                  -Dtest.database.username=postgres -Dtest.database.password=secret
 * </pre>
 *
 * <p>Regla: métodos {@code static}, sin frameworks externos, solo
 * {@link DriverManager}. Los errores de red se registran en DEBUG y se
 * traducen a {@code false} en {@link #isAvailable()} para que los tests
 * puedan usar {@code Assumptions.assumeTrue(...)} y quedar como
 * <em>skipped</em> en el reporte, no como verdes silenciosos.</p>
 */
public final class TestDatabaseConfig {

	private static final Logger log = LoggerFactory.getLogger(TestDatabaseConfig.class);

	/** Defaults alineados con {@code .env.example} / {@code application-dev.properties}. */
	public static final String DEFAULT_HOST     = "localhost";
	public static final String DEFAULT_PORT     = "5432";
	public static final String DEFAULT_NAME     = "optibienestar360";
	public static final String DEFAULT_USER     = "optibienestar360_app";
	public static final String DEFAULT_PASSWORD = "changeme-dev";

	private TestDatabaseConfig() {
		// Utility class — no instanciable.
	}

	/** JDBC URL efectiva según la precedencia documentada arriba. */
	public static String jdbcUrl() {
		// (a) System property
		String prop = System.getProperty("test.database.url");
		if (isNotBlank(prop)) {
			return normalizeJdbcUrl(prop);
		}
		// (b) DATABASE_URL (puede venir como jdbc:, postgres:// o postgresql://)
		String envUrl = System.getenv("DATABASE_URL");
		if (isNotBlank(envUrl)) {
			return normalizeJdbcUrl(envUrl);
		}
		// (b) o composición a partir de piezas sueltas
		String host = firstNonBlank(System.getenv("DATABASE_HOST"), DEFAULT_HOST);
		String port = firstNonBlank(System.getenv("DATABASE_PORT"), DEFAULT_PORT);
		String name = firstNonBlank(System.getenv("DATABASE_NAME"), DEFAULT_NAME);
		return "jdbc:postgresql://" + host + ":" + port + "/" + name;
	}

	/** Usuario efectivo. */
	public static String username() {
		String prop = System.getProperty("test.database.username");
		if (isNotBlank(prop)) return prop;

		String envUser = System.getenv("DATABASE_USER");
		if (isNotBlank(envUser)) return envUser;

		// Si DATABASE_URL trae userinfo (postgres://user:pass@host/db) lo respetamos.
		String fromUrl = userFromDatabaseUrl();
		if (isNotBlank(fromUrl)) return fromUrl;

		return DEFAULT_USER;
	}

	/** Password efectiva. */
	public static String password() {
		String prop = System.getProperty("test.database.password");
		if (isNotBlank(prop)) return prop;

		String envPass = System.getenv("DATABASE_PASSWORD");
		if (isNotBlank(envPass)) return envPass;

		String fromUrl = passwordFromDatabaseUrl();
		if (isNotBlank(fromUrl)) return fromUrl;

		return DEFAULT_PASSWORD;
	}

	/**
	 * Abre una conexión nueva usando {@link DriverManager}.
	 * Propaga {@link SQLException} para que el llamador decida (normalmente
	 * tras comprobar {@link #isAvailable()} con {@code Assumptions}).
	 */
	public static Connection open() throws SQLException {
		return DriverManager.getConnection(jdbcUrl(), username(), password());
	}

	/**
	 * Comprueba disponibilidad sin lanzar. Devuelve {@code false} si el host
	 * está caído, las credenciales son inválidas o el driver no está en el
	 * classpath — en cuyo caso el test debería saltarse con {@code Assumptions}.
	 */
	public static boolean isAvailable() {
		try (Connection c = open()) {
			return c != null && !c.isClosed();
		} catch (SQLException e) {
			log.debug("BD no disponible en {} (user={}): {}", jdbcUrl(), username(), e.getMessage());
			return false;
		} catch (RuntimeException e) {
			// p.ej. ClassNotFoundException del driver envuelto por DriverManager
			log.debug("Error no SQL abriendo conexión a {}: {}", jdbcUrl(), e.getMessage());
			return false;
		}
	}

	// ------------------------------------------------------------------
	// Helpers
	// ------------------------------------------------------------------

	/**
	 * Acepta tanto {@code jdbc:postgresql://...} como {@code postgres://...}
	 * / {@code postgresql://...} (formato típico de Heroku/Railway/Docker)
	 * y devuelve siempre un JDBC URL válido para el driver de Postgres.
	 */
	private static String normalizeJdbcUrl(String raw) {
		String trimmed = raw.trim();
		if (trimmed.startsWith("jdbc:")) {
			return trimmed;
		}
		if (trimmed.startsWith("postgres://") || trimmed.startsWith("postgresql://")) {
			try {
				URI uri = URI.create(trimmed);
				String host = uri.getHost() != null ? uri.getHost() : DEFAULT_HOST;
				int port = uri.getPort() > 0 ? uri.getPort() : 5432;
				String path = uri.getPath() != null && !uri.getPath().isEmpty() ? uri.getPath() : "/" + DEFAULT_NAME;
				String query = uri.getQuery() != null ? "?" + uri.getQuery() : "";
				return "jdbc:postgresql://" + host + ":" + port + path + query;
			} catch (IllegalArgumentException ignored) {
				// Fallback defensivo
				return "jdbc:postgresql://" + trimmed.substring(trimmed.indexOf("://") + 3);
			}
		}
		// "host:port/db" sin esquema
		return "jdbc:postgresql://" + trimmed;
	}

	private static String userFromDatabaseUrl() {
		return userInfoPart(0);
	}

	private static String passwordFromDatabaseUrl() {
		return userInfoPart(1);
	}

	private static String userInfoPart(int index) {
		String envUrl = System.getenv("DATABASE_URL");
		if (!isNotBlank(envUrl)) return null;
		try {
			URI uri = URI.create(envUrl.trim());
			String info = uri.getUserInfo();
			if (!isNotBlank(info)) return null;
			String[] parts = info.split(":", 2);
			if (index < parts.length) return parts[index];
		} catch (IllegalArgumentException ignored) {
			// URL no parseable — dejamos que los env dedicados tomen el relevo.
		}
		return null;
	}

	private static boolean isNotBlank(String s) {
		return s != null && !s.isBlank();
	}

	private static String firstNonBlank(String... values) {
		for (String v : values) {
			if (isNotBlank(v)) return v;
		}
		return null;
	}

}
