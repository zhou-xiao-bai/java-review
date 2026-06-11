package com.javareview.today;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

import com.javareview.reviewpoint.ReviewPoint;
import com.javareview.reviewpoint.ReviewPointStatus;

@Service
public class ReviewPriorityService {

	private static final BigDecimal CARRY_OVER_BONUS = BigDecimal.valueOf(50);
	private final java.time.Clock clock;

	public ReviewPriorityService(java.time.Clock clock) {
		this.clock = clock;
	}

	public BigDecimal forReviewPoint(ReviewPoint point, LocalDate today, boolean carryOver) {
		return explainReviewPoint(point, today, carryOver).totalScore();
	}

	public PriorityBreakdown explainReviewPoint(ReviewPoint point, LocalDate today, boolean carryOver) {
		List<PriorityFactor> factors = new ArrayList<>();
		if (carryOver) {
			factors.add(new PriorityFactor(
					"carry_over",
					"顺延未完成",
					"是",
					CARRY_OVER_BONUS,
					"未完成任务会优先排回今日计划。"));
		}
		factors.add(new PriorityFactor(
				"overdue",
				"逾期天数",
				overdueDaysLabel(point, today),
				overdueBonus(point, today),
				"超过应复习日期后，每逾期 1 天提高排序权重。"));
		factors.add(new PriorityFactor(
				"topic_interview_value",
				"主题面试价值",
				point.getTopic().getInterviewValue() + "/5",
				BigDecimal.valueOf(point.getTopic().getInterviewValue() * 3L),
				"主题越贴近面试高频场景，越优先安排。"));
		factors.add(new PriorityFactor(
				"importance",
				"知识点重要度",
				point.getImportance() + "/5",
				BigDecimal.valueOf(point.getImportance() * 2L),
				"核心知识点会比边缘知识点更靠前。"));
		factors.add(new PriorityFactor(
				"interview_frequency",
				"面试出现频率",
				point.getInterviewFrequency() + "/5",
				BigDecimal.valueOf(point.getInterviewFrequency() * 2L),
				"越常被问到，越优先复验。"));
		factors.add(new PriorityFactor(
				"difficulty",
				"知识点困难度",
				point.getDifficulty() + "/5",
				BigDecimal.valueOf(point.getDifficulty()),
				"越难遗忘和混淆风险越高。"));
		factors.add(new PriorityFactor(
				"decay_risk",
				"掌握状态风险",
				statusLabel(point.getStatus()),
				decayRisk(point),
				"掌握越不稳定，越需要更早复验。"));
		factors.add(new PriorityFactor(
				"wrong_count",
				"历史出错次数",
				String.valueOf(point.getWrongCount()),
				wrongBonus(point),
				"错误次数会提高复习优先级，最高加 10。"));
		factors.add(new PriorityFactor(
				"mastery",
				"当前掌握度",
				point.getMastery().setScale(2, RoundingMode.HALF_UP).toPlainString() + "/5",
				point.getMastery().negate(),
				"掌握度越高，排序权重越低。"));

		BigDecimal score = factors.stream()
				.map(PriorityFactor::contribution)
				.reduce(BigDecimal.ZERO, BigDecimal::add)
				.setScale(2, RoundingMode.HALF_UP);
		return new PriorityBreakdown(score, factors);
	}

	public record PriorityBreakdown(BigDecimal totalScore, List<PriorityFactor> factors) {
	}

	public record PriorityFactor(
			String key,
			String label,
			String value,
			BigDecimal contribution,
			String description) {
	}

	private BigDecimal overdueBonus(ReviewPoint point, LocalDate today) {
		if (point.getNextReviewAt() == null) {
			return BigDecimal.ZERO;
		}
		long overdueDays = overdueDays(point, today);
		return BigDecimal.valueOf(overdueDays * 2L);
	}

	private String overdueDaysLabel(ReviewPoint point, LocalDate today) {
		return overdueDays(point, today) + " 天";
	}

	private long overdueDays(ReviewPoint point, LocalDate today) {
		if (point.getNextReviewAt() == null) {
			return 0;
		}
		LocalDate dueDate = point.getNextReviewAt().atZone(clock.getZone()).toLocalDate();
		return Math.max(0, ChronoUnit.DAYS.between(dueDate, today));
	}

	private static BigDecimal decayRisk(ReviewPoint point) {
		return switch (point.getStatus()) {
			case UNSTABLE -> BigDecimal.valueOf(8);
			case DUE -> BigDecimal.valueOf(6);
			case FIRST_PASS -> BigDecimal.valueOf(4);
			case STABLE -> BigDecimal.valueOf(1);
			case LONG_TERM -> BigDecimal.ZERO;
			case UNCOVERED -> BigDecimal.valueOf(5);
		};
	}

	private static BigDecimal wrongBonus(ReviewPoint point) {
		return BigDecimal.valueOf(Math.min(10, point.getWrongCount() * 2L));
	}

	private static String statusLabel(ReviewPointStatus status) {
		return switch (status) {
			case UNSTABLE -> "掌握不稳定";
			case DUE -> "需要按期复验";
			case FIRST_PASS -> "初步掌握";
			case STABLE -> "掌握稳定";
			case LONG_TERM -> "长期掌握";
			case UNCOVERED -> "未覆盖";
		};
	}
}
