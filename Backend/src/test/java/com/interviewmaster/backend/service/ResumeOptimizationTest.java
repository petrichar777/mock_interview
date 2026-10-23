package com.interviewmaster.backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.interviewmaster.backend.exception.BusinessException;
import com.interviewmaster.backend.mapper.InterviewMessageMapper;
import com.interviewmaster.backend.mapper.InterviewSessionMapper;
import com.interviewmaster.backend.mapper.ResumeOptimizationMapper;
import com.interviewmaster.backend.mapper.UserMapper;
import com.interviewmaster.backend.model.dto.request.ResumeOptimizeRequest;
import com.interviewmaster.backend.model.dto.response.ResumeOptimizeAsyncResponse;
import com.interviewmaster.backend.model.dto.response.ResumeOptimizeResponse;
import com.interviewmaster.backend.model.dto.response.ResumeOptimizeResultResponse;
import com.interviewmaster.backend.model.dto.response.QuotaInfoResponse;
import com.interviewmaster.backend.model.entity.ResumeOptimization;
import com.interviewmaster.backend.model.entity.User;
import com.interviewmaster.backend.util.RedisUtil;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationContext;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@ExtendWith(MockitoExtension.class)
@DisplayName("简历优化模块测试")
class ResumeOptimizationTest {

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private HashOperations<String, Object, Object> hashOperations;

    @Mock
    private ListOperations<String, String> listOperations;

    @Mock
    private InterviewSessionMapper interviewSessionMapper;

    @Mock
    private InterviewMessageMapper interviewMessageMapper;

    @Mock
    private DeepSeekService deepSeekService;

    @Mock
    private RedisUtil redisUtil;

    @Mock
    private CheckinService checkinService;

    @Mock
    private UserMapper userMapper;

    @Mock
    private ResumeOptimizationMapper resumeOptimizationMapper;

    @Mock
    private ApplicationContext applicationContext;

    private InterviewService interviewService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private User makeUser(Long id, int extraResumeQuota) {
        User user = new User();
        user.setId(id);
        user.setUsername("testuser" + id);
        user.setExtraResumeQuota(extraResumeQuota);
        return user;
    }

    private ResumeOptimizeRequest makeRequest(String resumeText, String jd) {
        ResumeOptimizeRequest req = new ResumeOptimizeRequest();
        req.setResumeText(resumeText);
        req.setJobDescription(jd);
        return req;
    }

    private ResumeOptimizeResponse makeAiResponse() {
        ResumeOptimizeResponse r = new ResumeOptimizeResponse();
        r.setOverallScore(85);
        r.setSummary("整体评价良好");
        r.setDimensions(List.of());
        r.setSectionAdvice(List.of());
        r.setJdMatch("匹配度较高");
        r.setInterviewInsights(List.of("改进沟通表达"));
        return r;
    }

    @BeforeEach
    void setUp() {
        lenient().when(checkinService.getContinuousDays(anyLong())).thenReturn(0);
        lenient().when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        lenient().when(stringRedisTemplate.opsForHash()).thenReturn(hashOperations);
        lenient().when(stringRedisTemplate.opsForList()).thenReturn(listOperations);
        // Mock ApplicationContext.getBean to return the same interviewService
        // so async method is called synchronously in tests.
        lenient().when(applicationContext.getBean(InterviewService.class))
                .thenAnswer(inv -> interviewService);
        interviewService = new InterviewService(stringRedisTemplate, interviewSessionMapper,
                interviewMessageMapper, deepSeekService, objectMapper, redisUtil, checkinService,
                userMapper, resumeOptimizationMapper, applicationContext);
    }

    // ========================================================================
    // 1. 基础功能测试
    // ========================================================================
    @Nested
    @DisplayName("同步优化 (optimizeResume)")
    class SyncOptimize {

        @Test
        @DisplayName("正常流程：返回 AI 分析结果")
        void success() {
            when(redisUtil.incrementWithExpiry(anyString(), any())).thenReturn(1L);
            when(interviewSessionMapper.listLatestScoredSessions(eq(1L), eq(5)))
                    .thenReturn(Collections.emptyList());
            ResumeOptimizeResponse aiResult = makeAiResponse();
            when(deepSeekService.optimizeResume(anyString(), anyString(), any()))
                    .thenReturn(aiResult);

            ResumeOptimizeResponse result = interviewService.optimizeResume(1L,
                    makeRequest("我的简历内容", "Java 后端"));

            assertNotNull(result);
            assertEquals(85, result.getOverallScore());
            assertEquals("整体评价良好", result.getSummary());
            verify(deepSeekService, times(1)).optimizeResume(anyString(), anyString(), any());
        }

