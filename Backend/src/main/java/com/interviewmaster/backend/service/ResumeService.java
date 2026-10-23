package com.interviewmaster.backend.service;

import com.interviewmaster.backend.exception.BusinessException;
import com.interviewmaster.backend.model.dto.response.ResumeParseResponse;
import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hwpf.extractor.WordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@Service
@RequiredArgsConstructor
public class ResumeService {

    private static final long MAX_RESUME_FILE_SIZE = 5 * 1024 * 1024;
    private static final Duration RESUME_FILE_TTL = Duration.ofDays(7);
    private static final String RESUME_FILE_KEY_PREFIX = "interview:resume:file:";

    // Resume content detection thresholds
    private static final int RESUME_SCORE_PASS = 6;
    private static final int RESUME_SCORE_WARN = 3;

    // Hard signals — at least one must match for PASS (email/phone/education)
    private static final Pattern EMAIL_PATTERN = Pattern.compile("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}");
    private static final Pattern PHONE_PATTERN = Pattern.compile("1[3-9]\\d{9}");

    // Education (weight 2, hard signal)
    private static final Pattern EDU_PATTERN = Pattern.compile("教育背景|学历|毕业院校|大学|学院|专业|学位|本科|硕士|博士");

    // Work experience (weight 2) — narrowed: exclude standalone "工作"/"公司" which match requirements docs
    private static final Pattern WORK_PATTERN = Pattern.compile("工作经历|实习经历|就职|任职");

    // Project experience (weight 1) — narrowed: only explicit project section headers
    private static final Pattern PROJECT_PATTERN = Pattern.compile("项目经验|项目经历");

    // Date structure (weight 1) — reduced, dates appear in many non-resume docs
    private static final Pattern DATE_PATTERN = Pattern.compile("\\d{4}[./年]\\d{1,2}");

    private final StringRedisTemplate stringRedisTemplate;

    public ResumeParseResponse parseResume(Long userId, MultipartFile file) {
        if (userId == null) {
            throw new BusinessException(401, "未登录");
        }
        if (file == null || file.isEmpty()) {
            throw new BusinessException("请上传简历文件");
        }
        if (file.getSize() > MAX_RESUME_FILE_SIZE) {
            throw new BusinessException("文件大小不能超过5MB");
        }
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || originalFilename.isBlank()) {
            throw new BusinessException("文件名不能为空");
        }
        String extension = resolveExtension(originalFilename);
        if (!isAllowedExtension(extension)) {
            throw new BusinessException("仅支持 pdf/doc/docx 格式");
        }

        List<String> warnings = new ArrayList<>();
        String extractedText = "";
        String parseStatus = "SUCCESS";
        try {
            extractedText = extractText(file, extension, warnings);
            if (extractedText == null) {
                extractedText = "";
            }
            extractedText = extractedText.trim();
            if (extractedText.isBlank()) {
                parseStatus = warnings.isEmpty() ? "FAILED" : "PARTIAL";
                if (warnings.isEmpty()) {
                    warnings.add("未识别到可用文本");
                }
            } else {
                int resumeScore = detectResumeContent(extractedText);
                if (resumeScore < RESUME_SCORE_WARN) {
                    parseStatus = "FAILED";
                    warnings.add("未识别到简历内容，请上传简历文件");
                    extractedText = "";
                } else if (resumeScore < RESUME_SCORE_PASS) {
                    parseStatus = "PARTIAL";
                    warnings.add("内容识别度较低，请确认上传的是简历文件");
                } else if (!warnings.isEmpty()) {
                    parseStatus = "PARTIAL";
                }
            }
        } catch (Exception ex) {
            log.warn("Resume parse failed. userId={}, fileName={}", userId, originalFilename, ex);
            parseStatus = "FAILED";
            warnings.add("文件解析失败，请检查文件内容或格式");
            extractedText = "";
        }

        String resumeFileId = UUID.randomUUID().toString().replace("-", "");
        persistResumeFile(userId, resumeFileId, originalFilename, file.getSize(), parseStatus, extractedText);

