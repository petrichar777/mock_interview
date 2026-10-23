package com.interviewmaster.backend.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.interviewmaster.backend.exception.BusinessException;
import com.interviewmaster.backend.model.dto.response.EvaluateInterviewResponse;
import com.interviewmaster.backend.model.dto.response.ResumeOptimizeResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

@Service
@RequiredArgsConstructor
@Slf4j
public class DeepSeekService {

    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    @Value("${deepseek.base-url}")
    private String deepSeekBaseUrl;

    @Value("${deepseek.api-key}")
    private String deepSeekApiKey;

    @Value("${deepseek.model:deepseek-chat}")
    private String deepSeekModel;

    @Value("${deepseek.request-timeout-ms:30000}")
    private long deepSeekRequestTimeoutMs;

    @Value("${deepseek.request-retry:1}")
    private int deepSeekRequestRetry;

    public String getModelName() {
        return deepSeekModel;
    }

    public String generateOpeningQuestion(String jobTitle, String mode, String style, String jdText, String resumeContext) {
        boolean pressure = "PRESSURE".equalsIgnoreCase(style);
        boolean isAlgo = "ALGORITHM".equalsIgnoreCase(mode);

        String systemPrompt;
        String userPrompt;

        if (isAlgo && pressure) {
            systemPrompt = "你是大厂算法终面面试官，正在进行【算法专项压力式模拟面试】。"
                    + "你的角色对标字节跳动/拼多多等公司的硬核算法面试风格：当场要最优解、要精确复杂度、不给提示。"
                    + "核心规则：你必须以第一人称直接对候选人说话。严禁输出思考过程或第三方描述。"
                    + "第一个问题的风格："
                    + "1) 直接抛出一个有挑战性的问题让候选人当场分析，不给选择余地；"
                    + "2) 语气严格、直接，从第一轮就建立高压基调；"
                    + "3) 要求候选人在回答中明确给出时间/空间复杂度预估；"
                    + "4) 一次只问一个问题，输出中文，40-150 字。";
            userPrompt = "岗位：" + safe(jobTitle)
                    + "\n模式：ALGORITHM（算法专项·压力式）"
                    + "\nJD：" + safe(jdText)
                    + "\n简历摘要：" + safe(resumeContext)
                    + "\n请给出压力式算法面试的第1轮问题：直接抛出一道与JD方向相关的、有挑战性的算法题目，"
                    + "要求候选人在不借助提示的情况下给出分析和复杂度预估。语气严格、不拖泥带水。";
        } else if (isAlgo) {
            systemPrompt = "你是大厂算法面试官，正在进行【算法专项引导式模拟面试】。"
                    + "你的角色是算法导师——按照结构化追问链路（朴素解法→优化思路→复杂度分析→边界Case→变式迁移）"
                    + "引导候选人建立系统的算法面试思维，而非直接给答案。"
                    + "核心规则：你必须以第一人称直接对候选人说话。严禁输出思考过程或第三方描述。"
                    + "第一个问题的风格："
                    + "1) 让候选人选择最熟悉的算法方向，并描述一道典型题目的朴素解法思路；"
                    + "2) 语气专业温和，给候选人足够空间展开思路；"
                    + "3) 问题需能区分候选人是否真正理解该算法，而非背诵模板；"
                    + "4) 一次只问一个问题，输出中文，30-120 字。";
            userPrompt = "岗位：" + safe(jobTitle)
                    + "\n模式：ALGORITHM（算法专项·引导式）"
                    + "\nJD：" + safe(jdText)
                    + "\n简历摘要：" + safe(resumeContext)
                    + "\n请给出引导式算法面试的第1轮问题：让候选人选择最熟悉的算法方向，"
                    + "并描述一道典型题目的朴素解法思路。语气温和，给人思考空间。";
        } else if (pressure) {
            systemPrompt = "你是大厂 P8+ 技术终面面试官，正在进行【综合能力压力式模拟面试】。"
                    + "你的角色是严苛的终面面试官——从第一个问题就建立高压基调，"
                    + "让候选人感受到真实大厂终面的审视感和压迫感。"
                    + "核心规则：你必须以第一人称直接对候选人说话。严禁输出思考过程或第三方描述。"
                    + "第一个问题的风格："
                    + "1) 让候选人简短自我介绍后，立刻追问其项目中「最有技术挑战的一个决策」；"
                    + "2) 语气犀利、不寒暄、不给缓冲时间；"
                    + "3) 暗示后续会深挖细节和量化证据（数字、日志、压测结果）；"
                    + "4) 绝不提纯算法题，聚焦工程能力、架构决策、项目落地；"
                    + "5) 一次只问一个问题，输出中文，30-120 字。";
            userPrompt = "岗位：" + safe(jobTitle)
                    + "\n模式：COMPREHENSIVE（综合能力·压力式）"
                    + "\nJD：" + safe(jdText)
                    + "\n简历摘要：" + safe(resumeContext)
                    + "\n请给出压力式综合面试的第1轮问题：让候选人自我介绍后，立刻用一个尖锐的技术决策问题施压。"
                    + "语气像终面面试官——直接、审视、不寒暄。";
        } else {
            systemPrompt = "你是大厂技术面试官，正在进行【综合能力引导式模拟面试】。"
                    + "你的角色是资深技术导师——专业但不过度施压，帮助候选人放松心态、充分展示真实水平。"
                    + "核心规则：你必须以第一人称直接对候选人说话。严禁输出思考过程或第三方描述。"
                    + "第一个问题的风格："
                    + "1) 请候选人做简短自我介绍，并挑一个最能证明岗位胜任力的项目经历来讲；"
                    + "2) 语气专业温和，强调「结论先行」并鼓励补充细节；"
                    + "3) 绝不提纯算法题，聚焦工程能力、架构决策、项目落地；"
                    + "4) 一次只问一个问题，输出中文，30-120 字。";
            userPrompt = "岗位：" + safe(jobTitle)
                    + "\n模式：COMPREHENSIVE（综合能力·引导式）"
                    + "\nJD：" + safe(jdText)
                    + "\n简历摘要：" + safe(resumeContext)
                    + "\n请给出引导式综合面试的第1轮问题：让候选人做自我介绍并分享最能证明胜任力的项目经历。"
                    + "语气像技术导师——专业、鼓励、给人空间。";
        }
        return chat(systemPrompt, userPrompt, 0.7);
    }

