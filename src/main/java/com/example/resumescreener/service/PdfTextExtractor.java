package com.example.resumescreener.service;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class PdfTextExtractor {

    public String extractText(MultipartFile file) throws IOException {
        try (PDDocument document = PDDocument.load(file.getInputStream())) {
            PDFTextStripper stripper = new PDFTextStripper();
            return stripper.getText(document);
        }
    }

    public String extractEmail(String text) {
        Matcher matcher = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}").matcher(text);
        return matcher.find() ? matcher.group() : null;
    }

    public String extractPhone(String text) {
        Matcher matcher = Pattern.compile("(?:\\+?\\d{1,3}[-.\\s]?)?(?:\\(?\\d{2,4}\\)?[-.\\s]?)\\d{3}[-.\\s]?\\d{4}").matcher(text);
        return matcher.find() ? matcher.group() : null;
    }

    public String extractName(String text) {
        String cleaned = text == null ? "" : text.replaceAll("\\s+", " ").trim();
        if (cleaned.length() == 0) {
            return "Unknown Candidate";
        }
        String[] parts = cleaned.split("\\n");
        String firstLine = parts.length > 0 ? parts[0].trim() : cleaned;
        if (firstLine.length() > 120) {
            firstLine = firstLine.substring(0, 120);
        }
        return firstLine.isBlank() ? "Unknown Candidate" : firstLine;
    }
}
