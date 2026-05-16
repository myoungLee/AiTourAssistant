-- @author myoung
-- 用途：重建本地 MySQL aitour 业务表，并写入可用于接口联调的真实风格测试数据。
-- 说明：本脚本会删除并重建业务表；默认保留 flyway_schema_history，避免 Spring Boot 启动时 Flyway 历史异常。

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

DROP TABLE IF EXISTS tool_call_log;
DROP TABLE IF EXISTS llm_call_log;
DROP TABLE IF EXISTS budget_breakdown;
DROP TABLE IF EXISTS trip_item;
DROP TABLE IF EXISTS trip_day;
DROP TABLE IF EXISTS trip_plan;
DROP TABLE IF EXISTS trip_request;
DROP TABLE IF EXISTS user_profile;
DROP TABLE IF EXISTS users;

SET FOREIGN_KEY_CHECKS = 1;

CREATE TABLE users (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '用户主键，自增',
    username VARCHAR(64) NOT NULL COMMENT '登录用户名，全局唯一',
    password_hash VARCHAR(255) NOT NULL COMMENT '密码字段；当前本地测试阶段按明文存储',
    nickname VARCHAR(64) NULL COMMENT '用户昵称',
    avatar_url VARCHAR(512) NULL COMMENT '头像地址',
    phone VARCHAR(32) NULL COMMENT '手机号',
    email VARCHAR(128) NULL COMMENT '邮箱',
    status VARCHAR(32) NOT NULL COMMENT '用户状态：ENABLED、DISABLED',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_users_username (username),
    KEY idx_users_status_created_at (status, created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '系统用户表，保存登录账号和基础资料';

CREATE TABLE user_profile (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '用户画像主键，自增',
    user_id BIGINT NOT NULL COMMENT '用户 ID',
    gender VARCHAR(32) NULL COMMENT '性别',
    age_range VARCHAR(32) NULL COMMENT '年龄段',
    travel_style VARCHAR(64) NULL COMMENT '旅行风格，例如美食休闲、亲子度假',
    default_budget_level VARCHAR(32) NULL COMMENT '默认预算等级',
    preferred_transport VARCHAR(64) NULL COMMENT '偏好的交通方式',
    preferences_json TEXT NULL COMMENT '用户偏好 JSON',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_profile_user_id (user_id),
    CONSTRAINT fk_user_profile_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '用户旅行画像表，保存偏好、预算和交通习惯';

CREATE TABLE trip_request (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '出行需求主键，自增',
    user_id BIGINT NOT NULL COMMENT '提交需求的用户 ID',
    user_input TEXT NULL COMMENT '用户原始补充需求',
    destination VARCHAR(128) NOT NULL COMMENT '目的地城市或区域',
    start_date DATE NOT NULL COMMENT '出发日期',
    days INT NOT NULL COMMENT '出行天数',
    budget DECIMAL(12, 2) NULL COMMENT '用户填写的总预算',
    people_count INT NOT NULL COMMENT '出行人数',
    preferences_json TEXT NULL COMMENT '偏好标签 JSON',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    KEY idx_trip_request_user_created_at (user_id, created_at),
    KEY idx_trip_request_destination_start_date (destination, start_date),
    CONSTRAINT fk_trip_request_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '用户出行需求表，保存一次规划请求的原始参数';

CREATE TABLE trip_plan (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '行程计划主键，自增',
    user_id BIGINT NOT NULL COMMENT '所属用户 ID',
    request_id BIGINT NOT NULL COMMENT '关联的出行需求 ID',
    title VARCHAR(255) NOT NULL COMMENT '行程标题',
    summary TEXT NULL COMMENT 'AI 生成的行程总结',
    status VARCHAR(32) NOT NULL COMMENT '行程状态：PENDING、GENERATING、GENERATED、FAILED',
    total_budget DECIMAL(12, 2) NULL COMMENT '行程总预算',
    raw_ai_result_json TEXT NULL COMMENT 'AI 和工具原始结果 JSON',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_trip_plan_user_status_updated_at (user_id, status, updated_at),
    KEY idx_trip_plan_request_id (request_id),
    KEY idx_trip_plan_status_created_at (status, created_at),
    CONSTRAINT fk_trip_plan_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_trip_plan_request FOREIGN KEY (request_id) REFERENCES trip_request (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '行程计划主表，保存生成状态、总结和预算';

CREATE TABLE trip_day (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '行程天主键，自增',
    plan_id BIGINT NOT NULL COMMENT '所属行程计划 ID',
    day_index INT NOT NULL COMMENT '第几天，从 1 开始',
    date DATE NOT NULL COMMENT '当天日期',
    city VARCHAR(128) NOT NULL COMMENT '当天所在城市',
    weather_summary VARCHAR(512) NULL COMMENT '天气摘要',
    daily_budget DECIMAL(12, 2) NULL COMMENT '当天预算',
    PRIMARY KEY (id),
    UNIQUE KEY uk_trip_day_plan_day_index (plan_id, day_index),
    KEY idx_trip_day_plan_date (plan_id, date),
    KEY idx_trip_day_city_date (city, date),
    CONSTRAINT fk_trip_day_plan FOREIGN KEY (plan_id) REFERENCES trip_plan (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '行程每日安排表，保存每天的城市、天气和预算';

CREATE TABLE trip_item (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '行程条目主键，自增',
    day_id BIGINT NOT NULL COMMENT '所属行程天 ID',
    time_slot VARCHAR(32) NOT NULL COMMENT '时间段：MORNING、AFTERNOON、EVENING',
    place_name VARCHAR(255) NOT NULL COMMENT '地点名称',
    place_type VARCHAR(64) NULL COMMENT '地点类型：ATTRACTION、FOOD、MUSEUM 等',
    address VARCHAR(512) NULL COMMENT '地点地址',
    duration_minutes INT NULL COMMENT '预计停留分钟数',
    transport_suggestion VARCHAR(512) NULL COMMENT '交通建议',
    estimated_cost DECIMAL(12, 2) NULL COMMENT '预计费用',
    reason VARCHAR(1024) NULL COMMENT '推荐理由',
    PRIMARY KEY (id),
    KEY idx_trip_item_day_time_slot (day_id, time_slot),
    KEY idx_trip_item_place_type (place_type),
    KEY idx_trip_item_place_name (place_name),
    CONSTRAINT fk_trip_item_day FOREIGN KEY (day_id) REFERENCES trip_day (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '行程条目表，保存每天各时间段的景点、美食和活动';

CREATE TABLE budget_breakdown (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '预算明细主键，自增',
    plan_id BIGINT NOT NULL COMMENT '所属行程计划 ID',
    hotel_cost DECIMAL(12, 2) NULL COMMENT '住宿费用',
    food_cost DECIMAL(12, 2) NULL COMMENT '餐饮费用',
    transport_cost DECIMAL(12, 2) NULL COMMENT '交通费用',
    ticket_cost DECIMAL(12, 2) NULL COMMENT '门票费用',
    other_cost DECIMAL(12, 2) NULL COMMENT '其他费用',
    detail_json TEXT NULL COMMENT '预算详细 JSON',
    PRIMARY KEY (id),
    UNIQUE KEY uk_budget_breakdown_plan_id (plan_id),
    CONSTRAINT fk_budget_breakdown_plan FOREIGN KEY (plan_id) REFERENCES trip_plan (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '预算拆分表，保存行程预算的分类估算';

CREATE TABLE llm_call_log (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '大模型调用日志主键，自增',
    user_id BIGINT NULL COMMENT '调用用户 ID',
    plan_id BIGINT NULL COMMENT '关联行程计划 ID',
    provider VARCHAR(64) NOT NULL COMMENT '模型供应商',
    model VARCHAR(128) NOT NULL COMMENT '模型名称',
    prompt_summary TEXT NULL COMMENT '提示词摘要',
    response_summary TEXT NULL COMMENT '模型响应摘要',
    token_usage_json TEXT NULL COMMENT 'Token 使用情况 JSON',
    latency_ms BIGINT NULL COMMENT '调用耗时，毫秒',
    success BOOLEAN NOT NULL COMMENT '是否调用成功',
    error_message TEXT NULL COMMENT '失败信息',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    KEY idx_llm_call_log_user_created_at (user_id, created_at),
    KEY idx_llm_call_log_plan_created_at (plan_id, created_at),
    KEY idx_llm_call_log_success_created_at (success, created_at),
    CONSTRAINT fk_llm_call_log_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE SET NULL,
    CONSTRAINT fk_llm_call_log_plan FOREIGN KEY (plan_id) REFERENCES trip_plan (id) ON DELETE SET NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '大模型调用日志表，记录 Spring AI 调用摘要和耗时';

CREATE TABLE tool_call_log (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '工具调用日志主键，自增',
    user_id BIGINT NULL COMMENT '调用用户 ID',
    plan_id BIGINT NULL COMMENT '关联行程计划 ID',
    tool_name VARCHAR(128) NOT NULL COMMENT 'MCP 工具名称',
    request_json TEXT NULL COMMENT '工具请求 JSON',
    response_summary TEXT NULL COMMENT '工具响应摘要',
    latency_ms BIGINT NULL COMMENT '调用耗时，毫秒',
    success BOOLEAN NOT NULL COMMENT '是否调用成功',
    error_message TEXT NULL COMMENT '失败信息',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    KEY idx_tool_call_log_user_created_at (user_id, created_at),
    KEY idx_tool_call_log_plan_created_at (plan_id, created_at),
    KEY idx_tool_call_log_tool_success_created_at (tool_name, success, created_at),
    CONSTRAINT fk_tool_call_log_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE SET NULL,
    CONSTRAINT fk_tool_call_log_plan FOREIGN KEY (plan_id) REFERENCES trip_plan (id) ON DELETE SET NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'MCP 工具调用日志表，记录天气、景点、路线和预算工具调用';

INSERT INTO users (id, username, password_hash, nickname, avatar_url, phone, email, status, created_at, updated_at) VALUES
(10001, 'zhangsan', '12345678', '张三', 'https://images.unsplash.com/photo-1535713875002-d1d0cf377fde?auto=format&fit=crop&w=200&q=80', '13800000001', 'zhangsan@test.com', 'ENABLED', '2026-05-15 09:00:00', '2026-05-15 09:00:00'),
(10002, 'lisi', '12345678', '李四', 'https://images.unsplash.com/photo-1494790108377-be9c29b29330?auto=format&fit=crop&w=200&q=80', '13800000002', 'lisi@test.com', 'ENABLED', '2026-05-15 09:05:00', '2026-05-15 09:05:00'),
(10003, 'wangwu', '12345678', '王五', 'https://images.unsplash.com/photo-1500648767791-00dcc994a43e?auto=format&fit=crop&w=200&q=80', '13800000003', 'wangwu@test.com', 'ENABLED', '2026-05-15 09:10:00', '2026-05-15 09:10:00');

INSERT INTO user_profile (id, user_id, gender, age_range, travel_style, default_budget_level, preferred_transport, preferences_json, created_at, updated_at) VALUES
(20001, 10001, '男', '25-34', '美食休闲', '中等', '地铁+打车', '{"food":["火锅","川菜"],"pace":"轻松","hotel":"舒适型"}', '2026-05-15 09:00:00', '2026-05-15 09:00:00'),
(20002, 10002, '女', '25-34', '亲子度假', '偏高', '自驾', '{"family":true,"kidsAge":"6-10","pace":"适中","spotTypes":["乐园","动物园"]}', '2026-05-15 09:05:00', '2026-05-15 09:05:00'),
(20003, 10003, '男', '35-44', '文化深度游', '中高', '高铁+步行', '{"culture":true,"museum":true,"pace":"慢游","spotTypes":["古镇","博物馆"]}', '2026-05-15 09:10:00', '2026-05-15 09:10:00');

INSERT INTO trip_request (id, user_id, user_input, destination, start_date, days, budget, people_count, preferences_json, created_at) VALUES
(30001, 10001, '想吃火锅，不要太赶，晚上想逛夜市。', '成都', '2026-06-01', 3, 3200.00, 2, '["美食","轻松","夜市"]', '2026-05-15 10:00:00'),
(30002, 10002, '带孩子出行，希望白天安排轻松一些，酒店环境要好。', '上海', '2026-07-10', 2, 4800.00, 3, '["亲子","乐园","轻松"]', '2026-05-15 10:10:00'),
(30003, 10003, '更关注人文历史，希望路线集中，少折返。', '西安', '2026-08-18', 4, 5600.00, 2, '["文化","博物馆","古迹"]', '2026-05-15 10:20:00'),
(30004, 10001, '预算有限，想要一版北京周末低成本行程。', '北京', '2026-09-05', 2, 1800.00, 2, '["低预算","文化","步行"]', '2026-05-15 10:30:00');

INSERT INTO trip_plan (id, user_id, request_id, title, summary, status, total_budget, raw_ai_result_json, created_at, updated_at) VALUES
(40001, 10001, 30001, '成都3日智能行程', '以宽窄巷子、人民公园、春熙路和火锅体验为主，整体节奏偏轻松，兼顾成都美食与城市文化。', 'GENERATED', 3200.00, '{"weatherSummary":"多云转晴，适合户外活动","source":"seed-data"}', '2026-05-15 10:01:00', '2026-05-15 10:03:00'),
(40002, 10002, 30002, '上海2日亲子度假行程', '围绕迪士尼和浦东亲子活动安排，两天节奏适中，兼顾孩子体验与家长休息。', 'GENERATED', 4800.00, '{"weatherSummary":"阵雨概率较低，建议随身携带轻便雨具","source":"seed-data"}', '2026-05-15 10:11:00', '2026-05-15 10:13:00'),
(40003, 10003, 30003, '西安4日文化深度行程', '围绕兵马俑、陕西历史博物馆、大雁塔和城墙展开，注重历史脉络与路线集中度。', 'GENERATED', 5600.00, '{"weatherSummary":"晴到多云，午后偏热，适合上午优先安排户外","source":"seed-data"}', '2026-05-15 10:21:00', '2026-05-15 10:25:00'),
(40004, 10001, 30004, '北京2日低预算文化行程', '当前计划处于待生成状态，可用于测试历史列表中的 PENDING 状态展示。', 'PENDING', 1800.00, '{"source":"seed-data","note":"pending plan"}', '2026-05-15 10:31:00', '2026-05-15 10:31:00');

INSERT INTO trip_day (id, plan_id, day_index, date, city, weather_summary, daily_budget) VALUES
(50001, 40001, 1, '2026-06-01', '成都', '多云，气温22-30℃', 1066.67),
(50002, 40001, 2, '2026-06-02', '成都', '晴，气温23-31℃', 1066.67),
(50003, 40001, 3, '2026-06-03', '成都', '晴转多云，气温22-29℃', 1066.66),
(50004, 40002, 1, '2026-07-10', '上海', '多云，午后局部阵雨', 2400.00),
(50005, 40002, 2, '2026-07-11', '上海', '阴转多云，体感较闷', 2400.00),
(50006, 40003, 1, '2026-08-18', '西安', '晴，午后偏热', 1400.00),
(50007, 40003, 2, '2026-08-19', '西安', '多云，适合户外', 1400.00),
(50008, 40003, 3, '2026-08-20', '西安', '晴转多云', 1400.00),
(50009, 40003, 4, '2026-08-21', '西安', '多云，夜间微风', 1400.00);

INSERT INTO trip_item (id, day_id, time_slot, place_name, place_type, address, duration_minutes, transport_suggestion, estimated_cost, reason) VALUES
(60001, 50001, 'MORNING', '宽窄巷子', 'CULTURE', '成都市青羊区宽窄巷子', 120, '地铁优先，步行衔接', 120.00, '第一天先安排经典城市文化地标，便于快速建立对成都的整体印象。'),
(60002, 50001, 'AFTERNOON', '人民公园鹤鸣茶社', 'LEISURE', '成都市青羊区少城路12号', 150, '步行+短途打车', 180.00, '结合轻松偏好安排本地慢生活体验。'),
(60003, 50001, 'EVENING', '奎星楼街夜市', 'FOOD', '成都市青羊区奎星楼街', 180, '打车返程更方便', 260.00, '满足夜市和美食需求，晚间氛围更好。'),
(60004, 50002, 'MORNING', '成都博物馆', 'MUSEUM', '成都市青羊区小河街1号', 150, '地铁直达', 80.00, '室内项目适合上午深度参观。'),
(60005, 50002, 'AFTERNOON', '杜甫草堂', 'CULTURE', '成都市青羊区青华路37号', 150, '地铁+步行', 120.00, '兼顾人文体验和园林环境。'),
(60006, 50002, 'EVENING', '蜀大侠火锅', 'FOOD', '成都市锦江区春熙路商圈', 120, '打车前往', 360.00, '安排成都代表性火锅体验。'),
(60007, 50003, 'MORNING', '大熊猫繁育研究基地', 'ATTRACTION', '成都市成华区外北熊猫大道1375号', 180, '建议网约车直达', 220.00, '第三天安排热门景点作为收尾。'),
(60008, 50003, 'AFTERNOON', '春熙路太古里', 'SHOPPING', '成都市锦江区中纱帽街8号', 180, '地铁+步行', 260.00, '购物与城市街区体验结合。'),
(60009, 50004, 'MORNING', '上海迪士尼乐园', 'THEME_PARK', '上海市浦东新区川沙新镇黄赵路310号', 300, '建议地铁11号线或打车', 980.00, '亲子核心行程，第一天集中安排乐园。'),
(60010, 50004, 'EVENING', '迪士尼小镇', 'LEISURE', '上海市浦东新区申迪西路255弄', 120, '步行', 180.00, '孩子体力消耗后保留轻松活动。'),
(60011, 50005, 'MORNING', '上海海昌海洋公园', 'ATTRACTION', '上海市浦东新区银飞路166号', 240, '建议自驾或打车', 760.00, '延续亲子主题，且游玩强度低于乐园全天行程。'),
(60012, 50005, 'AFTERNOON', '前滩休闲公园', 'LEISURE', '上海市浦东新区前滩大道', 120, '自驾或打车', 120.00, '安排孩子放松和家长休息时间。'),
(60013, 50006, 'MORNING', '陕西历史博物馆', 'MUSEUM', '西安市雁塔区小寨东路91号', 180, '地铁优先', 120.00, '先建立历史脉络，再进入古迹类景点。'),
(60014, 50006, 'AFTERNOON', '大雁塔', 'CULTURE', '西安市雁塔区大慈恩寺内', 150, '步行衔接', 160.00, '与博物馆区域接近，减少折返。'),
(60015, 50007, 'MORNING', '秦始皇兵马俑博物馆', 'ATTRACTION', '西安市临潼区秦陵北路', 240, '建议包车或专车往返', 520.00, '核心历史景点，单独安排半天以上。'),
(60016, 50007, 'AFTERNOON', '华清宫', 'CULTURE', '西安市临潼区华清路38号', 150, '同线路衔接', 220.00, '与兵马俑同向，压缩交通成本。'),
(60017, 50008, 'MORNING', '西安城墙', 'CULTURE', '西安市碑林区南大街', 150, '地铁+步行', 160.00, '安排城市地标和慢节奏骑行体验。'),
(60018, 50008, 'EVENING', '回民街', 'FOOD', '西安市莲湖区北院门', 180, '步行或短途打车', 260.00, '安排当地饮食体验和夜游氛围。'),
(60019, 50009, 'MORNING', '碑林博物馆', 'MUSEUM', '西安市碑林区三学街15号', 120, '地铁优先', 120.00, '最后一天安排相对轻松的人文收尾。'),
(60020, 50009, 'AFTERNOON', '小雁塔', 'CULTURE', '西安市碑林区友谊西路72号', 120, '步行+地铁', 100.00, '结束前保持路线集中，不再拉长交通距离。');

INSERT INTO budget_breakdown (id, plan_id, hotel_cost, food_cost, transport_cost, ticket_cost, other_cost, detail_json) VALUES
(70001, 40001, 1200.00, 900.00, 350.00, 500.00, 250.00, '{"hotel":1200,"food":900,"transport":350,"ticket":500,"other":250,"source":"seed-data"}'),
(70002, 40002, 2200.00, 900.00, 500.00, 900.00, 300.00, '{"hotel":2200,"food":900,"transport":500,"ticket":900,"other":300,"source":"seed-data"}'),
(70003, 40003, 2000.00, 1200.00, 700.00, 1200.00, 500.00, '{"hotel":2000,"food":1200,"transport":700,"ticket":1200,"other":500,"source":"seed-data"}');

INSERT INTO tool_call_log (id, user_id, plan_id, tool_name, request_json, response_summary, latency_ms, success, error_message, created_at) VALUES
(80001, 10001, 40001, 'weather.query', '{"city":"成都"}', '成都未来三天以多云和晴天为主，适合户外活动。', 180, 1, NULL, '2026-05-15 10:01:10'),
(80002, 10001, 40001, 'place.search', '{"city":"成都","preferences":["美食","轻松","夜市"]}', '已返回宽窄巷子、人民公园、春熙路等候选景点。', 220, 1, NULL, '2026-05-15 10:01:20'),
(80003, 10001, 40001, 'route.plan', '{"from":"宽窄巷子","to":"春熙路"}', '路线建议为地铁换乘+短步行，总耗时约35分钟。', 145, 1, NULL, '2026-05-15 10:01:25'),
(80004, 10001, 40001, 'budget.estimate', '{"days":3,"peopleCount":2}', '当前预算可覆盖中等舒适度出行。', 90, 1, NULL, '2026-05-15 10:01:28'),
(80005, 10002, 40002, 'weather.query', '{"city":"上海"}', '上海两天多云为主，午后可能有短时阵雨。', 160, 1, NULL, '2026-05-15 10:11:10'),
(80006, 10002, 40002, 'place.search', '{"city":"上海","preferences":["亲子","乐园","轻松"]}', '已返回迪士尼、海洋公园、亲子休闲区候选结果。', 210, 1, NULL, '2026-05-15 10:11:18'),
(80007, 10003, 40003, 'weather.query', '{"city":"西安"}', '西安四天以晴到多云为主，中午偏热。', 170, 1, NULL, '2026-05-15 10:21:10'),
(80008, 10003, 40003, 'place.search', '{"city":"西安","preferences":["文化","博物馆","古迹"]}', '已返回兵马俑、陕历博、大雁塔、城墙等候选。', 230, 1, NULL, '2026-05-15 10:21:20'),
(80009, 10001, 40004, 'budget.estimate', '{"days":2,"peopleCount":2}', '预算偏紧，建议优先公共交通和免费公共文化空间。', 95, 1, NULL, '2026-05-15 10:31:30');

INSERT INTO llm_call_log (id, user_id, plan_id, provider, model, prompt_summary, response_summary, token_usage_json, latency_ms, success, error_message, created_at) VALUES
(90001, 10001, 40001, 'spring-ai-openai', 'gpt-5.4', '根据成都3日、美食休闲、预算3200生成总结', '成都行程以轻松节奏串联美食、茶馆和城市地标，适合周末慢游。', '{"promptTokens":520,"completionTokens":210}', 820, 1, NULL, '2026-05-15 10:02:00'),
(90002, 10002, 40002, 'spring-ai-openai', 'gpt-5.4', '根据上海2日亲子出行、预算4800生成总结', '上海亲子行程围绕迪士尼与海洋公园展开，保留午后休整时间，适合家庭度假。', '{"promptTokens":610,"completionTokens":240}', 910, 1, NULL, '2026-05-15 10:12:00'),
(90003, 10003, 40003, 'spring-ai-openai', 'gpt-5.4', '根据西安4日文化深度游、预算5600生成总结', '西安文化行程强调历史脉络与空间集中度，从馆藏到古迹逐步展开。', '{"promptTokens":680,"completionTokens":260}', 980, 1, NULL, '2026-05-15 10:23:00');

ALTER TABLE users AUTO_INCREMENT = 10004;
ALTER TABLE user_profile AUTO_INCREMENT = 20004;
ALTER TABLE trip_request AUTO_INCREMENT = 30005;
ALTER TABLE trip_plan AUTO_INCREMENT = 40005;
ALTER TABLE trip_day AUTO_INCREMENT = 50010;
ALTER TABLE trip_item AUTO_INCREMENT = 60021;
ALTER TABLE budget_breakdown AUTO_INCREMENT = 70004;
ALTER TABLE tool_call_log AUTO_INCREMENT = 80010;
ALTER TABLE llm_call_log AUTO_INCREMENT = 90004;
