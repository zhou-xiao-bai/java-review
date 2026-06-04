alter table topics
    add column relevance_tier varchar(32) not null default 'CORE',
    add column plan_enabled boolean not null default true,
    add column interview_value integer not null default 3 check (interview_value between 1 and 5);

update topics
set relevance_tier = 'PROJECT',
    interview_value = 4
where source = 'MANUAL';

update topics
set relevance_tier = 'SUPPLEMENT',
    plan_enabled = false,
    interview_value = 1
where code in (
    'java-date-time',
    'java-module-system',
    'spring-resource-loading',
    'spring-validation',
    'spring-security-basics',
    'spring-mvc-cors',
    'spring-mvc-rest-api-design',
    'spring-boot-startup-runners',
    'spring-boot-logging',
    'spring-boot-test-slices',
    'mybatis-lazy-loading',
    'dubbo-generic-invoke',
    'dubbo-qos-monitoring',
    'redis-pubsub'
);

update topics
set interview_value = 5
where code in (
    'spring-transactions',
    'spring-aop',
    'spring-circular-dependency',
    'jvm-gc',
    'jvm-oom-troubleshooting',
    'jvm-thread-dump',
    'concurrency-aqs',
    'concurrency-thread-pool',
    'concurrency-volatile',
    'concurrency-threadlocal',
    'concurrency-synchronized-monitor',
    'concurrency-concurrenthashmap',
    'mysql-indexes',
    'mysql-locks',
    'mysql-transaction-isolation',
    'mysql-mvcc',
    'mysql-explain-plan',
    'mysql-redo-undo-binlog',
    'redis-cache-consistency',
    'redis-distributed-lock',
    'redis-expiration-eviction',
    'redis-hotkey-bigkey',
    'rocketmq-reliability',
    'rocketmq-ordered-message',
    'rocketmq-transaction-message',
    'dubbo-invocation-chain',
    'dubbo-registry-discovery',
    'dubbo-fault-tolerance',
    'distributed-idempotency',
    'distributed-transactions',
    'distributed-outbox-saga'
);
