package ma.ensa.medtrend.dao;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Singleton JDBC connection manager for the SQLite database.
 * Initialises the schema on first call to {@link #initDatabase()}.
 */
public class DatabaseManager {

    private static final String DB_URL = "jdbc:sqlite:medtrend.db";
    private static DatabaseManager instance;
    private Connection connection;

    private DatabaseManager() {
        try {
            this.connection = DriverManager.getConnection(DB_URL);
            System.out.println("[DatabaseManager] Connexion SQLite établie.");
        } catch (SQLException e) {
            System.err.println("[DatabaseManager] Erreur de connexion SQLite : " + e.getMessage());
        }
    }

    public static synchronized DatabaseManager getInstance() {
        if (instance == null) {
            instance = new DatabaseManager();
        }
        return instance;
    }

    public Connection getConnection() {
        return connection;
    }

    /**
     * Creates the leads table if it does not already exist.
     */
    public void initDatabase() {
        String sql = "CREATE TABLE IF NOT EXISTS leads ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                + "url TEXT, "
                + "email TEXT, "
                + "hasWhatsApp BOOLEAN, "
                + "isSslValid BOOLEAN, "
                + "serverInfo TEXT, "
                + "riskScore INTEGER"
                + ");";

        try (Statement stmt = connection.createStatement()) {
            stmt.execute(sql);
            System.out.println("[DatabaseManager] Table 'leads' initialisée avec succès.");
        } catch (SQLException e) {
            System.err.println("[DatabaseManager] Erreur lors de l'initialisation de la table : " + e.getMessage());
        }
    }
}
