package com.javareview.topic;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.javareview.common.ResourceNotFoundException;
import com.javareview.reviewpoint.AutoPlanTier;
import com.javareview.reviewpoint.ReviewPoint;
import com.javareview.reviewpoint.ReviewPointRepository;
import com.javareview.reviewpoint.ReviewPointStatus;
import com.javareview.topic.TopicDtos.CreateTopicRequest;
import com.javareview.topic.TopicDtos.DomainTopicsResponse;
import com.javareview.topic.TopicDtos.TopicSummaryResponse;
import com.javareview.topic.TopicDtos.TopicTotalsResponse;
import com.javareview.topic.TopicDtos.TopicsResponse;
import com.javareview.topic.TopicDtos.UpdateTopicPlanningRequest;
import com.javareview.topic.TopicDtos.UpdateTopicSelectionRequest;
import com.javareview.topic.TopicDtos.UpdateTopicSelectionsRequest;

@Service
public class TopicService {

	private static final BigDecimal ZERO_MASTERY = BigDecimal.ZERO.setScale(2);
	private static final List<PointTemplate> DEFAULT_TEMPLATES = List.of(
			PointTemplate.core("%s 核心机制与调用链路", 4, 3, 4),
			PointTemplate.core("%s 高频面试边界", 5, 4, 5),
			PointTemplate.core("%s 生产故障排查", 5, 4, 4),
			PointTemplate.expand("%s 对比取舍与反例", 4, 4, 4),
			PointTemplate.optional("%s 两分钟表达结构", 3, 3, 5));
	private static final Map<String, List<PointTemplate>> BUILTIN_DOMAIN_TEMPLATES = Map.ofEntries(
			Map.entry("java-foundation", List.of(
					new PointTemplate("%s API 语义与常见误用", 4, 3, 5),
					new PointTemplate("%s 源码结构与复杂度", 4, 4, 4),
					new PointTemplate("%s 与语言特性的边界", 4, 4, 4),
					new PointTemplate("%s 线上排查与代码审查点", 4, 4, 4),
					new PointTemplate("%s 面试表达闭环", 3, 3, 5))),
			Map.entry("jvm", List.of(
					new PointTemplate("%s 运行时机制", 5, 4, 5),
					new PointTemplate("%s 参数与工具观测", 5, 5, 5),
					new PointTemplate("%s 性能退化与故障边界", 5, 5, 5),
					new PointTemplate("%s 与应用代码的关联", 4, 4, 4),
					new PointTemplate("%s 生产排查路径", 5, 5, 5))),
			Map.entry("concurrency", List.of(
					new PointTemplate("%s 内存可见性与有序性", 5, 5, 5),
					new PointTemplate("%s 阻塞、唤醒与中断边界", 5, 5, 5),
					new PointTemplate("%s 线程安全失效场景", 5, 4, 5),
					new PointTemplate("%s 性能取舍与压测指标", 4, 5, 4),
					new PointTemplate("%s 生产排查路径", 5, 5, 5))),
			Map.entry("spring", List.of(
					new PointTemplate("%s 容器机制与源码入口", 5, 4, 5),
					new PointTemplate("%s 代理与生命周期边界", 5, 4, 5),
					new PointTemplate("%s 配置生效与失效场景", 4, 4, 4),
					new PointTemplate("%s 生产问题排查", 5, 4, 5),
					new PointTemplate("%s 面试表达闭环", 4, 3, 5))),
			Map.entry("spring-mvc", List.of(
					new PointTemplate("%s 请求链路与扩展点", 5, 4, 5),
					new PointTemplate("%s 参数、响应与异常边界", 4, 4, 5),
					new PointTemplate("%s 与 Servlet/Spring 组件关系", 4, 4, 4),
					new PointTemplate("%s 线上问题排查", 5, 4, 4),
					new PointTemplate("%s 面试表达闭环", 4, 3, 5))),
			Map.entry("spring-boot", List.of(
					new PointTemplate("%s 自动装配与条件判断", 5, 4, 5),
					new PointTemplate("%s 配置加载与覆盖顺序", 5, 4, 5),
					new PointTemplate("%s 启动链路与诊断入口", 4, 4, 4),
					new PointTemplate("%s 生产治理与可观测性", 4, 4, 4),
					new PointTemplate("%s 面试表达闭环", 4, 3, 5))),
			Map.entry("mybatis", List.of(
					new PointTemplate("%s 执行链路与核心对象", 5, 4, 5),
					new PointTemplate("%s SQL 映射与缓存边界", 5, 4, 5),
					new PointTemplate("%s 插件扩展与失效场景", 4, 4, 4),
					new PointTemplate("%s 线上慢 SQL 排查", 5, 4, 5),
					new PointTemplate("%s 面试表达闭环", 4, 3, 5))),
			Map.entry("mysql", List.of(
					new PointTemplate("%s InnoDB 核心机制", 5, 4, 5),
					new PointTemplate("%s 索引、锁与事务边界", 5, 5, 5),
					new PointTemplate("%s 执行计划与性能诊断", 5, 5, 5),
					new PointTemplate("%s 生产故障排查", 5, 5, 5),
					new PointTemplate("%s 面试表达闭环", 4, 3, 5))),
			Map.entry("redis", List.of(
					new PointTemplate("%s 数据结构与命令语义", 5, 4, 5),
					new PointTemplate("%s 内存、过期与持久化边界", 5, 4, 5),
					new PointTemplate("%s 高可用与集群行为", 5, 5, 5),
					new PointTemplate("%s 生产问题排查", 5, 5, 5),
					new PointTemplate("%s 面试表达闭环", 4, 3, 5))),
			Map.entry("rocketmq", List.of(
					new PointTemplate("%s Broker/NameServer 链路", 5, 4, 5),
					new PointTemplate("%s 可靠性、顺序与重试边界", 5, 4, 5),
					new PointTemplate("%s 消费进度与幂等治理", 5, 4, 5),
					new PointTemplate("%s 线上问题排查", 5, 5, 5),
					new PointTemplate("%s 面试表达闭环", 4, 3, 5))),
			Map.entry("dubbo", List.of(
					new PointTemplate("%s 调用链与扩展点", 5, 4, 5),
					new PointTemplate("%s 注册、路由与集群容错", 5, 4, 5),
					new PointTemplate("%s 超时、重试与幂等边界", 5, 4, 5),
					new PointTemplate("%s 线上问题排查", 5, 4, 5),
					new PointTemplate("%s 面试表达闭环", 4, 3, 5))),
			Map.entry("netty", List.of(
					new PointTemplate("%s 事件循环与线程模型", 5, 4, 5),
					new PointTemplate("%s 内存、编解码与背压边界", 5, 5, 5),
					new PointTemplate("%s Channel 生命周期与异常传播", 5, 4, 5),
					new PointTemplate("%s 线上问题排查", 5, 5, 5),
					new PointTemplate("%s 面试表达闭环", 4, 3, 5))),
			Map.entry("kafka", List.of(
					new PointTemplate("%s 生产消费链路", 5, 4, 5),
					new PointTemplate("%s 分区、副本与一致性边界", 5, 5, 5),
					new PointTemplate("%s 位移、事务与幂等语义", 5, 5, 5),
					new PointTemplate("%s 性能调优与故障排查", 5, 5, 5),
					new PointTemplate("%s 面试表达闭环", 4, 3, 5))),
			Map.entry("distributed-systems", List.of(
					new PointTemplate("%s 一致性与可用性边界", 5, 5, 5),
					new PointTemplate("%s 幂等、重试与补偿机制", 5, 4, 5),
					new PointTemplate("%s 容量、降级与隔离策略", 5, 4, 5),
					new PointTemplate("%s 线上问题排查", 5, 5, 5),
					new PointTemplate("%s 面试表达闭环", 4, 3, 5))),
			Map.entry("computer-basics", List.of(
					new PointTemplate("%s 核心协议与系统调用链路", 5, 4, 5),
					new PointTemplate("%s 性能指标与观测手段", 4, 4, 4),
					new PointTemplate("%s 失效边界与异常场景", 5, 4, 5),
					new PointTemplate("%s 生产排查路径", 5, 5, 5),
					new PointTemplate("%s 面试表达闭环", 4, 3, 5))));
	private static final Map<String, List<PointTemplate>> BUILTIN_TEMPLATES = Map.ofEntries(
			Map.entry("spring-transactions", List.of(
					new PointTemplate("事务代理生效边界", 5, 4, 5),
					new PointTemplate("传播行为与嵌套调用", 5, 4, 5),
					new PointTemplate("异常捕获与回滚规则", 5, 4, 5),
					new PointTemplate("事务上下文与线程绑定", 4, 4, 4),
					new PointTemplate("异步调用中的事务边界", 4, 5, 4),
					new PointTemplate("生产事务失效排查", 5, 5, 5))),
			Map.entry("jvm-gc", List.of(
					new PointTemplate("垃圾回收器选择与适用场景", 5, 4, 5),
					new PointTemplate("对象存活判定与引用类型", 4, 3, 4),
					new PointTemplate("分代假设与晋升机制", 4, 4, 4),
					new PointTemplate("GC 日志关键指标解读", 5, 5, 5),
					new PointTemplate("停顿时间与吞吐量取舍", 4, 4, 5))),
			Map.entry("mysql-locks", List.of(
					new PointTemplate("行锁、间隙锁与临键锁", 5, 5, 5),
					new PointTemplate("锁等待与死锁排查", 5, 4, 5),
					new PointTemplate("索引命中对加锁范围的影响", 5, 5, 5),
					new PointTemplate("当前读与快照读边界", 4, 4, 4),
					new PointTemplate("生产慢事务治理", 4, 4, 4))),
			Map.entry("redis-cache-consistency", List.of(
					new PointTemplate("缓存更新策略选择", 5, 4, 5),
					new PointTemplate("双写不一致窗口分析", 5, 5, 5),
					new PointTemplate("延迟双删与消息补偿", 4, 4, 4),
					new PointTemplate("缓存击穿、穿透、雪崩治理", 5, 4, 5),
					new PointTemplate("一致性与可用性的取舍表达", 4, 4, 5))),
			Map.entry("concurrency-aqs", List.of(
					new PointTemplate("AQS 同步队列结构", 5, 5, 5),
					new PointTemplate("独占与共享获取流程", 5, 5, 5),
					new PointTemplate("Condition 等待队列转换", 4, 5, 4),
					new PointTemplate("ReentrantLock 与 Semaphore 应用边界", 4, 4, 4),
					new PointTemplate("阻塞唤醒与中断处理", 4, 4, 4))),
			Map.entry("rocketmq-reliability", List.of(
					new PointTemplate("生产者发送可靠性", 5, 4, 5),
					new PointTemplate("Broker 刷盘与复制策略", 5, 4, 5),
					new PointTemplate("消费者幂等与重试", 5, 4, 5),
					new PointTemplate("消息丢失排查链路", 5, 5, 5),
					new PointTemplate("可靠性与延迟吞吐取舍", 4, 4, 4))),
			Map.entry("dubbo-invocation-chain", List.of(
					new PointTemplate("代理生成与调用入口", 4, 4, 4),
					new PointTemplate("Invoker、Filter、Cluster 调用链", 5, 5, 5),
					new PointTemplate("注册发现与路由选择", 4, 4, 4),
					new PointTemplate("超时、重试和幂等边界", 5, 4, 5),
					new PointTemplate("调用异常排查路径", 5, 4, 5))),
			Map.entry("netty-bytebuf", List.of(
					new PointTemplate("ByteBuf 读写指针模型", 5, 4, 5),
					new PointTemplate("堆外内存与池化分配", 5, 5, 5),
					new PointTemplate("引用计数与内存泄漏", 5, 5, 5),
					new PointTemplate("零拷贝相关 API 边界", 4, 4, 4),
					new PointTemplate("粘包拆包中的缓冲区使用", 4, 4, 4))));

