-- ============================================================
-- 基于 Vue+SpringBoot 的实时模拟面试系统 —— 全量建表脚本
-- 数据库：mock_interview    版本：MySQL 8.0
-- 日期：2026-10-21
--
-- 说明：
--   1. 本脚本为全量建表脚本：既有 12 张业务表 + 新增 4 张表，共 16 张；
--   2. 新增 4 张表分别对应 M2 接口限流日志、M3 视频面试房间、结构化面试单与 AI 总结报告；
--   3. 可在空库直接执行；脚本使用 CREATE TABLE IF NOT EXISTS，
--      若库中已存在同名表会跳过，不会修改旧表结构；
--   4. 线上已有库请先执行 mysqldump 备份并与本脚本比对，避免结构差异被 IF NOT EXISTS 掩盖；
--   5. 所有表均为 InnoDB + utf8mb4，字段与索引均带注释。
-- ============================================================

SET NAMES utf8mb4;

CREATE DATABASE IF NOT EXISTS `mock_interview`
    DEFAULT CHARACTER SET utf8mb4
    COLLATE utf8mb4_general_ci;

USE `mock_interview`;

-- ------------------------------------------------------------
-- 1. 用户表
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `im_user` (
    `id`                    BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `username`              VARCHAR(50)  NOT NULL                COMMENT '用户名',
    `email`                 VARCHAR(100) NOT NULL                COMMENT '邮箱',
    `password_hash`         VARCHAR(100) NOT NULL                COMMENT '密码哈希（BCrypt）',
    `role`                  VARCHAR(20)  NOT NULL DEFAULT 'USER' COMMENT '角色：USER/ADMIN',
    `status`                TINYINT      NOT NULL DEFAULT 0      COMMENT '状态：0正常/1禁用',
    `last_login_at`         DATETIME     NULL                    COMMENT '最后登录时间',
    `extra_interview_quota` INT          NOT NULL DEFAULT 0      COMMENT '额外AI面试次数',
    `extra_resume_quota`    INT          NOT NULL DEFAULT 0      COMMENT '额外简历优化次数',
    `created_at`            DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`            DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_username` (`username`),
    UNIQUE KEY `uk_email` (`email`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '用户表';

-- ------------------------------------------------------------
-- 2. 职业方向表
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `im_direction` (
    `id`         BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `code`       VARCHAR(32) NOT NULL                COMMENT '方向代码',
    `name`       VARCHAR(50) NOT NULL                COMMENT '方向名称',
    `sort`       INT         NOT NULL DEFAULT 0      COMMENT '排序',
    `status`     TINYINT     NOT NULL DEFAULT 0      COMMENT '状态：0启用/1停用',
    `created_at` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_code` (`code`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '职业方向表';

-- ------------------------------------------------------------
-- 3. 题目分类表
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `im_category` (
    `id`           BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `direction_id` BIGINT      NOT NULL                COMMENT '所属方向ID',
    `code`         VARCHAR(32) NOT NULL                COMMENT '分类代码',
    `name`         VARCHAR(50) NOT NULL                COMMENT '分类名称',
    `sort`         INT         NOT NULL DEFAULT 0      COMMENT '排序',
    `status`       TINYINT     NOT NULL DEFAULT 0      COMMENT '状态：0启用/1停用',
    `created_at`   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_code` (`code`),
    KEY `idx_direction_id` (`direction_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '题目分类表';

-- ------------------------------------------------------------
-- 4. 题目模块表
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `im_question_module` (
    `id`            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `category_name` VARCHAR(50)  NULL                    COMMENT '分类名称（冗余字段）',
    `category_id`   BIGINT       NULL                    COMMENT '分类ID',
    `name`          VARCHAR(100) NOT NULL                COMMENT '模块名称',
    `description`   VARCHAR(500) NULL                    COMMENT '模块描述',
    `sort`          INT          NOT NULL DEFAULT 0      COMMENT '排序',
    `status`        TINYINT      NOT NULL DEFAULT 0      COMMENT '状态：0启用/1停用',
    `created_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_category_id` (`category_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '题目模块表';

-- ------------------------------------------------------------
-- 5. 题目表
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `im_question` (
    `id`         BIGINT   NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `module_id`  BIGINT   NOT NULL                COMMENT '所属模块ID',
    `type`       TINYINT  NOT NULL                COMMENT '题型：1单选/2多选/3判断',
    `difficulty` TINYINT  NOT NULL DEFAULT 1      COMMENT '难度：1简单/2中等/3困难',
    `stem`       TEXT     NOT NULL                COMMENT '题干',
    `analysis`   TEXT     NULL                    COMMENT '答案解析',
    `tags`       VARCHAR(255) NULL                COMMENT '标签（逗号分隔）',
    `status`     TINYINT  NOT NULL DEFAULT 0      COMMENT '状态：0上架/1下架/2草稿',
    `created_by` BIGINT   NULL                    COMMENT '创建人ID',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_module_status` (`module_id`, `status`),
    KEY `idx_status_type` (`status`, `type`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '题目表';

-- ------------------------------------------------------------
-- 6. 题目选项表
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `im_question_option` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `question_id` BIGINT       NOT NULL                COMMENT '题目ID',
    `option_key`  VARCHAR(8)   NOT NULL                COMMENT '选项标识（A/B/C/D）',
    `option_text` VARCHAR(500) NOT NULL                COMMENT '选项内容',
    `is_correct`  TINYINT      NOT NULL DEFAULT 0      COMMENT '是否正确：0否/1是',
    `sort`        INT          NOT NULL DEFAULT 0      COMMENT '排序',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_question_option` (`question_id`, `option_key`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '题目选项表';

-- ------------------------------------------------------------
-- 7. 错题本表
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `im_user_mistake` (
    `id`          BIGINT   NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `user_id`     BIGINT   NOT NULL                COMMENT '用户ID',
    `question_id` BIGINT   NOT NULL                COMMENT '题目ID',
    `created_at`  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '加入时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_question` (`user_id`, `question_id`),
    KEY `idx_question_id` (`question_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '错题本表';

-- ------------------------------------------------------------
-- 8. 练习记录表
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `im_practice_record` (
    `id`           BIGINT   NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `user_id`      BIGINT   NOT NULL                COMMENT '用户ID',
    `module_id`    BIGINT   NOT NULL                COMMENT '模块ID',
    `question_id`  BIGINT   NOT NULL                COMMENT '题目ID',
    `answers`      VARCHAR(64) NOT NULL             COMMENT '作答选项（逗号分隔）',
    `is_correct`   TINYINT  NOT NULL DEFAULT 0      COMMENT '是否正确：0否/1是',
    `time_cost_ms` BIGINT   NOT NULL DEFAULT 0      COMMENT '答题耗时（毫秒）',
    `answered_at`  DATETIME NOT NULL                COMMENT '答题时间',
    PRIMARY KEY (`id`),
    KEY `idx_user_correct_question_time` (`user_id`, `is_correct`, `question_id`, `answered_at` DESC),
    KEY `idx_user_module_question` (`user_id`, `module_id`, `question_id`),
    KEY `idx_question_id` (`question_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '练习记录表';

-- ------------------------------------------------------------
-- 9. 签到记录表
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `im_checkin_day` (
    `id`           BIGINT   NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `user_id`      BIGINT   NOT NULL                COMMENT '用户ID',
    `checkin_date` DATE     NOT NULL                COMMENT '签到日期',
    `created_at`   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_checkin_date` (`user_id`, `checkin_date`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '签到记录表';

-- ------------------------------------------------------------
-- 10. 简历优化记录表
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `im_resume_optimization` (
    `id`              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `user_id`         BIGINT       NOT NULL                COMMENT '用户ID',
    `task_id`         VARCHAR(64)  NOT NULL                COMMENT '异步任务ID',
    `resume_text`     LONGTEXT     NOT NULL                COMMENT '简历原文',
    `job_description` TEXT         NULL                    COMMENT '目标岗位描述',
    `result_json`     LONGTEXT     NULL                    COMMENT '优化结果（JSON）',
    `status`          VARCHAR(20)  NOT NULL DEFAULT 'PROCESSING' COMMENT '状态：PROCESSING/DONE/FAILED',
    `error_message`   VARCHAR(1000) NULL                   COMMENT '错误信息',
    `created_at`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_task_id` (`task_id`),
    KEY `idx_user_id` (`user_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '简历优化记录表';

-- ------------------------------------------------------------
-- 11. AI 面试会话表
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `im_interview_session` (
    `id`          BIGINT   NOT NULL                COMMENT '会话ID（Redis计数器生成）',
    `user_id`     BIGINT   NOT NULL                COMMENT '用户ID',
    `title`       VARCHAR(200) NOT NULL            COMMENT '会话标题',
    `difficulty`  TINYINT  NOT NULL DEFAULT 1      COMMENT '面试模式：1综合面试/2算法面试',
    `focus`       TINYINT  NOT NULL DEFAULT 1      COMMENT '面试重点：1综合/2算法',
    `style`       TINYINT  NOT NULL DEFAULT 1      COMMENT '面试风格：1引导式/2压力式',
    `status`      TINYINT  NOT NULL DEFAULT 0      COMMENT '状态：0进行中/1已完成',
    `evaluation`  LONGTEXT NULL                    COMMENT '评估结果（JSON）',
    `started_at`  DATETIME NOT NULL                COMMENT '开始时间',
    `finished_at` DATETIME NULL                    COMMENT '结束时间',
    `created_at`  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_user_status_finished` (`user_id`, `status`, `finished_at` DESC),
    KEY `idx_user_status_difficulty` (`user_id`, `status`, `difficulty`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = 'AI面试会话表';

-- ------------------------------------------------------------
-- 12. AI 面试消息表
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `im_interview_message` (
    `id`         BIGINT   NOT NULL                COMMENT '消息ID（Redis计数器生成）',
    `session_id` BIGINT   NOT NULL                COMMENT '会话ID',
    `role`       VARCHAR(20) NOT NULL             COMMENT '角色：user/assistant',
    `turn_no`    INT      NOT NULL                COMMENT '轮次',
    `content`    LONGTEXT NOT NULL                COMMENT '消息内容',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_session_id` (`session_id`, `id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = 'AI面试消息表';

-- ------------------------------------------------------------
-- 13. 实时视频面试房间表（M3 新增）
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
-- 14. 结构化面试单表（M3 新增，AI 总结的输入）
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
-- 15. AI 面试总结报告表（M3 新增）
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
-- 16. 接口限流日志表（M2 新增，可选）
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

-- ------------------------------------------------------------
-- 执行结果自检（可选）：应返回 16 张表
-- ------------------------------------------------------------
-- SELECT COUNT(*) AS table_count
-- FROM information_schema.tables
-- WHERE table_schema = 'mock_interview'
--   AND table_name LIKE 'im\_%';