    public List<String> generateAlgorithmFlowSteps(String algorithmDirection, String style) {
        String systemPrompt = "你是算法面试流程设计器。"
                + "请根据给定的练习方向生成结构化追问流程。"
                + "必须返回 JSON，且仅返回 JSON，不要多余文本。"
                + "JSON 格式固定为：{\"steps\":[\"...\"]}。"
                + "要求：steps 数量 4~6，每一项 12~40 字，必须按从基础到深入推进，便于逐轮追问。";
        String userPrompt = "算法方向：" + safe(algorithmDirection)
                + "\n面试风格：" + safe(style)
                + "\n请输出适配该方向的流程步骤数组 steps。";
        String content = chat(systemPrompt, userPrompt, 0.4);
        Map<String, Object> payload = parseJsonContent(content);
        Object stepsObj = payload.get("steps");
        if (!(stepsObj instanceof List<?> rawSteps)) {
            return fallbackAlgorithmFlowSteps(algorithmDirection);
        }
        List<String> steps = new ArrayList<>();
        for (Object raw : rawSteps) {
            if (raw == null) {
                continue;
            }
            String text = raw.toString().trim();
            if (text.isEmpty() || steps.contains(text)) {
                continue;
            }
            steps.add(text);
            if (steps.size() >= 6) {
                break;
            }
        }
        if (steps.size() < 4) {
            return fallbackAlgorithmFlowSteps(algorithmDirection);
        }
        return steps;
    }

