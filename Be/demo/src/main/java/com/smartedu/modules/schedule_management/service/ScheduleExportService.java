package com.smartedu.modules.schedule_management.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.rendering.PDFRenderer;
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

            byte[] pdfBytes = createPdf(option);

            byte[] outputBytes = pdfBytes;

            String extension = "pdf";

            if ("PNG".equals(format)) {
                try (PDDocument document = Loader.loadPDF(pdfBytes);
                        ByteArrayOutputStream imageOutput = new ByteArrayOutputStream()) {

                    PDFRenderer renderer = new PDFRenderer(document);

                    var image = renderer.renderImageWithDPI(0, 180);

                    javax.imageio.ImageIO.write(
                            image,
                            "png",
                            imageOutput
                    );

                    outputBytes = imageOutput.toByteArray();
                    extension = "png";
                }
            }

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

        try (PDDocument document = new PDDocument();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {

            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);

            try (PDPageContentStream content =
                         new PDPageContentStream(document, page)) {

                content.beginText();
                content.setFont(
                    new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD),
                    16
                );
                content.newLineAtOffset(50, 790);
                content.showText("WEEKLY STUDY SCHEDULE");
                content.endText();

                float y = 750;

                for (var item : option.getSections()) {

                    var section = item.getCourseSection();

                    String line = section.getCourse().getCourseCode()
                            + " - "
                            + section.getCourse().getCourseName()
                            + " - "
                            + section.getSectionCode();

                    content.beginText();
                    content.setFont(
                        new PDType1Font(Standard14Fonts.FontName.HELVETICA),
                        10
                    );
                    content.newLineAtOffset(50, y);
                    content.showText(line);
                    content.endText();

                    y -= 22;

                    for (var meeting : section.getMeetings()) {

                        String meetingText =
                                "Day " + meeting.getWeekday()
                                + ", period "
                                + meeting.getStartPeriod()
                                + "-"
                                + meeting.getEndPeriod();

                        content.beginText();
                        content.setFont(
                            new PDType1Font(Standard14Fonts.FontName.HELVETICA),
                            9
                        );
                        content.newLineAtOffset(70, y);
                        content.showText(meetingText);
                        content.endText();

                        y -= 18;
                    }

                    y -= 8;
                }
            }

            document.save(output);
            return output.toByteArray();
        }
    }
}
