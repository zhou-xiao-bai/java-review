create table domains (
    id uuid primary key,
    code varchar(64) not null unique,
    name varchar(80) not null,
    sort_order integer not null,
    created_at timestamptz not null
);

create table topics (
    id uuid primary key,
    domain_id uuid not null references domains (id),
    code varchar(96) not null unique,
    title varchar(120) not null,
    source varchar(32) not null,
    selected boolean not null default false,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    constraint uq_topics_domain_title unique (domain_id, title)
);

create index idx_topics_domain on topics (domain_id);
create index idx_topics_selected on topics (selected);

create table review_points (
    id uuid primary key,
    topic_id uuid not null references topics (id) on delete cascade,
    title varchar(160) not null,
    importance integer not null check (importance between 1 and 5),
    difficulty integer not null check (difficulty between 1 and 5),
    interview_frequency integer not null check (interview_frequency between 1 and 5),
    mastery numeric(3, 2) not null default 0 check (mastery >= 0 and mastery <= 5),
    status varchar(32) not null,
    last_reviewed_at timestamptz,
    next_review_at timestamptz,
    review_count integer not null default 0,
    wrong_count integer not null default 0,
    weak_points jsonb not null default '[]'::jsonb,
    next_probe text,
    recent_question_types jsonb not null default '[]'::jsonb,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    constraint uq_review_points_topic_title unique (topic_id, title)
);

create index idx_review_points_topic on review_points (topic_id);
create index idx_review_points_status on review_points (status);
create index idx_review_points_next_review_at on review_points (next_review_at);

insert into domains (id, code, name, sort_order, created_at) values
    ('00000000-0000-4000-8000-000000000001', 'java-foundation', 'Java 基础', 10, now()),
    ('00000000-0000-4000-8000-000000000002', 'jvm', 'JVM', 20, now()),
    ('00000000-0000-4000-8000-000000000003', 'concurrency', '并发', 30, now()),
    ('00000000-0000-4000-8000-000000000004', 'spring', 'Spring', 40, now()),
    ('00000000-0000-4000-8000-000000000005', 'spring-mvc', 'Spring MVC', 50, now()),
    ('00000000-0000-4000-8000-000000000006', 'spring-boot', 'Spring Boot', 60, now()),
    ('00000000-0000-4000-8000-000000000007', 'mybatis', 'MyBatis', 70, now()),
    ('00000000-0000-4000-8000-000000000008', 'mysql', 'MySQL', 80, now()),
    ('00000000-0000-4000-8000-000000000009', 'redis', 'Redis', 90, now()),
    ('00000000-0000-4000-8000-000000000010', 'rocketmq', 'RocketMQ', 100, now()),
    ('00000000-0000-4000-8000-000000000011', 'dubbo', 'Dubbo', 110, now()),
    ('00000000-0000-4000-8000-000000000012', 'netty', 'Netty', 120, now());