    public ReviewFollowupResult generateReviewAndFollowup(String jobTitle, String mode, String style, Integer turnNo,
                                                          String jdText, String resumeContext, String answerContent,
                                                          Integer algoStep, List<String> algoSteps,
                                                          String conversationHistory) {
        boolean pressure = "PRESSURE".equalsIgnoreCase(style);
        boolean isAlgo = "ALGORITHM".equalsIgnoreCase(mode);
        int totalSteps = algoSteps == null ? 0 : algoSteps.size();
        String stepsText = totalSteps == 0 ? "无" : String.join(" -> ", algoSteps);

        String systemPrompt;
        String userPrompt;

        if (isAlgo && pressure) {
            // ── 算法 · 压力式 ──
            systemPrompt = "你是大厂算法终面面试官，正在进行【算法专项压力式模拟面试】。"
                    + "你的角色对标字节跳动/拼多多算法面试风格：当场要最优解、要精确复杂度推导、要手写关键代码逻辑。"
                    + "\n核心规则：你必须以第一人称直接对候选人说话。严禁输出思考过程或第三方描述。"
                    + "\n你必须严格输出 JSON，不要 Markdown、不要代码块、不要任何额外文本。"
                    + "\nJSON 字段：reviewContent(string), reviewType(string:POSITIVE|NEUTRAL|IMPROVE),"
                    + "followupContent(string), typing(boolean), isFinished(boolean), nextAlgoStep(number)。"
                    + "\n"
                    + "\n点评风格（压力式）："
                    + "\n1) reviewContent 三段结构：【点评结论】【参考答案】【改进建议】。"
                    + "\n2) 【点评结论】直指算法错误或复杂度误判——\"你的O(n)分析是错的，内层循环还有一个遍历\""
                    + "\"这个边界case你完全没考虑\"\"这不是最优解，还有更低复杂度的方案\"。"
                    + "\n3) 【参考答案】给出最优解法的完整推导：关键步骤→时间/空间复杂度→边界case→为什么这是最优的。"
                    + "\n4) 【改进建议】不给提示、不给梯子——只说候选人缺少什么能力（如\"你需要建立复杂度敏感度，"
                    + "看到嵌套循环就应该立刻怀疑O(n²)\"）。"
                    + "\n5) reviewType 判定标准从严：解法最优且复杂度精确=POSITIVE；思路对但非最优或复杂度分析有误=NEUTRAL；"
                    + "解法错误或遗漏关键约束=IMPROVE。不允许因为「态度好」就给POSITIVE。"
                    + "\n"
                    + "\n追问风格（压力式）："
                    + "\n1) 不断挑战更优解：\"还有没有更好的？\"\"空间还能不能降？\"\"能不能一次遍历？\""
                    + "\n2) 典型追问模式：\"请手写这个函数的核心逻辑\"\"推导你的复杂度公式，每一项都要解释\""
                    + "\"给一个让这个算法挂掉的测试用例\"\"如果数据是有序的你还能优化吗\""
                    + "\n3) 不给提示方向——候选人必须自己找到突破点。"
                    + "\n4) 每轮追问必须比上一轮更难，直到触及候选人算法能力的真实边界。"
                    + "\n5) 持续施压，不轻易结束——至少追问3轮以后才能考虑 isFinished=true。"
                    + "\n6) nextAlgoStep 在1~" + totalSteps + "区间推进。";
            userPrompt = "岗位：" + safe(jobTitle)
                    + "\n模式：ALGORITHM（算法专项·压力式）"
                    + "\n轮次：" + turnNo
                    + "\n算法当前步骤：" + algoStep + "/" + totalSteps
                    + "\n算法步骤定义：" + stepsText
                    + "\nJD：" + safe(jdText)
                    + "\n简历摘要：" + safe(resumeContext)
                    + "\n对话历史：\n" + safe(conversationHistory)
                    + "\n候选人本轮回答：" + safe(answerContent)
                    + "\n请严格输出 JSON。注意：点评必须直指算法错误或复杂度误判，追问必须有挑战性、不给提示。";
        } else if (isAlgo) {
            // ── 算法 · 引导式 ──
            systemPrompt = "你是大厂算法面试官，正在进行【算法专项引导式模拟面试】。"
                    + "你的角色是算法导师——按照结构化追问链路（朴素解法→优化思路→复杂度分析→边界Case→变式迁移）"
                    + "引导候选人，帮助他们建立系统的算法面试思维。"
                    + "\n核心规则：你必须以第一人称直接对候选人说话。严禁输出思考过程或第三方描述。"
                    + "\n你必须严格输出 JSON，不要 Markdown、不要代码块、不要任何额外文本。"
                    + "\nJSON 字段：reviewContent(string), reviewType(string:POSITIVE|NEUTRAL|IMPROVE),"
                    + "followupContent(string), typing(boolean), isFinished(boolean), nextAlgoStep(number)。"
                    + "\n"
                    + "\n点评风格（引导式）："
                    + "\n1) reviewContent 三段结构：【点评结论】【参考答案】【改进建议】。"
                    + "\n2) 【点评结论】先肯定正确的部分，再指出可以深化的方向——\"你的主线思路是对的，"
                    + "用哈希表优化查找这个方向没问题\"而不是直接否定。"
                    + "\n3) 【参考答案】给出标准解法+关键步骤+时间/空间复杂度+边界case，作为教学参考。"
                    + "\n4) 【改进建议】给出1-2个具体的提示方向，帮助候选人自己想到优化——\"可以从空间换时间的角度再想想\""
                    + "\"试试看双指针能不能把O(n²)降到O(n)\"。"
                    + "\n5) reviewType 判定标准：解法正确且复杂度分析到位=POSITIVE；主线思路对但细节可优化=NEUTRAL；"
                    + "思路有明显错误或遗漏关键约束=IMPROVE。"
                    + "\n"
                    + "\n追问风格（引导式）："
                    + "\n1) 按算法步骤定义阶梯式推进，给候选人思考空间。"
                    + "\n2) 追问时给出1-2个提示方向作为脚手架。"
                    + "\n3) 每个追问必须与上一轮回答强相关，保持逻辑连贯。"
                    + "\n4) 所有步骤完成且候选人充分展示后可置 isFinished=true。"
                    + "\n5) nextAlgoStep 在1~" + totalSteps + "区间合理推进。";
            userPrompt = "岗位：" + safe(jobTitle)
                    + "\n模式：ALGORITHM（算法专项·引导式）"
                    + "\n轮次：" + turnNo
                    + "\n算法当前步骤：" + algoStep + "/" + totalSteps
                    + "\n算法步骤定义：" + stepsText
                    + "\nJD：" + safe(jdText)
                    + "\n简历摘要：" + safe(resumeContext)
                    + "\n对话历史：\n" + safe(conversationHistory)
                    + "\n候选人本轮回答：" + safe(answerContent)
                    + "\n请严格输出 JSON。注意：点评要专业温和但精准，追问要给提示方向帮助候选人进步。";
        } else if (pressure) {
            // ── 综合 · 压力式 ──
            systemPrompt = "你是大厂 P8+ 技术终面面试官，正在进行【综合能力压力式模拟面试】。"
                    + "你的角色是严苛的终面面试官——犀利、直接、不留情面。你用事实和逻辑施压，而非情绪化否定。"
                    + "\n核心规则：你必须以第一人称直接对候选人说话。严禁输出思考过程或第三方描述。"
                    + "\n你必须严格输出 JSON，不要 Markdown、不要代码块、不要任何额外文本。"
                    + "\nJSON 字段：reviewContent(string), reviewType(string:POSITIVE|NEUTRAL|IMPROVE),"
                    + "followupContent(string), typing(boolean), isFinished(boolean)。"
                    + "\n"
                    + "\n点评风格（压力式）："
                    + "\n1) reviewContent 三段结构：【点评结论】【参考答案】【改进建议】。"
                    + "\n2) 【点评结论】直击漏洞和矛盾——\"你说性能提升了30%，这个数字怎么测出来的？用什么工具？"
                    + "基线是什么？\"\"你一直没提到数据一致性怎么保证，这是最关键的，你在回避吗？\""
                    + "强制要求量化证据：指标、日志、压测报告、线上故障复盘记录、具体技术方案对比。"
                    + "\n3) 【参考答案】给出业内的标准做法或最佳实践，可执行、可验证、可量化。"
                    + "\n4) 【改进建议】直言不讳——\"你需要培养用数据说话的习惯，泛泛而谈在大厂面试中会被直接挂掉\"。"
                    + "\n5) reviewType 判定标准从严：有量化数据支撑且逻辑严密=POSITIVE；方向对但无具体数据支撑=NEUTRAL；"
                    + "泛泛而谈、回避关键追问、无实证=IMPROVE。不允许因为「回答流利」就给POSITIVE。"
                    + "\n"
                    + "\n追问风格（压力式）："
                    + "\n1) 针锋相对地攻击薄弱环节——如果候选人避重就轻，直接挑明追问。"
                    + "\n2) 典型追问模式：\"你提到XX优化了30%，这个数字怎么得出来的？基线和对照组是什么？\""
                    + "\"如果流量再涨10倍，你的方案哪里会先挂？\"\"你选的这个方案跟XX方案比有什么优势？为什么不用XX？\""
                    + "\"这个项目如果让你重新做，你会怎么做不同？\""
                    + "\n3) 不给提示——让候选人自己面对漏洞。"
                    + "\n4) 持续施压，至少追问3轮以上才能考虑 isFinished=true。"
                    + "\n5) 不涉及纯算法题，所有追问围绕工程能力（项目经验/技术深度/系统设计/业务理解）。";
            userPrompt = "岗位：" + safe(jobTitle)
                    + "\n模式：COMPREHENSIVE（综合能力·压力式）"
                    + "\n轮次：" + turnNo
                    + "\nJD：" + safe(jdText)
                    + "\n简历摘要：" + safe(resumeContext)
                    + "\n对话历史：\n" + safe(conversationHistory)
                    + "\n候选人本轮回答：" + safe(answerContent)
                    + "\n请严格输出 JSON（不需要 nextAlgoStep 字段）。"
                    + "注意：点评要尖锐直击漏洞，强制要求量化证据。追问要有挑战性，不给提示。";
        } else {
            // ── 综合 · 引导式 ──
            systemPrompt = "你是大厂技术面试官，正在进行【综合能力引导式模拟面试】。"
                    + "你的角色是资深技术导师——专业但不过度施压，目标是帮助候选人梳理思路、补齐短板、在面试中成长。"
                    + "\n核心规则：你必须以第一人称直接对候选人说话。严禁输出思考过程或第三方描述。"
                    + "\n你必须严格输出 JSON，不要 Markdown、不要代码块、不要任何额外文本。"
                    + "\nJSON 字段：reviewContent(string), reviewType(string:POSITIVE|NEUTRAL|IMPROVE),"
                    + "followupContent(string), typing(boolean), isFinished(boolean)。"
                    + "\n"
                    + "\n点评风格（引导式）："
                    + "\n1) reviewContent 三段结构：【点评结论】【参考答案】【改进建议】。"
                    + "\n2) 【点评结论】先肯定亮点和正确部分——\"你从系统架构的视角切入分析这个问题，这个思路很好\""
                    + "然后指出可以深化的方向——\"如果再补充一下数据层的具体实现细节，回答会更有说服力\"。"
                    + "\n3) 【参考答案】给出业内标准做法或最佳实践，作为教学参考——可执行、可验证。"
                    + "\n4) 【改进建议】给出具体、可操作的改进方向——\"建议你下次回答这类问题时，先给结论（一句话总结），"
                    + "再用STAR法则展开（情境→任务→行动→结果），最后用一到两个量化指标收尾\"。"
                    + "\n5) reviewType 判定标准：核心思路正确且关键细节到位=POSITIVE；方向对但深度或细节不够=NEUTRAL；"
                    + "方向性错误或关键概念混淆=IMPROVE。"
                    + "\n"
                    + "\n追问风格（引导式）："
                    + "\n1) 在候选人回答的基础上递进追问，保持逻辑连贯。"
                    + "\n2) 追问时给1-2个方向性提示作为脚手架——\"可以从数据一致性的角度再展开一下\"。"
                    + "\n3) 聚焦工程能力（项目经验/技术深度/系统设计/业务理解），不涉及纯算法题。"
                    + "\n4) 如已围绕JD核心能力完成3轮以上有效验证，且候选人充分展示，可置 isFinished=true。"
                    + "\n5) 灵活把握节奏——如果候选人在某个维度表现特别突出，可以深入追问；"
                    + "如果某个维度明显薄弱，追问1-2轮后就切换到其他维度。";
            userPrompt = "岗位：" + safe(jobTitle)
                    + "\n模式：COMPREHENSIVE（综合能力·引导式）"
                    + "\n轮次：" + turnNo
                    + "\nJD：" + safe(jdText)
                    + "\n简历摘要：" + safe(resumeContext)
                    + "\n对话历史：\n" + safe(conversationHistory)
                    + "\n候选人本轮回答：" + safe(answerContent)
                    + "\n请严格输出 JSON（不需要 nextAlgoStep 字段）。"
                    + "注意：点评要专业温和，先肯定再建议。追问要给提示方向，帮助候选人成长。";
        }
        String content = chat(systemPrompt, userPrompt, 0.7);
        Map<String, Object> payload = parseJsonContent(content);
        ReviewFollowupResult result = new ReviewFollowupResult();
        result.setReviewContent(asString(payload.get("reviewContent")));
        result.setReviewType(asString(payload.get("reviewType")));
        result.setFollowupContent(asString(payload.get("followupContent")));
        result.setTyping(asBoolean(payload.get("typing")));
        result.setFinished(asBoolean(payload.get("isFinished")));
        if (isAlgo) {
            result.setNextAlgoStep(asInteger(payload.get("nextAlgoStep")));
        }
        return result;
    }