	private final DomainRepository domainRepository;
	private final TopicRepository topicRepository;
	private final ReviewPointRepository reviewPointRepository;

	public TopicService(
			DomainRepository domainRepository,
			TopicRepository topicRepository,
			ReviewPointRepository reviewPointRepository) {
		this.domainRepository = domainRepository;
		this.topicRepository = topicRepository;
		this.reviewPointRepository = reviewPointRepository;
	}

	@Transactional(readOnly = true)
	public TopicsResponse listTopics(String search) {
		List<Domain> domains = domainRepository.findAllByOrderBySortOrderAscNameAsc();
		List<Topic> topics = topicRepository.findAllWithDomain();
		Map<UUID, List<ReviewPoint>> pointsByTopic = loadPointsByTopic(topics.stream().map(Topic::getId).toList());
		String normalizedSearch = normalizeSearch(search);

		List<DomainTopicsResponse> domainResponses = new ArrayList<>();
		for (Domain domain : domains) {
			List<TopicSummaryResponse> topicResponses = topics.stream()
					.filter(topic -> topic.getDomain().getId().equals(domain.getId()))
					.filter(topic -> matchesSearch(topic, normalizedSearch))
					.map(topic -> toTopicSummary(topic, pointsByTopic.getOrDefault(topic.getId(), List.of())))
					.toList();

			if (!topicResponses.isEmpty() || normalizedSearch == null) {
				domainResponses.add(new DomainTopicsResponse(
						domain.getId(),
						domain.getCode(),
						domain.getName(),
						topicResponses.size(),
						topicResponses.stream().filter(TopicSummaryResponse::selected).count(),
						topicResponses));
			}
		}

		return new TopicsResponse(domainResponses, totals(domainResponses));
	}

