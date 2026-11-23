package com.smartedu.modules.learning.service;

import java.io.InputStream;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.springframework.stereotype.Service;

@Service
public class DocumentExtractionService {

    public String extract(
        InputStream input,
        String extension
    ) throws Exception {

        if ("pdf".equalsIgnoreCase(extension)) {

            try (PDDocument document =
                     Loader.loadPDF(input.readAllBytes())) {

                PDFTextStripper stripper =
                    new PDFTextStripper();

                return stripper.getText(document);
            }
        }

        if ("docx".equalsIgnoreCase(extension)) {

            try (XWPFDocument document =
                     new XWPFDocument(input);
                 XWPFWordExtractor extractor =
                     new XWPFWordExtractor(document)) {

                return extractor.getText();
            }
        }

        throw new IllegalArgumentException(
            "Chỉ hỗ trợ PDF và DOCX"
        );
    }
}
