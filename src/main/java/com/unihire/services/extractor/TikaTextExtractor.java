package com.unihire.services.extractor;

import org.apache.tika.Tika;
import org.springframework.stereotype.Component;

import java.io.InputStream;

@Component
public class TikaTextExtractor implements DocumentTextExtractor {

    private final Tika tika;

    public TikaTextExtractor() {
        this.tika = new Tika();
    }

    @Override
    public String extractText(InputStream inputStream, String filename) {
        try {
            return tika.parseToString(inputStream);
        } catch (Exception e) {
            throw new RuntimeException("Failed to extract text from document: " + filename, e);
        }
    }
}
