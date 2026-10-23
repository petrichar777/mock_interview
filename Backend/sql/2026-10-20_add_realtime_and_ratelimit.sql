-- ============================================================
-- 增量脚本：实时视频面试房间 + 结构化面试单 + AI 总结报告 + 接口限流日志
-- 对应改造项：M3 TRTC 双人视频面试、M2 接口限流与防刷
-- 数据库：mock_interview    版本：MySQL 8.0
-- 日期：2026-10-20
--
-- 说明：本脚本只新增表，不修改既有 12 张表；
--       四个表均可重复执行（IF NOT EXISTS）。
--       方案 B 不引入房间消息表，AI 总结的输入为面试单逐题评价与备注。
-- ============================================================

USE `mock_interview`;

-- ------------------------------------------------------------
-- 1. 实时视频面试房间表
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `im_interview_room` (
    `id`             BIGINT       NOT NULL AUTO_INCREMENT COMMENT '房间ID',
    `room_code`      CHAR(6)      NOT NULL                COMMENT '房间码（6位）',
    `trtc_room_id`   BIGINT       NULL                    COMMENT 'TRTC 房间号（创建后回填为房间ID）',
    `interviewer_id` BIGINT       NOT NULL                COMMENT '面试官ID',
    `candidate_id`   BIGINT       NULL                    COMMENT '候选人ID',
    `position`       VARCHAR(100) NULL                    COMMENT '面试岗位',
    `level`          VARCHAR(32)  NULL                    COMMENT '岗位级别（校招/初级/中级/高级）',
    `status`         TINYINT      NOT NULL DEFAULT 0      COMMENT '状态：0待加入/1进行中/2已结束',
    `started_at`     DATETIME     NULL                    COMMENT '开始时间',
    `finished_at`    DATETIME     NULL                    COMMENT '结束时间',
    `created_at`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_room_code` (`room_code`),
    UNIQUE KEY `uk_trtc_room_id` (`trtc_room_id`),
    KEY `idx_interviewer_status` (`interviewer_id`, `status`),
    KEY `idx_candidate_status` (`candidate_id`, `status`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '实时视频面试房间表';

-- ------------------------------------------------------------
-- 2. 结构化面试单表（AI 总结的输入）
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `im_room_question` (
    `id`            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `room_id`       BIGINT       NOT NULL                COMMENT '房间ID',
    `question_id`   BIGINT       NULL                    COMMENT '题库题目ID（自定义题为NULL）',
    `question_stem` TEXT         NOT NULL                COMMENT '题干快照',
    `module_id`     BIGINT       NULL                    COMMENT '题目模块ID快照',
    `module_name`   VARCHAR(100) NULL                    COMMENT '题目模块名称快照',
    `order_no`      INT          NOT NULL                COMMENT '面试顺序（从1开始）',
    `result`        TINYINT      NOT NULL DEFAULT 0      COMMENT '作答评价：0未评/1不会/2一般/3掌握',
    `note`          VARCHAR(500) NULL                    COMMENT '面试官备注',
    `created_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_room_order` (`room_id`, `order_no`),
    KEY `idx_question_id` (`question_id`),
    KEY `idx_room_module` (`room_id`, `module_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '结构化面试单表';

-- ------------------------------------------------------------
-- 3. AI 面试总结报告表
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `im_room_report` (
    `id`            BIGINT      NOT NULL AUTO_INCREMENT COMMENT '报告ID',
    `room_id`       BIGINT      NOT NULL                COMMENT '房间ID',
    `overall_score` TINYINT     NULL                    COMMENT '综合评分（0-100）',
    `summary`       LONGTEXT    NULL                    COMMENT 'AI总结（Markdown）',
    `report_json`   LONGTEXT    NULL                    COMMENT '结构化报告（维度评分/亮点/不足/推荐题目）',
    `model`         VARCHAR(50) NULL                    COMMENT '生成模型',
    `status`        TINYINT     NOT NULL DEFAULT 0      COMMENT '状态：0生成中/1成功/2失败',
    `error_message` VARCHAR(500) NULL                   COMMENT '失败原因',
    `created_at`    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_room_id` (`room_id`),
    KEY `idx_status` (`status`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = 'AI面试总结报告表';

-- ------------------------------------------------------------
-- 4. 接口限流日志表（可选）
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `im_rate_limit_log` (
    `id`             BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `limit_key`      VARCHAR(200) NOT NULL                COMMENT '限流键',
    `dimension`      VARCHAR(16)  NOT NULL                COMMENT '限流维度：USER/IP/ACCOUNT/GLOBAL',
    `user_id`        BIGINT       NULL                    COMMENT '用户ID',
    `client_ip`      VARCHAR(64)  NULL                    COMMENT '客户端IP',
    `request_uri`    VARCHAR(255) NOT NULL                COMMENT '请求路径',
    `request_method` VARCHAR(10)  NOT NULL                COMMENT '请求方法',
    `limit_count`    INT          NOT NULL                COMMENT '阈值次数',
    `window_seconds` INT          NOT NULL                COMMENT '窗口时长（秒）',
    `current_count`  INT          NOT NULL                COMMENT '触发时的请求次数',
    `created_at`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '触发时间',
    PRIMARY KEY (`id`),
    KEY `idx_created_at` (`created_at`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_client_ip` (`client_ip`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '接口限流日志表';
