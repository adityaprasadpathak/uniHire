package com.unihire.services.ai;

import com.unihire.dto.ResumeEnhancementResult;
import com.unihire.dto.TargetCustomizationRequest;
import com.unihire.models.CandidateProfile;

public interface ResumeGenerationProvider {
    String getProviderName();
    ResumeEnhancementResult enhance(CandidateProfile profile, TargetCustomizationRequest request);
}