	@Transactional
	public TopicSummaryResponse createTopic(CreateTopicRequest request) {
		Domain domain = domainRepository.findById(request.domainId())
				.orElseThrow(() -> new ResourceNotFoundException("Domain not found."));
		String title = trimRequired(request.title(), "title");
		if (topicRepository.existsByDomainIdAndTitleIgnoreCase(domain.getId(), title)) {
			throw new IllegalArgumentException("Topic already exists in this domain.");
		}

		Topic topic = topicRepository.save(new Topic(
				domain,
				"manual-" + UUID.randomUUID(),
				title,
				TopicSource.MANUAL,
				true));
		initializePoints(topic);
		List<ReviewPoint> points = reviewPointRepository.findByTopicId(topic.getId());
		return toTopicSummary(topic, points);
	}

	@Transactional
	public TopicSummaryResponse updateSelection(UUID topicId, UpdateTopicSelectionRequest request) {
		Topic topic = requireTopic(topicId);
		topic.setSelected(request.selected());
		if (Boolean.TRUE.equals(request.selected())) {
			initializePoints(topic);
		}
		List<ReviewPoint> points = reviewPointRepository.findByTopicId(topic.getId());
		return toTopicSummary(topic, points);
	}

	@Transactional
	public TopicsResponse updateSelections(UpdateTopicSelectionsRequest request) {
		List<UUID> topicIds = request.topicIds()
				.stream()
				.collect(Collectors.collectingAndThen(
						Collectors.toCollection(LinkedHashSet::new),
						ArrayList::new));
		List<Topic> topics = topicRepository.findAllById(topicIds);
		if (topics.size() != topicIds.size()) {
			throw new ResourceNotFoundException("Topic not found.");
		}

		boolean selected = Boolean.TRUE.equals(request.selected());
		for (Topic topic : topics) {
			topic.setSelected(selected);
			if (selected) {
				initializePoints(topic);
			}
		}
		return listTopics(null);
	}

