package ma.ensa.medtrend.dao;

import ma.ensa.medtrend.models.Lead;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * DAO implementation for CRUD operations on the leads table.
 */
public class LeadDaoImpl implements ILeadDao {

    private final Connection connection;

    public LeadDaoImpl() {
        this.connection = DatabaseManager.getInstance().getConnection();
    }

    @Override
    public boolean batchInsert(List<Lead> leads) {
        String sql = "INSERT INTO leads (url, email, hasWhatsApp, isSslValid, serverInfo, riskScore) "
                + "VALUES (?, ?, ?, ?, ?, ?)";

        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            connection.setAutoCommit(false);

            for (Lead lead : leads) {
                pstmt.setString(1, lead.getUrl());
                pstmt.setString(2, lead.getEmail());
                pstmt.setBoolean(3, lead.isHasWhatsApp());
                pstmt.setBoolean(4, lead.isSslValid());
                pstmt.setString(5, lead.getServerInfo());
                pstmt.setInt(6, lead.getRiskScore());
                pstmt.addBatch();
            }

            pstmt.executeBatch();
            connection.commit();
            connection.setAutoCommit(true);
            System.out.println("[LeadDaoImpl] " + leads.size() + " leads insérés avec succès.");
            return true;

        } catch (SQLException e) {
            System.err.println("[LeadDaoImpl] Erreur lors de l'insertion batch : " + e.getMessage());
            try {
                connection.rollback();
                connection.setAutoCommit(true);
            } catch (SQLException rollbackEx) {
                System.err.println("[LeadDaoImpl] Erreur lors du rollback : " + rollbackEx.getMessage());
            }
            return false;
        }
    }

    @Override
    public List<Lead> getAllLeads() {
        List<Lead> leads = new ArrayList<>();
        String sql = "SELECT url, email, hasWhatsApp, isSslValid, serverInfo, riskScore FROM leads";

        try (PreparedStatement pstmt = connection.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {

            while (rs.next()) {
                Lead lead = new Lead(rs.getString("url"));
                lead.setEmail(rs.getString("email"));
                lead.setHasWhatsApp(rs.getBoolean("hasWhatsApp"));
                lead.setSslValid(rs.getBoolean("isSslValid"));
                lead.setServerInfo(rs.getString("serverInfo"));
                lead.setRiskScore(rs.getInt("riskScore"));
                leads.add(lead);
            }

        } catch (SQLException e) {
            System.err.println("[LeadDaoImpl] Erreur lors de la récupération des leads : " + e.getMessage());
        }

        return leads;
    }

    /**
     * Deletes all rows from the leads table.
     */
    public boolean deleteAll() {
        try (Statement stmt = connection.createStatement()) {
            stmt.executeUpdate("DELETE FROM leads");
            System.out.println("[LeadDaoImpl] Tous les leads supprimés.");
            return true;
        } catch (SQLException e) {
            System.err.println("[LeadDaoImpl] Erreur lors de la suppression : " + e.getMessage());
            return false;
        }
    }
}
