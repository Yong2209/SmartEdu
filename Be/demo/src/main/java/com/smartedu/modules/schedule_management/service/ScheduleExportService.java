package com.smartedu.modules.schedule_management.service;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.Normalizer;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.smartedu.common.exception.BusinessException;
import com.smartedu.common.security.CurrentUserResolver;
import com.smartedu.modules.schedule_management.dto.ScheduleExportDtos;
import com.smartedu.modules.schedule_management.entity.ScheduleExport;
import com.smartedu.modules.schedule_management.repository.ScheduleExportRepository;
import com.smartedu.modules.schedule_management.repository.ScheduleOptionRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ScheduleExportService {

    private final ScheduleOptionRepository optionRepository;
    private final ScheduleExportRepository exportRepository;
    private final CurrentUserResolver currentUserResolver;

    private static final Path EXPORT_DIRECTORY =
            Paths.get("uploads", "schedule-exports");

    @Transactional
    public byte[] export(ScheduleExportDtos.ExportRequest request) {

        var user = currentUserResolver.requireCurrentUser();

        var option = optionRepository
                .findByIdAndScheduleGenerationRegistrationPlanUserId(
                        request.optionId(),
                        user.getId()
                )
                .orElseThrow(() -> new BusinessException(
                        "Không tìm thấy phương án.",
                        HttpStatus.NOT_FOUND
                ));

        String format = request.format().toUpperCase();

        try {
            Files.createDirectories(EXPORT_DIRECTORY);

            byte[] outputBytes = "PNG".equals(format) ? createPng(option) : createPdf(option);
            String extension = "PNG".equals(format) ? "png" : "pdf";

            String fileName = "schedule_" + option.getId()
                    + "_" + System.currentTimeMillis()
                    + "." + extension;

            Path filePath = EXPORT_DIRECTORY.resolve(fileName);

            Files.write(filePath, outputBytes);

            ScheduleExport export = ScheduleExport.builder()
                    .user(user)
                    .scheduleOption(option)
                    .exportFormat(format)
                    .filePath(filePath.toString())
                    .exportStatus("COMPLETED")
                    .fileSizeBytes((long) outputBytes.length)
                    .exportedAt(LocalDateTime.now())
                    .build();

            exportRepository.save(export);

            return outputBytes;

        } catch (IOException e) {
            throw new BusinessException(
                    "Không thể xuất thời khóa biểu: " + e.getMessage(),
                    HttpStatus.INTERNAL_SERVER_ERROR
            );
        }
    }

    private byte[] createPdf(
            com.smartedu.modules.schedule_management.entity.ScheduleOption option
    ) throws IOException {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PDFont bold = loadFont(document, true);
            PDFont regular = loadFont(document, false);
            List<String> rows = buildExportRows(option);
            PDPage page = null;
            PDPageContentStream content = null;
            float y = 0;
            try {
                for (String row : rows) {
                    if (content == null || y < 42) {
                        if (content != null) content.close();
                        page = new PDPage(new PDRectangle(PDRectangle.A4.getHeight(), PDRectangle.A4.getWidth()));
                        document.addPage(page);
                        content = new PDPageContentStream(document, page);
                        drawPdfLine(content, bold, "THỜI KHÓA BIỂU - " + option.getOptionName(), 12, 42, 555);
                        drawPdfLine(content, regular, "Tong tin chi: " + option.getTotalCredits()
                                + " | Diem xep hang: " + option.getTotalScore(), 9, 42, 535);
                        y = 512;
                    }
                    drawPdfLine(content, regular, row, 9, 42, y);
                    y -= 19;
                }
            } finally {
                if (content != null) content.close();
            }
            document.save(output);
            return output.toByteArray();
        }
    }

    private byte[] createPng(
            com.smartedu.modules.schedule_management.entity.ScheduleOption option
    ) throws IOException {
        List<String> rows = buildExportRows(option);
        int width = 1600;
        int height = Math.max(500, 180 + rows.size() * 42);
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, width, height);
            graphics.setColor(new Color(37, 42, 62));
            graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 28));
            graphics.drawString("Thời khóa biểu · " + safeImageText(option.getOptionName()), 48, 56);
            graphics.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 18));
            graphics.drawString("Tổng tín chỉ: " + option.getTotalCredits() + "   ·   Điểm xếp hạng: " + option.getTotalScore(), 48, 92);
            graphics.setColor(new Color(225, 228, 238));
            graphics.fillRect(48, 116, width - 96, 1);
            graphics.setColor(new Color(48, 56, 78));
            graphics.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 17));
            int y = 152;
            for (String row : rows) {
                graphics.drawString(safeImageText(row), 48, y);
                y += 42;
            }
        } finally {
            graphics.dispose();
        }
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            javax.imageio.ImageIO.write(image, "png", output);
            return output.toByteArray();
        }
    }

    private List<String> buildExportRows(
            com.smartedu.modules.schedule_management.entity.ScheduleOption option
    ) {
        List<String> rows = new ArrayList<>();
        for (var item : option.getSections()) {
            var section = item.getCourseSection();
            String course = section.getCourse().getCourseCode() + " - " + section.getCourse().getCourseName()
                    + " - Lop " + section.getSectionCode();
            for (var meeting : section.getMeetings()) {
                String room = meeting.getRoom() == null ? "Chua xep phong" : meeting.getRoom().getRoomName();
                String campus = meeting.getRoom() == null || meeting.getRoom().getCampus() == null
                        ? "" : " - Co so " + meeting.getRoom().getCampus().getCampusName();
                String time = meeting.getStartTime() != null && meeting.getEndTime() != null
                        ? meeting.getStartTime() + "-" + meeting.getEndTime()
                        : "tiet " + meeting.getStartPeriod() + "-" + meeting.getEndPeriod();
                String day = switch (meeting.getWeekday()) {
                    case 1 -> "Thu 2";
                    case 2 -> "Thu 3";
                    case 3 -> "Thu 4";
                    case 4 -> "Thu 5";
                    case 5 -> "Thu 6";
                    case 6 -> "Thu 7";
                    case 7 -> "Chu nhat";
                    default -> "Thu " + meeting.getWeekday();
                };
                String line = course + " | " + day + " " + time + " | Phong " + room + campus
                        + " | Giang vien " + (section.getInstructorName() == null ? "Chua phan cong" : section.getInstructorName());
                while (line.length() > 145) {
                    int splitAt = line.lastIndexOf(' ', 145);
                    if (splitAt < 40) splitAt = 145;
                    rows.add(line.substring(0, splitAt));
                    line = "  " + line.substring(splitAt).stripLeading();
                }
                rows.add(line);
            }
        }
        if (rows.isEmpty()) rows.add("Phuong an chua co buoi hoc nao.");
        return rows;
    }

    private PDFont loadFont(PDDocument document, boolean bold) throws IOException {
        String configuredPath = System.getenv("SMARTEDU_EXPORT_FONT_PATH");
        List<Path> candidates = new ArrayList<>();
        if (configuredPath != null && !configuredPath.isBlank()) {
            Path configured = Paths.get(configuredPath);
            if (bold) {
                String name = configured.getFileName().toString();
                String boldName = name.replaceAll("(?i)(\\.ttf)$", "-Bold$1");
                candidates.add(configured.resolveSibling(boldName));
            }
            candidates.add(configured);
        }
        if (bold) {
            candidates.add(Paths.get("C:/Windows/Fonts/arialbd.ttf"));
            candidates.add(Paths.get("/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"));
            candidates.add(Paths.get("/usr/share/fonts/truetype/liberation2/LiberationSans-Bold.ttf"));
        } else {
            candidates.add(Paths.get("C:/Windows/Fonts/arial.ttf"));
            candidates.add(Paths.get("/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf"));
            candidates.add(Paths.get("/usr/share/fonts/truetype/liberation2/LiberationSans-Regular.ttf"));
        }
        for (Path candidate : candidates) {
            if (Files.isRegularFile(candidate)) return PDType0Font.load(document, candidate.toFile());
        }
        return new PDType1Font(bold
                ? Standard14Fonts.FontName.HELVETICA_BOLD
                : Standard14Fonts.FontName.HELVETICA);
    }

    private void drawPdfLine(PDPageContentStream content, PDFont font, String text,
            int fontSize, float x, float y) throws IOException {
        content.beginText();
        content.setFont(font, fontSize);
        content.newLineAtOffset(x, y);
        content.showText(pdfSafe(font, text));
        content.endText();
    }

    private String pdfSafe(PDFont font, String text) throws IOException {
        if (text == null) return "";
        try {
            font.encode(text);
            return text;
        } catch (IOException unsupported) {
            // If no installed Unicode font covers a character, keep the content readable in the fallback font.
        }
        String normalized = Normalizer.normalize(text, Normalizer.Form.NFD)
                .replace("đ", "d").replace("Đ", "D");
        return normalized.replaceAll("\\p{M}", "")
                .replace("→", "-").replace("–", "-").replace("·", "|")
                .replaceAll("[^\\x20-\\x7E]", "?");
    }

    private String safeImageText(String text) {
        return text == null ? "" : text;
    }
}