	@Transactional
	public TopicSummaryResponse updatePlanning(UUID topicId, UpdateTopicPlanningRequest request) {
		Topic topic = requireTopic(topicId);
		topic.updatePlanning(
				request.relevanceTier(),
				Boolean.TRUE.equals(request.planEnabled()),
				request.interviewValue(),
				request.newExpansionLimit());
		return toTopicSummary(topic, reviewPointRepository.findByTopicId(topic.getId()));
	}

	@Transactional
	public TopicSummaryResponse initializePoints(UUID topicId) {
		Topic topic = requireTopic(topicId);
		initializePoints(topic);
		List<ReviewPoint> points = reviewPointRepository.findByTopicId(topic.getId());
		return toTopicSummary(topic, points);
	}

	private Topic requireTopic(UUID topicId) {
		return topicRepository.findById(topicId)
				.orElseThrow(() -> new ResourceNotFoundException("Topic not found."));
	}

	private void initializePoints(Topic topic) {
		List<ReviewPoint> existingPoints = reviewPointRepository.findByTopicId(topic.getId());
		List<PointTemplate> templates = BUILTIN_TEMPLATES.get(topic.getCode());
		if (templates == null && !existingPoints.isEmpty()) {
			return;
		}
		if (templates == null) {
			templates = BUILTIN_DOMAIN_TEMPLATES.getOrDefault(topic.getDomain().getCode(), DEFAULT_TEMPLATES);
		}
		List<ReviewPoint> missingPoints = templates.stream()
				.filter(template -> existingPoints.stream()
						.noneMatch(point -> point.getTitle().equals(template.resolvedTitle(topic))))
				.map(template -> template.toReviewPoint(topic))
				.toList();
		if (missingPoints.isEmpty()) {
			return;
		}
		reviewPointRepository.saveAll(missingPoints);
	}

