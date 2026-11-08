package com.unihire.services.resume;

import com.unihire.models.CountryStandard;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
public class ResumeLocalizationEngine {

    public Map<String, Object> localizeResumeFormat(String profileJson, CountryStandard targetCountry) {
        // Here we apply specific logic to adjust the layout and sections
        // For instance, removing photo for US, adding certain sections for India, etc.
        Map<String, Object> localizedConfig = new HashMap<>();
        
        switch (targetCountry) {
            case US:
                localizedConfig.put("includePhoto", false);
                localizedConfig.put("maxPages", 2);
                localizedConfig.put("focus", "metric_impact");
                break;
            case INDIA:
                localizedConfig.put("includePhoto", true);
                localizedConfig.put("maxPages", 3);
                localizedConfig.put("focus", "tech_stack_projects");
                break;
            case EUROPE:
                localizedConfig.put("includePhoto", true);
                localizedConfig.put("maxPages", 2);
                localizedConfig.put("focus", "europass_standards");
                break;
            case MIDDLE_EAST:
                localizedConfig.put("includePhoto", true);
                localizedConfig.put("maxPages", 2);
                localizedConfig.put("focus", "personal_details");
                break;
            case APAC:
                localizedConfig.put("includePhoto", true);
                localizedConfig.put("maxPages", 2);
                localizedConfig.put("focus", "professional_summary");
                break;
            default:
                localizedConfig.put("includePhoto", false);
                localizedConfig.put("maxPages", 2);
                localizedConfig.put("focus", "standard");
        }
        
        return localizedConfig;
    }
}
