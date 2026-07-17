package com.unihire.services.extractor;

import java.io.InputStream;

public interface DocumentTextExtractor {
    String extractText(InputStream inputStream, String filename);
}
