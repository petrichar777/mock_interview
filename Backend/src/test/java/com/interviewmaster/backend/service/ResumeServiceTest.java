package com.interviewmaster.backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.interviewmaster.backend.exception.BusinessException;
import com.interviewmaster.backend.model.dto.response.ResumeParseResponse;
import java.time.Duration;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mock.web.MockMultipartFile;

@ExtendWith(MockitoExtension.class)
class ResumeServiceTest {

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private HashOperations<String, Object, Object> hashOperations;

    private ResumeService resumeService;

    @BeforeEach
    void setUp() {
        resumeService = new ResumeService(stringRedisTemplate);
    }

    @Test
    void parse_resume_docx_success() throws Exception {
        when(stringRedisTemplate.opsForHash()).thenReturn(hashOperations);
        byte[] bytes = buildDocxBytes(
                "张三 | 13812345678 | zhangsan@example.com",
                "教育背景：北京大学 计算机科学与技术 本科 2018-2022",
                "工作经历：阿里巴巴 Java开发实习生 2021.06-2021.09",
                "项目经验：电商后台订单系统开发",
                "技能：Java、Spring Boot、MySQL"
        );
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "resume.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                bytes
        );

        ResumeParseResponse response = resumeService.parseResume(1L, file);

        assertEquals("SUCCESS", response.getParseStatus());
        assertTrue(response.getExtractedText().contains("张三"));
        assertTrue(response.getWarnings().isEmpty());
        verify(hashOperations).putAll(startsWith("interview:resume:file:"), anyMap());
        verify(stringRedisTemplate).expire(startsWith("interview:resume:file:"), any(Duration.class));
    }

    @Test
    void parse_resume_image_invalid_extension() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "resume.png",
                "image/png",
                new byte[] {1, 2, 3}
        );

        BusinessException ex = assertThrows(BusinessException.class, () -> resumeService.parseResume(2L, file));

        assertEquals("仅支持 pdf/doc/docx 格式", ex.getMessage());
    }

    @Test
    void parse_resume_invalid_extension() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "resume.txt",
                "text/plain",
                "hello".getBytes()
        );

        BusinessException ex = assertThrows(BusinessException.class, () -> resumeService.parseResume(1L, file));

        assertEquals("仅支持 pdf/doc/docx 格式", ex.getMessage());
    }

    private byte[] buildDocxBytes(String... paragraphs) throws Exception {
        try (XWPFDocument document = new XWPFDocument();
             java.io.ByteArrayOutputStream outputStream = new java.io.ByteArrayOutputStream()) {
            for (String paragraphText : paragraphs) {
                XWPFParagraph paragraph = document.createParagraph();
                paragraph.createRun().setText(paragraphText);
            }
            document.write(outputStream);
            return outputStream.toByteArray();
        }
    }
}
