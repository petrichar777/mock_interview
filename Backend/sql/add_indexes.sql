-- ============================================================
-- 面试准备网站 — 建议索引迁移脚本
-- 生成日期: 2026-05-30
-- 执行前请检查现有索引，避免重复创建（SHOW INDEX FROM table_name）
-- ============================================================

-- 1. 面试会话表 — 用户已完成面试列表（按结束时间倒序）
--    查询: WHERE user_id = ? AND status = ? ORDER BY finished_at DESC
ALTER TABLE im_interview_session
    ADD INDEX idx_user_status_finished (user_id, status, finished_at DESC);

-- 2. 面试会话表 — 按模式筛选已完成会话
--    查询: WHERE user_id = ? AND status = ? AND difficulty = ?
ALTER TABLE im_interview_session
    ADD INDEX idx_user_status_difficulty (user_id, status, difficulty);

-- 3. 练习记录表 — 错题查询核心索引（最重要，数据量最大）
--    查询: WHERE user_id = ? AND is_correct = 0 AND question_id IN (...) GROUP BY question_id
ALTER TABLE im_practice_record
    ADD INDEX idx_user_correct_question_time (user_id, is_correct, question_id, answered_at DESC);

-- 4. 练习记录表 — 模块维度统计
--    查询: WHERE user_id = ? AND module_id = ? / GROUP BY module_id
ALTER TABLE im_practice_record
    ADD INDEX idx_user_module_question (user_id, module_id, question_id);

-- 5. 签到表 — 日期范围查询
--    查询: WHERE user_id = ? AND checkin_date BETWEEN ? AND ?
ALTER TABLE im_checkin_day
    ADD INDEX idx_user_checkin_date (user_id, checkin_date);

-- 6. 用户表 — 防重复注册 + 加速登录查询
--    先检查是否已有唯一索引，避免报错
-- ALTER TABLE im_user ADD UNIQUE INDEX idx_username (username);
-- ALTER TABLE im_user ADD UNIQUE INDEX idx_email (email);
-- 如已有 UNIQUE 约束则跳过