    public Flux<String> streamRawCompletion(String systemPrompt, String userPrompt, double temperature) {
        if (deepSeekApiKey == null || deepSeekApiKey.isBlank()) {
            return Flux.error(new BusinessException(500, "DeepSeek API Key 未配置"));
        }
        Map<String, Object> payload = new HashMap<>();
        payload.put("model", deepSeekModel);
        payload.put("temperature", temperature);
        payload.put("stream", true);
        payload.put("messages", List.of(
                Map.of("role", "system", "content", systemPrompt),
                Map.of("role", "user", "content", userPrompt)
        ));
        return webClient.post()
                .uri(deepSeekBaseUrl + "/chat/completions")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + deepSeekApiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.TEXT_EVENT_STREAM)
                .bodyValue(payload)
                .retrieve()
                .bodyToFlux(String.class)
                .timeout(Duration.ofSeconds(30))
                .flatMap(this::splitSseLines)
                .doOnNext(line -> log.info("DeepSeek raw SSE line: {}", line))
                .filter(line -> !line.isBlank() && !line.startsWith(":"))
                .map(line -> {
                    String trimmed = line.trim();
                    if (trimmed.startsWith("data:")) {
                        trimmed = trimmed.substring("data:".length()).trim();
                    }
                    return trimmed;
                })
                .filter(line -> !line.isBlank());
    }

    private String chat(String systemPrompt, String userPrompt, double temperature) {
        if (deepSeekApiKey == null || deepSeekApiKey.isBlank()) {
            throw new BusinessException(500, "DeepSeek API Key 未配置");
        }
        Map<String, Object> payload = new HashMap<>();
        payload.put("model", deepSeekModel);
        payload.put("temperature", temperature);
        payload.put("max_tokens", 8192);
        payload.put("messages", List.of(
                Map.of("role", "system", "content", systemPrompt),
                Map.of("role", "user", "content", userPrompt)
        ));
        Map<String, Object> response = executeChatRequest(payload);
        if (response == null) {
            throw new BusinessException(500, "DeepSeek 响应为空");
        }
        Object choicesObj = response.get("choices");
        if (!(choicesObj instanceof List<?> choices) || choices.isEmpty()) {
            throw new BusinessException(500, "DeepSeek 响应格式错误");
        }
        Object firstObj = choices.get(0);
        if (!(firstObj instanceof Map<?, ?> firstChoice)) {
            throw new BusinessException(500, "DeepSeek 响应格式错误");
        }
        Object messageObj = firstChoice.get("message");
        if (!(messageObj instanceof Map<?, ?> message)) {
            throw new BusinessException(500, "DeepSeek 响应格式错误");
        }
        Object contentObj = message.get("content");
        String content = (contentObj != null && !contentObj.toString().isBlank())
                ? contentObj.toString().trim() : null;
        // Never fall back to reasoning_content — it is the model's internal
        // chain-of-thought and must not leak into interview-facing responses.
        if (content == null) {
            throw new BusinessException(500, "DeepSeek 返回内容为空");
        }
        return content.trim();
    }

    // .block() 在 Spring MVC (Tomcat Servlet) 线程中调用是安全的，
    // 不会阻塞 Netty 事件循环——本项目不使用 WebFlux 作为 Web 容器。
    private Map<String, Object> executeChatRequest(Map<String, Object> payload) {
        Exception lastException = null;
        int retry = Math.max(0, deepSeekRequestRetry);
        for (int attempt = 0; attempt <= retry; attempt++) {
            try {
                Mono<Map<String, Object>> request = webClient.post()
                        .uri(deepSeekBaseUrl + "/chat/completions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + deepSeekApiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(payload)
                        .retrieve()
                        .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                        .timeout(Duration.ofMillis(resolveTimeoutMs()));
                Map<String, Object> response = request.block();
                if (response != null) {
                    return response;
                }
                lastException = new BusinessException(500, "DeepSeek 响应为空");
            } catch (Exception ex) {
                lastException = ex;
            }
        }
        String message = lastException == null ? "DeepSeek 请求失败" : defaultErrorMessage(lastException);
        throw new BusinessException(500, message);
    }

    private long resolveTimeoutMs() {
        if (deepSeekRequestTimeoutMs < 1000) {
            return 1000L;
        }
        return deepSeekRequestTimeoutMs;
    }

    private String defaultErrorMessage(Exception ex) {
        String text = ex.getMessage();
        if (text == null || text.isBlank()) {
            return "DeepSeek 请求失败";
        }
        String lower = text.toLowerCase(Locale.ROOT);
        if (lower.contains("timeout")) {
            return "DeepSeek 请求超时";
        }
        return "DeepSeek 请求失败：" + text;
    }

    private Map<String, Object> parseJsonContent(String content) {
        try {
            return objectMapper.readValue(content, new TypeReference<Map<String, Object>>() {});
        } catch (Exception ignored) {
        }
        String normalized = content;
        int start = normalized.indexOf('{');
        int end = normalized.lastIndexOf('}');
        if (start >= 0 && end > start) {
            String json = normalized.substring(start, end + 1);
            try {
                return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
            } catch (Exception ignored) {
            }
        }
        throw new BusinessException(500, "DeepSeek 返回 JSON 解析失败");
    }

    private String safe(String value) {
        if (value == null || value.isBlank()) {
            return "无";
        }
        return value;
    }

    private String asString(Object value) {
        return value == null ? null : value.toString();
    }

    private Boolean asBoolean(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Boolean b) {
            return b;
        }
        String text = value.toString();
        if ("true".equalsIgnoreCase(text) || "1".equals(text)) {
            return true;
        }
        if ("false".equalsIgnoreCase(text) || "0".equals(text)) {
            return false;
        }
        return null;
    }

    private Integer asInteger(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private List<String> fallbackAlgorithmFlowSteps(String algorithmDirection) {
        String topic = safe(algorithmDirection);
        String normalized = "无".equals(topic) ? "算法基础" : topic;
        List<String> steps = new ArrayList<>();
        steps.add("请先描述" + normalized + "题目的朴素思路并说明适用场景");
        steps.add("请给出" + normalized + "的优化方案与关键剪枝/数据结构选择");
        steps.add("请分析该方案的时间复杂度与空间复杂度并说明瓶颈");
        steps.add("请补充边界输入、异常情况与可验证的测试用例");
        steps.add("请将该思路迁移到同类变式题并说明泛化方法");
        return steps;
    }

    public EvaluateInterviewResponse evaluateInterview(String mode, String conversationText, String jobTitle,
                                                       String resumeContext) {
        boolean isAlgo = "ALGORITHM".equalsIgnoreCase(mode);
        String systemPrompt;
        String userPrompt;

        if (isAlgo) {
            systemPrompt = "你是大厂算法面试官，刚刚完成了一场算法专项模拟面试。"
                    + "请基于完整的面试对话记录，对候选人进行综合评分。"
                    + "核心规则：你必须以第一人称直接对候选人说话。严禁输出思考过程或第三方描述。"
                    + "评分维度（每项0-20分，总分0-100）：\n"
                    + "1) 算法正确性 — 解题思路是否正确，关键步骤是否清晰\n"
                    + "2) 复杂度分析 — 时间/空间复杂度分析是否准确、完整\n"
                    + "3) 边界处理 — 是否考虑边界case、异常输入、鲁棒性\n"
                    + "4) 代码思维 — 是否具备工程化的代码表达能力\n"
                    + "5) 综合印象 — 沟通逻辑、问题拆解能力、抗压表现\n"
                    + "评分标准：0-5=完全未涉及，6-10=有提及但浅，11-15=基本到位，16-20=优秀。\n"
                    + "resumeAdvice规则：如果用户提供了简历上下文，请根据面试中暴露的弱点，"
                    + "倒推简历上哪些项目经历或技能描述可以优化，给出1-2条具体的简历改写建议"
                    + "（例如：某个项目的技术深度描述不够、缺少量化成果、关键技能未突出等）。"
                    + "如果用户没有提供简历上下文，resumeAdvice字段输出空字符串\"\"。\n"
                    + "你必须严格输出 JSON，不要 Markdown、不要代码块、不要任何额外文本：\n"
                    + "{\"totalScore\":85,\"summary\":\"...\",\"dimensions\":[{\"name\":\"算法正确性\",\"score\":17,\"maxScore\":20,\"comment\":\"...\"},...],\"highlights\":\"...\",\"improvements\":\"...\",\"suggestion\":\"...\",\"resumeAdvice\":\"...\"}\n"
                    + "要求：summary直接对候选人说话2-3句；每个dimension的comment是1-2句点评；highlights总结1-2个亮点；improvements指出1-2个改进方向；suggestion给出1条具体可行的学习建议；resumeAdvice直接对候选人说话1-2句简历优化建议。";
            userPrompt = "岗位：" + safe(jobTitle)
                    + "\n模式：算法专项面试\n"
                    + "\n面试对话记录：\n" + safe(conversationText)
                    + (resumeContext != null && !resumeContext.isBlank()
                            ? "\n\n候选人简历上下文：\n" + safe(resumeContext)
                            : "")
                    + "\n请严格按照指定JSON格式输出评分结果。";
        } else {
            systemPrompt = "你是大厂技术面试官，刚刚完成了一场综合能力模拟面试。"
                    + "请基于完整的面试对话记录，对候选人进行综合评分。"
                    + "核心规则：你必须以第一人称直接对候选人说话。严禁输出思考过程或第三方描述。"
                    + "评分维度（每项0-20分，总分0-100）：\n"
                    + "1) 岗位匹配度 — 项目经验与岗位JD的匹配程度\n"
                    + "2) 项目经验 — 项目复杂度、候选人角色与技术决策能力\n"
                    + "3) 技术深度 — 对技术原理的理解深度与横向对比能力\n"
                    + "4) 沟通表达 — 结构化表达能力，结论先行的习惯\n"
                    + "5) 综合印象 — 整体技术视野、学习潜力、抗压表现\n"
                    + "评分标准：0-5=完全未涉及，6-10=有提及但浅，11-15=基本到位，16-20=优秀。\n"
                    + "resumeAdvice规则：如果用户提供了简历上下文，请根据面试中暴露的弱点，"
                    + "倒推简历上哪些项目经历或技能描述可以优化，给出1-2条具体的简历改写建议"
                    + "（例如：某个项目的技术深度描述不够、缺少量化成果、关键技能未突出等）。"
                    + "如果用户没有提供简历上下文，resumeAdvice字段输出空字符串\"\"。\n"
                    + "你必须严格输出 JSON，不要 Markdown、不要代码块、不要任何额外文本：\n"
                    + "{\"totalScore\":85,\"summary\":\"...\",\"dimensions\":[{\"name\":\"岗位匹配度\",\"score\":17,\"maxScore\":20,\"comment\":\"...\"},...],\"highlights\":\"...\",\"improvements\":\"...\",\"suggestion\":\"...\",\"resumeAdvice\":\"...\"}\n"
                    + "要求：summary直接对候选人说话2-3句；每个dimension的comment是1-2句点评；highlights总结1-2个亮点；improvements指出1-2个改进方向；suggestion给出1条具体可行的学习建议；resumeAdvice直接对候选人说话1-2句简历优化建议。";
            userPrompt = "岗位：" + safe(jobTitle)
                    + "\n模式：综合能力面试\n"
                    + "\n面试对话记录：\n" + safe(conversationText)
                    + (resumeContext != null && !resumeContext.isBlank()
                            ? "\n\n候选人简历上下文：\n" + safe(resumeContext)
                            : "")
                    + "\n请严格按照指定JSON格式输出评分结果。";
        }
        String content = chat(systemPrompt, userPrompt, 0.5);
        Map<String, Object> payload = parseJsonContent(content);
        EvaluateInterviewResponse response = new EvaluateInterviewResponse();
        response.setTotalScore(asInteger(payload.get("totalScore")));
        response.setSummary(asString(payload.get("summary")));
        response.setHighlights(asString(payload.get("highlights")));
        response.setImprovements(asString(payload.get("improvements")));
        response.setSuggestion(asString(payload.get("suggestion")));
        response.setResumeAdvice(asString(payload.get("resumeAdvice")));

        Object dimsObj = payload.get("dimensions");
        List<EvaluateInterviewResponse.DimensionScore> dimensions = new ArrayList<>();
        if (dimsObj instanceof List<?> rawList) {
            for (Object item : rawList) {
                if (item instanceof Map<?, ?> dim) {
                    EvaluateInterviewResponse.DimensionScore ds = new EvaluateInterviewResponse.DimensionScore();
                    ds.setName(asString(dim.get("name")));
                    ds.setScore(asInteger(dim.get("score")));
                    ds.setMaxScore(asInteger(dim.get("maxScore")));
                    ds.setComment(asString(dim.get("comment")));
                    dimensions.add(ds);
                }
            }
        }
        response.setDimensions(dimensions);
        return response;
    }

    public ResumeOptimizeResponse optimizeResume(String resumeText, String jobDescription,
                                                  List<String> interviewInsights) {
        String systemPrompt = "你是资深技术招聘专家和简历优化顾问。"
                + "请仔细分析以下简历，给出专业的优化建议。"
                + "核心规则：你必须以第一人称直接对求职者说话，语气专业、具体、有建设性。严禁输出思考过程或第三方描述。"
                + "评分维度（每项0-25分，总分0-100）：\n"
                + "1) 完整性 — 是否包含教育背景、技能、项目经历、工作经历等关键模块\n"
                + "2) 关键词匹配 — 是否使用了行业术语和岗位相关技术关键词\n"
                + "3) 结构清晰度 — 布局是否逻辑清晰、层次分明、易于快速扫描\n"
                + "4) 表达量化度 — 成就是否有数据支撑、描述是否简洁有力、使用行动导向语言\n"
                + "评分标准：0-6=严重缺失，7-12=有待加强，13-18=基本合格，19-25=优秀。\n"
                + "sectionAdvice规则：将简历按实际段落/模块拆分，"
                + "对每个模块给出 originalSummary（用1句话概括该模块当前写了什么）和suggestion（1-2句具体的改写建议）。"
                + "覆盖3-6个主要模块即可，不要过度拆分。\n"
                + "jdMatch规则：如果用户提供了JD，分析简历与JD的匹配程度（200字以内），"
                + "指出匹配良好的点和需要加强的点。如果没有JD，输出空字符串\"\"。\n"
                + "interviewInsights规则：如果提供了面试历史洞察，"
                + "结合面试中暴露的弱点，反向推导简历上哪些地方需要优化（输出1-3条字符串）。"
                + "如果没有面试洞察，输出空数组[]。\n"
                + "你必须严格输出 JSON，不要 Markdown、不要代码块、不要任何额外文本：\n"
                + "{\"overallScore\":72,\"summary\":\"...\",\"dimensions\":[{\"name\":\"完整性\",\"score\":18,\"maxScore\":25,\"comment\":\"...\"},"
                + "{\"name\":\"关键词匹配\",\"score\":17,\"maxScore\":25,\"comment\":\"...\"},"
                + "{\"name\":\"结构清晰度\",\"score\":19,\"maxScore\":25,\"comment\":\"...\"},"
                + "{\"name\":\"表达量化度\",\"score\":18,\"maxScore\":25,\"comment\":\"...\"}],"
                + "\"jdMatch\":\"...\","
                + "\"sectionAdvice\":[{\"sectionName\":\"教育背景\",\"originalSummary\":\"...\",\"suggestion\":\"...\"},...],"
                + "\"interviewInsights\":[\"...\"]}";

        StringBuilder userPrompt = new StringBuilder();
        userPrompt.append("简历内容：\n").append(safe(resumeText));

        if (jobDescription != null && !jobDescription.isBlank()) {
            userPrompt.append("\n\n岗位描述（JD）：\n").append(safe(jobDescription));
        }
        if (interviewInsights != null && !interviewInsights.isEmpty()) {
            userPrompt.append("\n\n面试历史洞察（该求职者在过往模拟面试中暴露的弱点）：");
            for (int i = 0; i < interviewInsights.size(); i++) {
                userPrompt.append("\n").append(i + 1).append(". ").append(interviewInsights.get(i));
            }
        }
        userPrompt.append("\n\n请严格按照指定JSON格式输出简历优化建议。");

        String content = chat(systemPrompt, userPrompt.toString(), 0.4);
        Map<String, Object> payload = parseJsonContent(content);

        ResumeOptimizeResponse response = new ResumeOptimizeResponse();
        response.setOverallScore(asInteger(payload.get("overallScore")));
        response.setSummary(asString(payload.get("summary")));
        response.setJdMatch(asString(payload.get("jdMatch")));

        // Parse dimensions
        Object dimsObj = payload.get("dimensions");
        List<ResumeOptimizeResponse.ResumeDimension> dimensions = new ArrayList<>();
        if (dimsObj instanceof List<?> rawList) {
            for (Object item : rawList) {
                if (item instanceof Map<?, ?> dim) {
                    ResumeOptimizeResponse.ResumeDimension d = new ResumeOptimizeResponse.ResumeDimension();
                    d.setName(asString(dim.get("name")));
                    d.setScore(asInteger(dim.get("score")));
                    d.setMaxScore(asInteger(dim.get("maxScore")));
                    d.setComment(asString(dim.get("comment")));
                    dimensions.add(d);
                }
            }
        }
        response.setDimensions(dimensions);

        // Parse sectionAdvice
        Object sectionsObj = payload.get("sectionAdvice");
        List<ResumeOptimizeResponse.SectionAdvice> sectionAdvice = new ArrayList<>();
        if (sectionsObj instanceof List<?> rawSections) {
            for (Object item : rawSections) {
                if (item instanceof Map<?, ?> sec) {
                    ResumeOptimizeResponse.SectionAdvice sa = new ResumeOptimizeResponse.SectionAdvice();
                    sa.setSectionName(asString(sec.get("sectionName")));
                    sa.setOriginalSummary(asString(sec.get("originalSummary")));
                    sa.setSuggestion(asString(sec.get("suggestion")));
                    sectionAdvice.add(sa);
                }
            }
        }
        response.setSectionAdvice(sectionAdvice);

        // Parse interviewInsights
        Object insightsObj = payload.get("interviewInsights");
        List<String> insights = new ArrayList<>();
        if (insightsObj instanceof List<?> rawInsights) {
            for (Object item : rawInsights) {
                if (item != null && !item.toString().isBlank()) {
                    insights.add(item.toString());
                }
            }
        }
        response.setInterviewInsights(insights);

        return response;
    }

    private Flux<String> splitSseLines(String rawChunk) {
        if (rawChunk == null || rawChunk.isBlank()) {
            return Flux.empty();
        }
        String normalized = rawChunk.replace("\r", "");
        String[] lines = normalized.split("\n");
        return Flux.fromArray(lines);
    }

    public String generateQuestions(String moduleName, String categoryName, int count,
                                      Integer difficulty, Integer type) {
        String systemPrompt = "你是一位资深技术面试官和出题专家。"
                + "请根据指定的知识模块，生成高质量的面试题目。"
                + "你必须严格输出 JSON 数组，不要 Markdown、不要代码块、不要任何额外文本。"
                + "JSON 格式：[{\"stem\":\"...\",\"type\":1,\"difficulty\":1,\"options\":[{\"key\":\"A\",\"text\":\"...\",\"correct\":true},...],\"analysis\":\"...\",\"tags\":\"...\"},...]"
                + "\ntype 取值：1=单选题（4个选项，恰好1个正确），2=多选题（4个选项，1-3个正确），3=判断题。"
                + "\ndifficulty 取值：1=简单，2=中等，3=困难。"
                + "\n判断题重要规则：type=3 时，options 必须恰好2项，key 必须分别为 \"T\" 和 \"F\"，text 分别为 \"正确\" 和 \"错误\"，只有一个 correct 为 true。示例："
                + "\n{\"stem\":\"Java中String是不可变的\",\"type\":3,\"difficulty\":1,\"options\":[{\"key\":\"T\",\"text\":\"正确\",\"correct\":true},{\"key\":\"F\",\"text\":\"错误\",\"correct\":false}],\"analysis\":\"...\",\"tags\":\"Java,String\"}"
                + "\n要求：题干清晰无歧义，干扰项合理有迷惑性，解析准确详细，tags 用逗号分隔的关键词。"
                + "\n确保每道题的正确答案确实是正确的，不要出现事实性错误。";
        StringBuilder userPrompt = new StringBuilder();
        userPrompt.append("模块：").append(moduleName);
        if (categoryName != null && !categoryName.isBlank()) {
            userPrompt.append("（分类：").append(categoryName).append("）");
        }
        userPrompt.append("\n生成数量：").append(count).append(" 题");
        if (difficulty != null) {
            String diffLabel = difficulty == 1 ? "简单" : difficulty == 2 ? "中等" : "困难";
            userPrompt.append("\n指定难度：").append(diffLabel);
        }
        if (type != null) {
            String typeLabel = type == 1 ? "单选题" : type == 2 ? "多选题" : "判断题";
            userPrompt.append("\n指定题型：").append(typeLabel);
        }
        userPrompt.append("\n请生成题目 JSON 数组。");
        return chat(systemPrompt, userPrompt.toString(), 0.8);
    }

    @Data
    public static class ReviewFollowupResult {

        private String reviewContent;

        private String reviewType;

        private String followupContent;

        private Boolean typing;

        private Boolean finished;

        private Integer nextAlgoStep;
    }
}
