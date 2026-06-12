package ma.ensa.medtrend.dao;

import ma.ensa.medtrend.models.Lead;
import java.util.List;

public interface ILeadDao {
    boolean batchInsert(List<Lead> leads);
    List<Lead> getAllLeads();
}