package com.unihire.opensearch;

import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch.core.GetResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class CandidateSearchRepository {

    private static final Logger logger = LoggerFactory.getLogger(CandidateSearchRepository.class);
    private static final String INDEX_NAME = "candidates";

    private final OpenSearchClient client;

    public CandidateSearchRepository(OpenSearchClient client) {
        this.client = client;
    }

    public void save(CandidateDocument document) {
        try {
            client.index(i -> i
                    .index(INDEX_NAME)
                    .id(document.getId())
                    .document(document)
            );
        } catch (Exception e) {
            logger.error("Error saving candidate document: {}", e.getMessage(), e);
            throw new RuntimeException(e);
        }
    }

    public void deleteById(String id) {
        try {
            client.delete(d -> d
                    .index(INDEX_NAME)
                    .id(id)
            );
        } catch (Exception e) {
            logger.error("Error deleting candidate document {}: {}", id, e.getMessage(), e);
            throw new RuntimeException(e);
        }
    }

    public void deleteAll() {
        try {
            boolean exists = client.indices().exists(e -> e.index(INDEX_NAME)).value();
            if (exists) {
                client.deleteByQuery(d -> d
                        .index(INDEX_NAME)
                        .query(q -> q.matchAll(m -> m))
                );
            }
        } catch (Exception e) {
            logger.error("Error deleting all candidate documents: {}", e.getMessage(), e);
            throw new RuntimeException(e);
        }
    }

    public Optional<CandidateDocument> findById(String id) {
        try {
            GetResponse<CandidateDocument> response = client.get(g -> g
                            .index(INDEX_NAME)
                            .id(id),
                    CandidateDocument.class
            );
            return response.found() ? Optional.ofNullable(response.source()) : Optional.empty();
        } catch (Exception e) {
            logger.error("Error finding candidate document {}: {}", id, e.getMessage(), e);
            return Optional.empty();
        }
    }
}
