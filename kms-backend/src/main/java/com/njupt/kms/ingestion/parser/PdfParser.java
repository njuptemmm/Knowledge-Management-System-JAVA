package com.njupt.kms.ingestion.parser;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

import java.io.InputStream;

@Component
public class PdfParser implements TextParser {

    @Override
    public String extension() {
        return "pdf";
    }

    @Override
    public String parse(InputStream input) throws Exception {
        try (PDDocument document = Loader.loadPDF(input.readAllBytes())) {
            PDFTextStripper stripper = new PDFTextStripper();
            // 按位置排序，避免多栏排版被读成乱序
            stripper.setSortByPosition(true);
            return stripper.getText(document);
        }
    }
}