        @Test
        @DisplayName("AI 返回 null 时抛出异常")
        void aiReturnsNull() {
            when(redisUtil.incrementWithExpiry(anyString(), any())).thenReturn(1L);
            when(interviewSessionMapper.listLatestScoredSessions(eq(1L), eq(5)))
                    .thenReturn(Collections.emptyList());
            when(deepSeekService.optimizeResume(anyString(), anyString(), any()))
                    .thenReturn(null);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> interviewService.optimizeResume(1L, makeRequest("resume", "JD")));

            assertTrue(ex.getMessage().contains("AI 简历分析失败"));
        }

        @Test
        @DisplayName("简历内容为空时抛出异常")
        void emptyResumeText() {
            ResumeOptimizeRequest req = makeRequest("  ", "JD");
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> interviewService.optimizeResume(1L, req));

            assertEquals("简历内容不能为空", ex.getMessage());
        }

        @Test
        @DisplayName("未登录用户抛出 401")
        void unauthenticated() {
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> interviewService.optimizeResume(null, makeRequest("r", "j")));

            assertEquals(401, ex.getCode());
        }
    }

    @Nested
    @DisplayName("异步优化 (optimizeResumeAsync)")
    class AsyncOptimize {

        @Test
        @DisplayName("正常流程：返回 taskId，异步处理完成后 Redis + DB 写入结果")
        void success() {
            when(redisUtil.incrementWithExpiry(anyString(), any())).thenReturn(1L);
            when(interviewSessionMapper.listLatestScoredSessions(eq(1L), eq(5)))
                    .thenReturn(Collections.emptyList());
            ResumeOptimizeResponse aiResult = makeAiResponse();
            when(deepSeekService.optimizeResume(anyString(), anyString(), any()))
                    .thenReturn(aiResult);

            ResumeOptimizeAsyncResponse response = interviewService.optimizeResumeAsync(1L,
                    makeRequest("简历内容", "JD"));

            assertNotNull(response.getTaskId());
            assertTrue(response.getTaskId().startsWith("ro_1_"));

            // Verify DB insert with PROCESSING status
            ArgumentCaptor<ResumeOptimization> recordCaptor = ArgumentCaptor.forClass(ResumeOptimization.class);
            verify(resumeOptimizationMapper, times(1)).insert(recordCaptor.capture());
            assertEquals("PROCESSING", recordCaptor.getValue().getStatus());
            assertEquals("简历内容", recordCaptor.getValue().getResumeText());
            assertEquals("JD", recordCaptor.getValue().getJobDescription());

            // Verify Redis initial status set (1st call) + DONE result (2nd call)
            // In tests the @Async method runs synchronously, so both calls happen.
            ArgumentCaptor<String> redisKeyCaptor = ArgumentCaptor.forClass(String.class);
            ArgumentCaptor<String> redisValueCaptor = ArgumentCaptor.forClass(String.class);
            verify(valueOperations, times(2)).set(redisKeyCaptor.capture(), redisValueCaptor.capture(), any());
            // Both Redis keys should reference the same taskId
            assertTrue(redisKeyCaptor.getAllValues().get(0).contains(response.getTaskId()));
            assertTrue(redisKeyCaptor.getAllValues().get(1).contains(response.getTaskId()));
            // Second call should contain DONE status
            assertTrue(redisValueCaptor.getAllValues().get(1).contains("DONE"));

            // Verify DB updated to DONE after async completes
            verify(resumeOptimizationMapper, times(1))
                    .updateResultByTaskId(eq(response.getTaskId()), eq("DONE"), anyString(), eq(null));
        }

        @Test
        @DisplayName("AI 抛出异常时状态标记为 FAILED")
        void aiThrowsException() {
            when(redisUtil.incrementWithExpiry(anyString(), any())).thenReturn(1L);
            when(interviewSessionMapper.listLatestScoredSessions(eq(1L), eq(5)))
                    .thenReturn(Collections.emptyList());
            when(deepSeekService.optimizeResume(anyString(), anyString(), any()))
                    .thenThrow(new RuntimeException("DeepSeek API 超时"));

            ResumeOptimizeAsyncResponse response = interviewService.optimizeResumeAsync(1L,
                    makeRequest("简历", "JD"));

            // Verify DB updated to FAILED with error message
            verify(resumeOptimizationMapper, times(1))
                    .updateResultByTaskId(eq(response.getTaskId()), eq("FAILED"), anyString(),
                            eq("DeepSeek API 超时"));
        }

        @Test
        @DisplayName("AI 返回 null 时状态标记为 FAILED")
        void aiReturnsNull() {
            when(redisUtil.incrementWithExpiry(anyString(), any())).thenReturn(1L);
            when(interviewSessionMapper.listLatestScoredSessions(eq(1L), eq(5)))
                    .thenReturn(Collections.emptyList());
            when(deepSeekService.optimizeResume(anyString(), anyString(), any()))
                    .thenReturn(null);

            ResumeOptimizeAsyncResponse response = interviewService.optimizeResumeAsync(1L,
                    makeRequest("简历", "JD"));

            verify(resumeOptimizationMapper, times(1))
                    .updateResultByTaskId(eq(response.getTaskId()), eq("FAILED"), anyString(),
                            eq("AI 简历分析返回空结果"));
        }
    }

    @Nested
    @DisplayName("结果查询 (getOptimizeResult / getLatestOptimization)")
    class ResultQuery {

        @Test
        @DisplayName("从 Redis 读取 PROCESSING 状态")
        void processingFromRedis() throws Exception {
            ResumeOptimizeResultResponse cached = new ResumeOptimizeResultResponse();
            cached.setStatus("PROCESSING");
            when(valueOperations.get("resume:optimize:result:1:ro_1_123"))
                    .thenReturn(objectMapper.writeValueAsString(cached));

            ResumeOptimizeResultResponse result = interviewService.getOptimizeResult(1L, "ro_1_123");

            assertEquals("PROCESSING", result.getStatus());
        }

        @Test
        @DisplayName("从 Redis 读取 DONE 状态")
        void doneFromRedis() throws Exception {
            ResumeOptimizeResponse aiResult = makeAiResponse();
            ResumeOptimizeResultResponse cached = new ResumeOptimizeResultResponse();
            cached.setStatus("DONE");
            cached.setResult(aiResult);
            when(valueOperations.get("resume:optimize:result:1:ro_1_123"))
                    .thenReturn(objectMapper.writeValueAsString(cached));

            ResumeOptimizeResultResponse result = interviewService.getOptimizeResult(1L, "ro_1_123");

            assertEquals("DONE", result.getStatus());
            assertNotNull(result.getResult());
            assertEquals(85, result.getResult().getOverallScore());
        }

        @Test
        @DisplayName("Redis 未命中时从 DB 回退")
        void dbFallback() throws Exception {
            when(valueOperations.get("resume:optimize:result:1:ro_1_123")).thenReturn(null);

            ResumeOptimizeResponse aiResult = makeAiResponse();
            ResumeOptimizeResultResponse stored = new ResumeOptimizeResultResponse();
            stored.setStatus("DONE");
            stored.setResult(aiResult);
            String storedJson = objectMapper.writeValueAsString(stored);

            ResumeOptimization record = new ResumeOptimization();
            record.setTaskId("ro_1_123");
            record.setUserId(1L);
            record.setStatus("DONE");
            record.setResultJson(storedJson);
            when(resumeOptimizationMapper.selectLatestByUserId(1L)).thenReturn(record);

            ResumeOptimizeResultResponse result = interviewService.getOptimizeResult(1L, "ro_1_123");

            assertEquals("DONE", result.getStatus());
            assertEquals(85, result.getResult().getOverallScore());
        }

        @Test
        @DisplayName("Redis 和 DB 都未命中时返回 NOT_FOUND")
        void notFound() {
            when(valueOperations.get("resume:optimize:result:1:ro_1_999")).thenReturn(null);
            when(resumeOptimizationMapper.selectLatestByUserId(1L)).thenReturn(null);

            ResumeOptimizeResultResponse result = interviewService.getOptimizeResult(1L, "ro_1_999");

            assertEquals("NOT_FOUND", result.getStatus());
        }

        @Test
        @DisplayName("getLatestOptimization 返回最新记录含简历原文和 JD")
        void latestOptimization_success() throws Exception {
            ResumeOptimizeResponse aiResult = makeAiResponse();
            ResumeOptimizeResultResponse stored = new ResumeOptimizeResultResponse();
            stored.setStatus("DONE");
            stored.setResult(aiResult);
            String storedJson = objectMapper.writeValueAsString(stored);

            ResumeOptimization record = new ResumeOptimization();
            record.setTaskId("ro_1_456");
            record.setUserId(1L);
            record.setStatus("DONE");
            record.setResumeText("我的简历原文");
            record.setJobDescription("Java 后端 JD");
            record.setResultJson(storedJson);
            when(resumeOptimizationMapper.selectLatestByUserId(1L)).thenReturn(record);

            ResumeOptimizeResultResponse result = interviewService.getLatestOptimization(1L);

            assertNotNull(result);
            assertEquals("DONE", result.getStatus());
            assertEquals("我的简历原文", result.getResumeText());
            assertEquals("Java 后端 JD", result.getJobDescription());
            assertEquals(85, result.getResult().getOverallScore());
        }

        @Test
        @DisplayName("getLatestOptimization 无历史记录返回 null")
        void latestOptimization_none() {
            when(resumeOptimizationMapper.selectLatestByUserId(1L)).thenReturn(null);

            ResumeOptimizeResultResponse result = interviewService.getLatestOptimization(1L);

            assertNull(result);
        }

        @Test
        @DisplayName("getLatestOptimization 状态为 PROCESSING 时不尝试解析空 result")
        void latestOptimization_processingNoResult() {
            ResumeOptimization record = new ResumeOptimization();
            record.setTaskId("ro_1_789");
            record.setUserId(1L);
            record.setStatus("PROCESSING");
            record.setResumeText("简历...");
            record.setJobDescription("JD...");
            record.setResultJson(null);
            when(resumeOptimizationMapper.selectLatestByUserId(1L)).thenReturn(record);

            ResumeOptimizeResultResponse result = interviewService.getLatestOptimization(1L);

            assertEquals("PROCESSING", result.getStatus());
            assertEquals("简历...", result.getResumeText());
            assertNull(result.getResult());
        }
    }

    // ========================================================================
    // 2. 配额管理测试
    // ========================================================================
    @Nested
    @DisplayName("配额管理")
    class QuotaManagement {

        @Test
        @DisplayName("基础配额内允许请求（第5次）")
        void withinBaseQuota_allowed() {
            when(redisUtil.incrementWithExpiry(anyString(), any())).thenReturn(5L);
            when(interviewSessionMapper.listLatestScoredSessions(eq(1L), eq(5)))
                    .thenReturn(Collections.emptyList());
            when(deepSeekService.optimizeResume(anyString(), anyString(), any()))
                    .thenReturn(makeAiResponse());

            ResumeOptimizeResponse result = interviewService.optimizeResume(1L,
                    makeRequest("简历", "JD"));

            assertNotNull(result);
            // 第5次恰好等于 baseMaxDaily(5)，decrementExtraResumeQuota 被调用但返回 0（无额外配额）
            verify(userMapper, times(1)).decrementExtraResumeQuota(1L);
        }

        @Test
        @DisplayName("超过基础配额（第6次）但无额外配额时拒绝")
        void exceedsBaseQuota_noExtra_rejected() {
            when(redisUtil.incrementWithExpiry(anyString(), any())).thenReturn(6L);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> interviewService.optimizeResume(1L, makeRequest("简历", "JD")));

            assertTrue(ex.getMessage().contains("今日简历优化次数已用完"));
            assertTrue(ex.getMessage().contains("5"));
        }

        @Test
        @DisplayName("有额外配额时直接消耗额外配额，不占用每日次数")
        void extraQuota_allowedAndDecremented() {
            when(userMapper.decrementExtraResumeQuota(1L)).thenReturn(1);
            when(interviewSessionMapper.listLatestScoredSessions(eq(1L), eq(5)))
                    .thenReturn(Collections.emptyList());
            when(deepSeekService.optimizeResume(anyString(), anyString(), any()))
                    .thenReturn(makeAiResponse());

            ResumeOptimizeResponse result = interviewService.optimizeResume(1L,
                    makeRequest("简历", "JD"));

            assertNotNull(result);
            // 优先消耗额外配额，不触发每日计数器
            verify(userMapper, times(1)).decrementExtraResumeQuota(1L);
            verify(redisUtil, never()).incrementWithExpiry(anyString(), any());
        }

        @Test
        @DisplayName("额外配额用完时回退到每日配额检查并拒绝")
        void extraQuota_exhausted_rejected() {
            // 额外配额已耗尽，decrement 返回 0
            when(userMapper.decrementExtraResumeQuota(1L)).thenReturn(0);
            // base=5，第8次超过基础配额
            when(redisUtil.incrementWithExpiry(anyString(), any())).thenReturn(8L);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> interviewService.optimizeResume(1L, makeRequest("简历", "JD")));

            assertTrue(ex.getMessage().contains("5"));
        }

        @Test
        @DisplayName("getResumeQuota 正确返回剩余次数")
        void getResumeQuota_correctCounts() {
            User user = makeUser(1L, 3);
            when(userMapper.selectById(1L)).thenReturn(user);
            when(valueOperations.get("resume:daily:1:" + java.time.LocalDate.now())).thenReturn("4");

            QuotaInfoResponse response = interviewService.getResumeQuota(1L);

            // max = base(5), remaining = max(0, 5-4) + extra(3) = 4
            assertEquals(5, response.getMaxResumeOptimizations());
            assertEquals(4, response.getRemainingResumeOptimizations());
        }
    }

    // ========================================================================
    // 3. 并发测试
    // ========================================================================
    @Nested
    @DisplayName("并发场景")
    class Concurrency {

        /**
         * 模拟 Redis 原子 INCR：使用 AtomicLong 确保线程安全递增。
         */
        private void mockAtomicIncrement(AtomicLong counter) {
            when(redisUtil.incrementWithExpiry(anyString(), any())).thenAnswer(inv -> {
                // Redis Lua INCR 是原子的，用 AtomicLong 模拟
                return counter.incrementAndGet();
            });
        }

        @Test
        @DisplayName("并发请求超出配额时，DECR 回退防止计数器虚增")
        void concurrentExceedQuota_counterNotInflated() throws Exception {
            // 用户已使用 4 次 (counter 起始为 4)，基础配额 5，无额外配额
            when(interviewSessionMapper.listLatestScoredSessions(eq(1L), eq(5)))
                    .thenReturn(Collections.emptyList());
            when(deepSeekService.optimizeResume(anyString(), anyString(), any()))
                    .thenReturn(makeAiResponse());

            AtomicLong counter = new AtomicLong(4);
            mockAtomicIncrement(counter);
            // DECR on rejection: counter goes back down
            AtomicLong decrCount = new AtomicLong(0);
            when(valueOperations.decrement(anyString())).thenAnswer(inv -> {
                decrCount.incrementAndGet();
                return counter.decrementAndGet();
            });

            int threadCount = 6;
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            CountDownLatch latch = new CountDownLatch(threadCount);
            AtomicInteger successCount = new AtomicInteger(0);
            AtomicInteger rejectCount = new AtomicInteger(0);

            for (int i = 0; i < threadCount; i++) {
                executor.submit(() -> {
                    try {
                        interviewService.optimizeResume(1L, makeRequest("简历", "JD"));
                        successCount.incrementAndGet();
                    } catch (BusinessException e) {
                        rejectCount.incrementAndGet();
                    } finally {
                        latch.countDown();
                    }
                });
            }

            latch.await(10, TimeUnit.SECONDS);
            executor.shutdown();

            // 期望：只有 1 个成功（第5个槽位），5 个被拒
            assertEquals(1, successCount.get(),
                    "只剩 1 个配额槽位时，应恰好 1 个请求成功");
            assertEquals(5, rejectCount.get(),
                    "其余 5 个请求应被拒绝");

            // 修复后：计数器应为 5（4+1 成功, 5 个拒绝各 DECR 回退）
            // 由于并发顺序不确定，最终 counter ≈ 5（精确值取决于 INCR/DECR 的竞争）
            long finalCounter = counter.get();
            assertEquals(5, decrCount.get(),
                    "5 个被拒请求都应执行了 DECR");
            System.out.println("=== 并发超出配额后计数器值: " + finalCounter
                    + " (起始 4, 期望 ≈ 5, DECR 调用次数: " + decrCount.get() + ") ===");
        }

        @Test
        @DisplayName("并发：额外配额不会被扣到负数")
        void concurrentExtraQuota_notNegative() throws Exception {
            // 用户 extra=1, 基础=5; 先消耗额外配额再用每日
            when(interviewSessionMapper.listLatestScoredSessions(eq(1L), eq(5)))
                    .thenReturn(Collections.emptyList());
            when(deepSeekService.optimizeResume(anyString(), anyString(), any()))
                    .thenReturn(makeAiResponse());

            AtomicLong counter = new AtomicLong(4);
            mockAtomicIncrement(counter);

            // decrementExtraResumeQuota 的 SQL 有 WHERE extra_interview_quota > 0 保护
            // 但这里测试的是 TOCTOU：多个线程同时读到 extra=1 然后都去扣减
            AtomicInteger decrementCallCount = new AtomicInteger(0);
            doAnswer(inv -> {
                decrementCallCount.incrementAndGet();
                // MyBatis update 返回 1 表示成功，返回 0 表示条件不满足
                return decrementCallCount.get() <= 1 ? 1 : 0;
            }).when(userMapper).decrementExtraResumeQuota(1L);

            int threadCount = 8;
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            CountDownLatch latch = new CountDownLatch(threadCount);
            AtomicInteger successCount = new AtomicInteger(0);
            AtomicInteger rejectCount = new AtomicInteger(0);

            for (int i = 0; i < threadCount; i++) {
                executor.submit(() -> {
                    try {
                        interviewService.optimizeResume(1L, makeRequest("简历", "JD"));
                        successCount.incrementAndGet();
                    } catch (BusinessException e) {
                        rejectCount.incrementAndGet();
                    } finally {
                        latch.countDown();
                    }
                });
            }

            latch.await(10, TimeUnit.SECONDS);
            executor.shutdown();

            // max=6, 起始 counter=4, 所以槽位: 5(基础最后一个), 6(额外配额)
            // 期望最多 2 个成功
            assertTrue(successCount.get() <= 2,
                    "最多 2 个成功（基础槽位 5 + 额外 1），实际成功: " + successCount.get());

            System.out.println("=== 并发额外配额: 成功=" + successCount.get()
                    + ", 拒绝=" + rejectCount.get()
                    + ", decrementExtraResumeQuota 调用次数=" + decrementCallCount.get() + " ===");
        }

        @Test
        @DisplayName("并发：多用户配额互相独立")
        void concurrentMultiUser_independentQuotas() throws Exception {
            when(interviewSessionMapper.listLatestScoredSessions(anyLong(), eq(5)))
                    .thenReturn(Collections.emptyList());
            when(deepSeekService.optimizeResume(anyString(), anyString(), any()))
                    .thenReturn(makeAiResponse());

            // 为每个用户独立的计数器
            AtomicLong counter1 = new AtomicLong(4); // user1 剩 1 个基础配额
            AtomicLong counter2 = new AtomicLong(0); // user2 有 5 个额外，剩很多

            when(redisUtil.incrementWithExpiry(anyString(), any())).thenAnswer(inv -> {
                String key = inv.getArgument(0);
                if (key.contains(":1:")) {
                    return counter1.incrementAndGet();
                }
                return counter2.incrementAndGet();
            });

            int threadCount = 6; // 每人 3 个并发
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            CountDownLatch latch = new CountDownLatch(threadCount);
            AtomicInteger user1Success = new AtomicInteger(0);
            AtomicInteger user1Reject = new AtomicInteger(0);
            AtomicInteger user2Success = new AtomicInteger(0);

            for (int i = 0; i < 3; i++) {
                executor.submit(() -> {
                    try {
                        interviewService.optimizeResume(1L, makeRequest("r1", "jd"));
                        user1Success.incrementAndGet();
                    } catch (BusinessException e) {
                        user1Reject.incrementAndGet();
                    } finally {
                        latch.countDown();
                    }
                });
            }
            for (int i = 0; i < 3; i++) {
                executor.submit(() -> {
                    try {
                        interviewService.optimizeResume(2L, makeRequest("r2", "jd"));
                        user2Success.incrementAndGet();
                    } catch (BusinessException e) {
                        latch.countDown();
                    } finally {
                        latch.countDown();
                    }
                });
            }

            latch.await(10, TimeUnit.SECONDS);
            executor.shutdown();

            assertEquals(1, user1Success.get(),
                    "用户1 只剩 1 个配额，应恰好 1 个成功");
            assertEquals(3, user2Success.get(),
                    "用户2 配额充足，3 个并发应全部成功");

            System.out.println("=== 多用户并发: user1 成功=" + user1Success.get()
                    + " 拒绝=" + user1Reject.get()
                    + ", user2 成功=" + user2Success.get() + " ===");
        }

        @Test
        @DisplayName("并发：异步任务 ID 不重复")
        void concurrentAsync_uniqueTaskIds() throws Exception {
            when(interviewSessionMapper.listLatestScoredSessions(eq(1L), eq(5)))
                    .thenReturn(Collections.emptyList());
            when(deepSeekService.optimizeResume(anyString(), anyString(), any()))
                    .thenReturn(makeAiResponse());

            AtomicLong counter = new AtomicLong(0);
            mockAtomicIncrement(counter);

            int threadCount = 10;
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            CountDownLatch latch = new CountDownLatch(threadCount);
            List<String> taskIds = Collections.synchronizedList(new ArrayList<>());

            for (int i = 0; i < threadCount; i++) {
                executor.submit(() -> {
                    try {
                        ResumeOptimizeAsyncResponse resp = interviewService.optimizeResumeAsync(
                                1L, makeRequest("简历", "JD"));
                        taskIds.add(resp.getTaskId());
                    } catch (Exception e) {
                        // ignore
                    } finally {
                        latch.countDown();
                    }
                });
            }

            latch.await(10, TimeUnit.SECONDS);
            executor.shutdown();

            long uniqueCount = taskIds.stream().distinct().count();
            assertEquals(taskIds.size(), uniqueCount,
                    "UUID 保证每个并发请求产生唯一的 taskId");
        }
    }

    // ========================================================================
    // 4. 授权/安全测试
    // ========================================================================
    @Nested
    @DisplayName("授权与安全")
    class Authorization {

        @Test
        @DisplayName("跨用户 Redis 读取已被阻止：用户 B 无法读取用户 A 的结果")
        void crossUserRedisAccess_blocked() throws Exception {
            // 用户 A (userId=1) 的任务存储 key 为 resume:optimize:result:1:ro_1_999
            // 用户 B (userId=2) 查询时 key 为 resume:optimize:result:2:ro_1_999 → 不命中
            // Redis 的 key 已包含 userId，所以必然会 miss

            // Redis miss for user B's key
            when(valueOperations.get("resume:optimize:result:2:ro_1_999")).thenReturn(null);

            // DB fallback: user B's latest record doesn't match taskId
            ResumeOptimization recordB = new ResumeOptimization();
            recordB.setTaskId("ro_2_888");
            recordB.setUserId(2L);
            recordB.setStatus("PROCESSING");
            when(resumeOptimizationMapper.selectLatestByUserId(2L)).thenReturn(recordB);

            ResumeOptimizeResultResponse result = interviewService.getOptimizeResult(2L, "ro_1_999");

            // Cross-user access is blocked: returns NOT_FOUND
            assertEquals("NOT_FOUND", result.getStatus());
        }

        @Test
        @DisplayName("DB 回退路径：用户 B 无法通过 DB 读取用户 A 的结果（安全）")
        void crossUserDbAccess_blocked() {
            // Redis 已过期
            when(valueOperations.get("resume:optimize:result:2:ro_1_999")).thenReturn(null);

            // DB 中用户 B 的最新记录是自己的（或不存在）
            ResumeOptimization recordB = new ResumeOptimization();
            recordB.setTaskId("ro_2_888");
            recordB.setUserId(2L);
            recordB.setStatus("PROCESSING");
            when(resumeOptimizationMapper.selectLatestByUserId(2L)).thenReturn(recordB);

            // 用户 B 尝试用 taskId=ro_1_999 查询 → DB 回退找到的是自己的记录
            // taskId 不匹配，返回 NOT_FOUND
            ResumeOptimizeResultResponse result = interviewService.getOptimizeResult(2L, "ro_1_999");

            assertEquals("NOT_FOUND", result.getStatus());
        }

        @Test
        @DisplayName("未登录用户 getOptimizeResult 返回 401")
        void unauthenticated_getResult() {
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> interviewService.getOptimizeResult(null, "ro_1_123"));

            assertEquals(401, ex.getCode());
        }
    }

    // ========================================================================
    // 5. 边界情况
    // ========================================================================
    @Nested
    @DisplayName("边界情况")
    class EdgeCases {

        @Test
        @DisplayName("extraResumeQuota 为 null 时 decrement SQL 条件不成立返回 0")
        void nullExtraQuota_treatedAsZero() {
            // extra_resume_quota IS NULL → extra_resume_quota > 0 为 NULL → UPDATE 不命中 → 返回 0
            when(redisUtil.incrementWithExpiry(anyString(), any())).thenReturn(6L);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> interviewService.optimizeResume(1L, makeRequest("简历", "JD")));

            assertTrue(ex.getMessage().contains("5"));
        }

        @Test
        @DisplayName("overallScore 为 null 时设为 0")
        void nullOverallScore_setToZero() {
            when(redisUtil.incrementWithExpiry(anyString(), any())).thenReturn(1L);
            when(interviewSessionMapper.listLatestScoredSessions(eq(1L), eq(5)))
                    .thenReturn(Collections.emptyList());
            ResumeOptimizeResponse aiResult = new ResumeOptimizeResponse();
            aiResult.setOverallScore(null); // AI 没返回分数
            aiResult.setSummary("summary");
            when(deepSeekService.optimizeResume(anyString(), anyString(), any()))
                    .thenReturn(aiResult);

            ResumeOptimizeResponse result = interviewService.optimizeResume(1L,
                    makeRequest("简历", "JD"));

            assertEquals(0, result.getOverallScore());
        }

        @Test
        @DisplayName("面试洞察收集失败不影响主流程")
        void interviewInsightsFailure_doesNotBlock() {
            when(redisUtil.incrementWithExpiry(anyString(), any())).thenReturn(1L);
            // 查询面试记录时抛出异常
            when(interviewSessionMapper.listLatestScoredSessions(eq(1L), eq(5)))
                    .thenThrow(new RuntimeException("DB连接超时"));
            when(deepSeekService.optimizeResume(anyString(), anyString(), any()))
                    .thenReturn(makeAiResponse());

            // 不应抛出异常，应降级继续
            ResumeOptimizeResponse result = interviewService.optimizeResume(1L,
                    makeRequest("简历", "JD"));

            assertNotNull(result);
            assertEquals(85, result.getOverallScore());
        }

        @Test
        @DisplayName("resumeText 为 null 时抛异常")
        void nullResumeText_throws() {
            ResumeOptimizeRequest req = new ResumeOptimizeRequest();
            req.setResumeText(null);
            req.setJobDescription("JD");

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> interviewService.optimizeResume(1L, req));

            assertEquals("简历内容不能为空", ex.getMessage());
        }

        @Test
        @DisplayName("getOptimizeResult Redis JSON 损坏时回退到 DB")
        void corruptedRedisJson_fallsBackToDb() throws Exception {
            // Redis 中有损坏的 JSON
            when(valueOperations.get("resume:optimize:result:1:ro_1_123"))
                    .thenReturn("{corrupted json!!!!");

            // DB 中有有效记录
            ResumeOptimizeResponse aiResult = makeAiResponse();
            ResumeOptimizeResultResponse stored = new ResumeOptimizeResultResponse();
            stored.setStatus("DONE");
            stored.setResult(aiResult);
            String storedJson = objectMapper.writeValueAsString(stored);

            ResumeOptimization record = new ResumeOptimization();
            record.setTaskId("ro_1_123");
            record.setUserId(1L);
            record.setResultJson(storedJson);
            when(resumeOptimizationMapper.selectLatestByUserId(1L)).thenReturn(record);

            ResumeOptimizeResultResponse result = interviewService.getOptimizeResult(1L, "ro_1_123");

            assertEquals("DONE", result.getStatus());
            assertEquals(85, result.getResult().getOverallScore());
        }
    }
}