insert into topics (id, domain_id, code, title, source, selected, created_at, updated_at) values
    ('10000000-0000-4000-8000-000000000001', '00000000-0000-4000-8000-000000000001', 'java-collections', '集合框架', 'BUILTIN', false, now(), now()),
    ('10000000-0000-4000-8000-000000000002', '00000000-0000-4000-8000-000000000001', 'java-generics', '泛型机制', 'BUILTIN', false, now(), now()),
    ('10000000-0000-4000-8000-000000000003', '00000000-0000-4000-8000-000000000001', 'java-reflection-annotations', '反射与注解', 'BUILTIN', false, now(), now()),
    ('10000000-0000-4000-8000-000000000004', '00000000-0000-4000-8000-000000000001', 'java-class-loading-basics', '类加载基础', 'BUILTIN', false, now(), now()),
    ('10000000-0000-4000-8000-000000000005', '00000000-0000-4000-8000-000000000002', 'jvm-memory-model', 'JVM 内存模型', 'BUILTIN', false, now(), now()),
    ('10000000-0000-4000-8000-000000000006', '00000000-0000-4000-8000-000000000002', 'jvm-gc', 'JVM GC', 'BUILTIN', false, now(), now()),
    ('10000000-0000-4000-8000-000000000007', '00000000-0000-4000-8000-000000000002', 'jvm-class-loading', '类加载机制', 'BUILTIN', false, now(), now()),
    ('10000000-0000-4000-8000-000000000008', '00000000-0000-4000-8000-000000000002', 'jvm-oom-troubleshooting', 'OOM 排查', 'BUILTIN', false, now(), now()),
    ('10000000-0000-4000-8000-000000000009', '00000000-0000-4000-8000-000000000003', 'concurrency-aqs', 'AQS', 'BUILTIN', false, now(), now()),
    ('10000000-0000-4000-8000-000000000010', '00000000-0000-4000-8000-000000000003', 'concurrency-thread-pool', '线程池', 'BUILTIN', false, now(), now()),
    ('10000000-0000-4000-8000-000000000011', '00000000-0000-4000-8000-000000000003', 'concurrency-volatile', 'volatile', 'BUILTIN', false, now(), now()),
    ('10000000-0000-4000-8000-000000000012', '00000000-0000-4000-8000-000000000003', 'concurrency-threadlocal', 'ThreadLocal', 'BUILTIN', false, now(), now()),
    ('10000000-0000-4000-8000-000000000013', '00000000-0000-4000-8000-000000000004', 'spring-ioc', 'Spring IoC', 'BUILTIN', false, now(), now()),
    ('10000000-0000-4000-8000-000000000014', '00000000-0000-4000-8000-000000000004', 'spring-aop', 'Spring AOP', 'BUILTIN', false, now(), now()),
    ('10000000-0000-4000-8000-000000000015', '00000000-0000-4000-8000-000000000004', 'spring-transactions', 'Spring 事务', 'BUILTIN', false, now(), now()),
    ('10000000-0000-4000-8000-000000000016', '00000000-0000-4000-8000-000000000004', 'spring-bean-lifecycle', 'Bean 生命周期', 'BUILTIN', false, now(), now()),
    ('10000000-0000-4000-8000-000000000017', '00000000-0000-4000-8000-000000000005', 'spring-mvc-request-flow', '请求处理流程', 'BUILTIN', false, now(), now()),
    ('10000000-0000-4000-8000-000000000018', '00000000-0000-4000-8000-000000000005', 'spring-mvc-argument-binding', '参数绑定', 'BUILTIN', false, now(), now()),
    ('10000000-0000-4000-8000-000000000019', '00000000-0000-4000-8000-000000000005', 'spring-mvc-exception-handling', '异常处理', 'BUILTIN', false, now(), now()),
    ('10000000-0000-4000-8000-000000000020', '00000000-0000-4000-8000-000000000005', 'spring-mvc-interceptors', '拦截器', 'BUILTIN', false, now(), now()),
    ('10000000-0000-4000-8000-000000000021', '00000000-0000-4000-8000-000000000006', 'spring-boot-auto-configuration', '自动配置', 'BUILTIN', false, now(), now()),
    ('10000000-0000-4000-8000-000000000022', '00000000-0000-4000-8000-000000000006', 'spring-boot-starter', 'Starter 机制', 'BUILTIN', false, now(), now()),
    ('10000000-0000-4000-8000-000000000023', '00000000-0000-4000-8000-000000000006', 'spring-boot-config-loading', '配置加载', 'BUILTIN', false, now(), now()),
    ('10000000-0000-4000-8000-000000000024', '00000000-0000-4000-8000-000000000006', 'spring-boot-actuator', 'Actuator', 'BUILTIN', false, now(), now()),
    ('10000000-0000-4000-8000-000000000025', '00000000-0000-4000-8000-000000000007', 'mybatis-execution-flow', '执行流程', 'BUILTIN', false, now(), now()),
    ('10000000-0000-4000-8000-000000000026', '00000000-0000-4000-8000-000000000007', 'mybatis-cache', '一级与二级缓存', 'BUILTIN', false, now(), now()),
    ('10000000-0000-4000-8000-000000000027', '00000000-0000-4000-8000-000000000007', 'mybatis-plugin', '插件机制', 'BUILTIN', false, now(), now()),
    ('10000000-0000-4000-8000-000000000028', '00000000-0000-4000-8000-000000000007', 'mybatis-dynamic-sql', '动态 SQL', 'BUILTIN', false, now(), now()),
    ('10000000-0000-4000-8000-000000000029', '00000000-0000-4000-8000-000000000008', 'mysql-indexes', '索引', 'BUILTIN', false, now(), now()),
    ('10000000-0000-4000-8000-000000000030', '00000000-0000-4000-8000-000000000008', 'mysql-locks', '锁', 'BUILTIN', false, now(), now()),
    ('10000000-0000-4000-8000-000000000031', '00000000-0000-4000-8000-000000000008', 'mysql-transaction-isolation', '事务隔离', 'BUILTIN', false, now(), now()),
    ('10000000-0000-4000-8000-000000000032', '00000000-0000-4000-8000-000000000008', 'mysql-mvcc', 'MVCC', 'BUILTIN', false, now(), now()),
    ('10000000-0000-4000-8000-000000000033', '00000000-0000-4000-8000-000000000009', 'redis-data-structures', '数据结构', 'BUILTIN', false, now(), now()),
    ('10000000-0000-4000-8000-000000000034', '00000000-0000-4000-8000-000000000009', 'redis-cache-consistency', '缓存一致性', 'BUILTIN', false, now(), now()),
    ('10000000-0000-4000-8000-000000000035', '00000000-0000-4000-8000-000000000009', 'redis-distributed-lock', '分布式锁', 'BUILTIN', false, now(), now()),
    ('10000000-0000-4000-8000-000000000036', '00000000-0000-4000-8000-000000000009', 'redis-expiration-eviction', '过期淘汰', 'BUILTIN', false, now(), now()),
    ('10000000-0000-4000-8000-000000000037', '00000000-0000-4000-8000-000000000010', 'rocketmq-reliability', '消息可靠性', 'BUILTIN', false, now(), now()),
    ('10000000-0000-4000-8000-000000000038', '00000000-0000-4000-8000-000000000010', 'rocketmq-ordered-message', '顺序消息', 'BUILTIN', false, now(), now()),
    ('10000000-0000-4000-8000-000000000039', '00000000-0000-4000-8000-000000000010', 'rocketmq-transaction-message', '事务消息', 'BUILTIN', false, now(), now()),
    ('10000000-0000-4000-8000-000000000040', '00000000-0000-4000-8000-000000000010', 'rocketmq-consume-retry', '消费重试', 'BUILTIN', false, now(), now()),
    ('10000000-0000-4000-8000-000000000041', '00000000-0000-4000-8000-000000000011', 'dubbo-invocation-chain', '调用链路', 'BUILTIN', false, now(), now()),
    ('10000000-0000-4000-8000-000000000042', '00000000-0000-4000-8000-000000000011', 'dubbo-registry-discovery', '注册发现', 'BUILTIN', false, now(), now()),
    ('10000000-0000-4000-8000-000000000043', '00000000-0000-4000-8000-000000000011', 'dubbo-load-balancing', '负载均衡', 'BUILTIN', false, now(), now()),
    ('10000000-0000-4000-8000-000000000044', '00000000-0000-4000-8000-000000000011', 'dubbo-fault-tolerance', '容错机制', 'BUILTIN', false, now(), now()),
    ('10000000-0000-4000-8000-000000000045', '00000000-0000-4000-8000-000000000012', 'netty-reactor', 'Reactor 模型', 'BUILTIN', false, now(), now()),
    ('10000000-0000-4000-8000-000000000046', '00000000-0000-4000-8000-000000000012', 'netty-eventloop', 'EventLoop', 'BUILTIN', false, now(), now()),
    ('10000000-0000-4000-8000-000000000047', '00000000-0000-4000-8000-000000000012', 'netty-channelpipeline', 'ChannelPipeline', 'BUILTIN', false, now(), now()),
    ('10000000-0000-4000-8000-000000000048', '00000000-0000-4000-8000-000000000012', 'netty-bytebuf', 'ByteBuf', 'BUILTIN', false, now(), now());
