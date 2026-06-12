package ma.ensa.medtrend.services;

import ma.ensa.medtrend.models.Lead;
import java.util.List;

public interface IScraperService {
    List<Lead> extractData(List<String> targetUrls);
}