	private Map<UUID, List<ReviewPoint>> loadPointsByTopic(Collection<UUID> topicIds) {
		if (topicIds.isEmpty()) {
			return Map.of();
		}
		return reviewPointRepository.findByTopicIdIn(topicIds)
				.stream()
				.collect(Collectors.groupingBy(point -> point.getTopic().getId(), HashMap::new, Collectors.toList()));
	}

	private static TopicSummaryResponse toTopicSummary(Topic topic, List<ReviewPoint> points) {
		TopicAggregate aggregate = aggregate(points);
		return new TopicSummaryResponse(
				topic.getId(),
				topic.getDomain().getId(),
				topic.getDomain().getName(),
				topic.getCode(),
				topic.getTitle(),
				topic.getSource().name(),
				topic.isSelected(),
				topic.getRelevanceTier().name(),
				topic.isPlanEnabled(),
				topic.getInterviewValue(),
				topic.getNewExpansionLimit(),
				points.size(),
				aggregate.coveredCount(),
				aggregate.averageMastery(),
				aggregate.nextReviewAt(),
				aggregate.weakPointSummary());
	}

	private static TopicTotalsResponse totals(List<DomainTopicsResponse> domains) {
		List<TopicSummaryResponse> topics = domains.stream()
				.flatMap(domain -> domain.topics().stream())
				.toList();
		long reviewPointCount = topics.stream().mapToLong(TopicSummaryResponse::reviewPointCount).sum();
		BigDecimal averageMastery = reviewPointCount == 0
				? ZERO_MASTERY
				: topics.stream()
						.map(topic -> topic.averageMastery().multiply(BigDecimal.valueOf(topic.reviewPointCount())))
						.reduce(BigDecimal.ZERO, BigDecimal::add)
						.divide(BigDecimal.valueOf(reviewPointCount), 2, RoundingMode.HALF_UP);

		return new TopicTotalsResponse(
				domains.size(),
				topics.size(),
				topics.stream().filter(TopicSummaryResponse::selected).count(),
				reviewPointCount,
				averageMastery);
	}

