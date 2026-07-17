package com.unihire.services;

import com.unihire.dto.CandidateSummaryDto;
import com.unihire.models.CandidateProfile;
import com.unihire.models.Experience;
import com.unihire.repositories.CandidateProfileRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.context.annotation.Lazy;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class SemanticSearchService {

    private static final Logger logger = LoggerFactory.getLogger(SemanticSearchService.class);

    private final VectorStore vectorStore;
    private final CandidateProfileRepository candidateProfileRepository;
    private final TransactionTemplate transactionTemplate;
    private final SemanticSearchService self;

    public SemanticSearchService(VectorStore vectorStore, CandidateProfileRepository candidateProfileRepository, TransactionTemplate transactionTemplate, @Lazy SemanticSearchService self) {
        this.vectorStore = vectorStore;
        this.candidateProfileRepository = candidateProfileRepository;
        this.transactionTemplate = transactionTemplate;
        this.self = self;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void indexAllCandidatesOnStartup() {
        logger.info("Initializing vector store with existing candidates...");
        List<CandidateProfile> profiles = candidateProfileRepository.findAll();
        for (CandidateProfile profile : profiles) {
            self.indexCandidateProfileAsync(profile.getId());
        }
        logger.info("Indexed {} candidates.", profiles.size());
    }

    @Async
    public void indexCandidateProfileAsync(Long candidateId) {
        try {
            Document document = transactionTemplate.execute(status -> {
                CandidateProfile profile = candidateProfileRepository.findById(candidateId).orElse(null);
                if (profile == null) return null;

                // Build searchable content
                StringBuilder content = new StringBuilder();
                content.append("Name: ").append(profile.getFirstName()).append(" ").append(profile.getLastName()).append("\n");
                
                if (profile.getSkills() != null && !profile.getSkills().isEmpty()) {
                    content.append("Skills: ").append(String.join(", ", profile.getSkills())).append("\n");
                }
                
                if (profile.getExperiences() != null && !profile.getExperiences().isEmpty()) {
                    content.append("Experience:\n");
                    for (Experience exp : profile.getExperiences()) {
                        content.append("- ").append(exp.getJobTitle()).append(" at ").append(exp.getCompany()).append("\n");
                        if (exp.getDescription() != null) {
                            content.append("  ").append(exp.getDescription()).append("\n");
                        }
                    }
                }

                return new Document(
                        "candidate_" + candidateId,
                        content.toString(),
                        Map.of(
                                "candidateId", profile.getId(),
                                "name", profile.getFirstName() + " " + profile.getLastName(),
                                "skills", profile.getSkills() != null ? String.join(",", profile.getSkills()) : ""
                        )
                );
            });

            if (document == null) return;
            
            // Note: Some VectorStore implementations might not strictly upsert by ID.
            // Spring AI's PgVectorStore does handle upserts if ID is provided.
            vectorStore.add(List.of(document));
            logger.info("Successfully indexed candidate profile {} into pgvector", candidateId);
            
        } catch (Exception e) {
            logger.error("Failed to index candidate {}: {}", candidateId, e.getMessage());
        }
    }

    public List<CandidateSummaryDto> searchCandidates(String query, int topK) {
        List<Document> documents = vectorStore.similaritySearch(
                SearchRequest.builder().query(query).topK(topK).build()
        );

        return documents.stream().map(doc -> {
            CandidateSummaryDto dto = new CandidateSummaryDto();
            
            // Extract from metadata
            Map<String, Object> metadata = doc.getMetadata();
            if (metadata.containsKey("candidateId")) {
                dto.setCandidateId(Long.valueOf(metadata.get("candidateId").toString()));
            }
            if (metadata.containsKey("name")) {
                dto.setName(metadata.get("name").toString());
            }
            if (metadata.containsKey("skills")) {
                String skillsStr = metadata.get("skills").toString();
                if (!skillsStr.isEmpty()) {
                    dto.setTopSkills(List.of(skillsStr.split(",")));
                }
            }
            
            // Distance is sometimes in metadata or we can just leave it. 
            // In Spring AI, we might not have direct access to similarity score yet natively in the Document interface,
            // but let's see if distance is stored in metadata.
            if (metadata.containsKey("distance")) {
                Double distance = Double.valueOf(metadata.get("distance").toString());
                dto.setSimilarityScore(1.0 - distance); // COSINE distance -> similarity
            }

            return dto;
        }).collect(Collectors.toList());
    }
}