        ResumeParseResponse response = new ResumeParseResponse();
        response.setResumeFileId(resumeFileId);
        response.setFileName(originalFilename);
        response.setFileSize(file.getSize());
        response.setParseStatus(parseStatus);
        response.setExtractedText(extractedText);
        response.setWarnings(warnings);
        return response;
    }

    private int detectResumeContent(String text) {
        if (text.length() < 30) {
            return 0;
        }

        boolean hasEmail = EMAIL_PATTERN.matcher(text).find();
        boolean hasPhone = PHONE_PATTERN.matcher(text).find();
        boolean hasEdu = EDU_PATTERN.matcher(text).find();
        boolean hasHardSignal = hasEmail || hasPhone || hasEdu;

        int score = 0;
        if (hasEmail) score += 3;
        if (hasPhone) score += 3;
        if (hasEdu) score += 2;
        if (WORK_PATTERN.matcher(text).find()) score += 2;
        if (PROJECT_PATTERN.matcher(text).find()) score += 1;
        if (DATE_PATTERN.matcher(text).find()) score += 1;

        // Hard gate: PASS requires a personal identifier signal
        // A requirements document has dates/projects but no email/phone/education
        if (score >= RESUME_SCORE_PASS && !hasHardSignal) {
            return RESUME_SCORE_WARN; // degrade to WARN
        }

        // Anti-pattern: pure code content with no resume keywords
        if (score == 0) {
            int codeChars = 0;
            int nonSpace = 0;
            for (int i = 0; i < text.length(); i++) {
                char c = text.charAt(i);
                if (c != ' ' && c != '\n' && c != '\r' && c != '\t') {
                    nonSpace++;
                    if (c == '{' || c == '}' || c == '(' || c == ')' || c == ';'
                            || c == '=' || c == '<' || c == '>' || c == '[' || c == ']') {
                        codeChars++;
                    }
                }
            }
            if (nonSpace > 0 && (double) codeChars / nonSpace > 0.3) {
                return 0;
            }
        }

        return score;
    }

    private void persistResumeFile(Long userId, String resumeFileId, String fileName, long fileSize, String parseStatus, String extractedText) {
        String key = RESUME_FILE_KEY_PREFIX + resumeFileId;
        Map<String, String> payload = new HashMap<>();
        payload.put("userId", String.valueOf(userId));
        payload.put("fileName", fileName);
        payload.put("fileSize", String.valueOf(fileSize));
        payload.put("parseStatus", parseStatus);
        payload.put("extractedText", extractedText == null ? "" : extractedText);
        stringRedisTemplate.opsForHash().putAll(key, payload);
        stringRedisTemplate.expire(key, RESUME_FILE_TTL);
    }

    private String extractText(MultipartFile file, String extension, List<String> warnings) throws IOException {
        return switch (extension) {
            case "pdf" -> extractPdfText(file);
            case "doc" -> extractDocText(file);
            case "docx" -> extractDocxText(file);
            default -> throw new BusinessException("仅支持 pdf/doc/docx 格式");
        };
    }

    private String extractPdfText(MultipartFile file) throws IOException {
        try (InputStream inputStream = file.getInputStream();
             PDDocument document = PDDocument.load(inputStream)) {
            PDFTextStripper stripper = new PDFTextStripper();
            return stripper.getText(document);
        }
    }

    private String extractDocText(MultipartFile file) throws IOException {
        try (InputStream inputStream = file.getInputStream();
             HWPFDocument document = new HWPFDocument(inputStream);
             WordExtractor extractor = new WordExtractor(document)) {
            return extractor.getText();
        }
    }

    private String extractDocxText(MultipartFile file) throws IOException {
        try (InputStream inputStream = file.getInputStream();
             XWPFDocument document = new XWPFDocument(inputStream)) {
            StringBuilder builder = new StringBuilder();
            for (XWPFParagraph paragraph : document.getParagraphs()) {
                if (paragraph == null || paragraph.getText() == null) {
                    continue;
                }
                if (!builder.isEmpty()) {
                    builder.append('\n');
                }
                builder.append(paragraph.getText());
            }
            return builder.toString();
        }
    }

    private boolean isAllowedExtension(String extension) {
        return "pdf".equals(extension)
                || "doc".equals(extension)
                || "docx".equals(extension);
    }

    private String resolveExtension(String fileName) {
        int lastDot = fileName.lastIndexOf('.');
        if (lastDot < 0 || lastDot == fileName.length() - 1) {
            throw new BusinessException("文件扩展名缺失");
        }
        return fileName.substring(lastDot + 1).toLowerCase(Locale.ROOT).trim();
    }
}