	private static TopicAggregate aggregate(List<ReviewPoint> points) {
		if (points.isEmpty()) {
			return new TopicAggregate(0, ZERO_MASTERY, null, List.of());
		}

		BigDecimal averageMastery = points.stream()
				.map(ReviewPoint::getMastery)
				.reduce(BigDecimal.ZERO, BigDecimal::add)
				.divide(BigDecimal.valueOf(points.size()), 2, RoundingMode.HALF_UP);
		Instant nextReviewAt = points.stream()
				.map(ReviewPoint::getNextReviewAt)
				.filter(instant -> instant != null)
				.min(Comparator.naturalOrder())
				.orElse(null);
		List<String> weakPointSummary = points.stream()
				.flatMap(point -> point.getWeakPoints().stream())
				.distinct()
				.limit(3)
				.toList();

		return new TopicAggregate(
				points.stream().filter(point -> point.getStatus() != ReviewPointStatus.UNCOVERED).count(),
				averageMastery,
				nextReviewAt,
				weakPointSummary);
	}

	private static boolean matchesSearch(Topic topic, String normalizedSearch) {
		if (normalizedSearch == null) {
			return true;
		}
		return normalizeSearch(topic.getTitle()).contains(normalizedSearch)
				|| normalizeSearch(topic.getCode()).contains(normalizedSearch)
				|| normalizeSearch(topic.getDomain().getName()).contains(normalizedSearch);
	}

	private static String trimRequired(String value, String field) {
		if (value == null || value.trim().isEmpty()) {
			throw new IllegalArgumentException(field + " is required.");
		}
		return value.trim();
	}

	private static String normalizeSearch(String value) {
		if (value == null || value.trim().isEmpty()) {
			return null;
		}
		return Normalizer.normalize(value.trim().toLowerCase(Locale.ROOT), Normalizer.Form.NFKC);
	}

	private record TopicAggregate(
			long coveredCount,
			BigDecimal averageMastery,
			Instant nextReviewAt,
			List<String> weakPointSummary) {
	}

	private record PointTemplate(
			String title,
			int importance,
			int difficulty,
			int interviewFrequency,
			AutoPlanTier autoPlanTier) {

		PointTemplate(String title, int importance, int difficulty, int interviewFrequency) {
			this(title, importance, difficulty, interviewFrequency, inferredTier(title, importance, interviewFrequency));
		}

		static PointTemplate core(String title, int importance, int difficulty, int interviewFrequency) {
			return new PointTemplate(title, importance, difficulty, interviewFrequency, AutoPlanTier.CORE);
		}

		static PointTemplate expand(String title, int importance, int difficulty, int interviewFrequency) {
			return new PointTemplate(title, importance, difficulty, interviewFrequency, AutoPlanTier.EXPAND);
		}

		static PointTemplate optional(String title, int importance, int difficulty, int interviewFrequency) {
			return new PointTemplate(title, importance, difficulty, interviewFrequency, AutoPlanTier.OPTIONAL);
		}

		ReviewPoint toReviewPoint(Topic topic) {
			String resolvedTitle = resolvedTitle(topic);
			return new ReviewPoint(
					topic,
					resolvedTitle,
					importance,
					difficulty,
					interviewFrequency,
					autoPlanTier,
					"围绕「" + resolvedTitle + "」追问机制、边界和生产排查。");
		}

		String resolvedTitle(Topic topic) {
			return title.contains("%s") ? title.formatted(topic.getTitle()) : title;
		}

		private static AutoPlanTier inferredTier(String title, int importance, int interviewFrequency) {
			if (importance <= 3 || title.contains("面试表达闭环") || title.contains("两分钟表达结构")) {
				return AutoPlanTier.OPTIONAL;
			}
			if (importance >= 5 && interviewFrequency >= 5) {
				return AutoPlanTier.CORE;
			}
			return AutoPlanTier.EXPAND;
		}
	}
}
