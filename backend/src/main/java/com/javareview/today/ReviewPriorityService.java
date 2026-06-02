package com.javareview.today;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

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

	public BigDecimal forCarryOver(ReviewTask task, LocalDate today) {
		BigDecimal reviewPointScore = task.getReviewPoint() == null
				? BigDecimal.ZERO
				: forReviewPoint(task.getReviewPoint(), today, false);
		BigDecimal ageBonus = BigDecimal.valueOf(Math.max(1, ChronoUnit.DAYS.between(task.getTaskDate(), today)) * 3L);
		return reviewPointScore
				.add(CARRY_OVER_BONUS)
				.add(ageBonus)
				.setScale(2, RoundingMode.HALF_UP);
	}

	public BigDecimal forReviewPoint(ReviewPoint point, LocalDate today, boolean carryOver) {
		BigDecimal score = BigDecimal.ZERO;
		if (carryOver) {
			score = score.add(CARRY_OVER_BONUS);
		}
		score = score
				.add(overdueBonus(point, today))
				.add(BigDecimal.valueOf(point.getImportance() * 2L))
				.add(BigDecimal.valueOf(point.getInterviewFrequency() * 2L))
				.add(BigDecimal.valueOf(point.getDifficulty()))
				.add(decayRisk(point))
				.add(wrongBonus(point))
				.subtract(point.getMastery());
		return score.setScale(2, RoundingMode.HALF_UP);
	}

	private BigDecimal overdueBonus(ReviewPoint point, LocalDate today) {
		if (point.getNextReviewAt() == null) {
			return BigDecimal.ZERO;
		}
		LocalDate dueDate = point.getNextReviewAt().atZone(clock.getZone()).toLocalDate();
		long overdueDays = Math.max(0, ChronoUnit.DAYS.between(dueDate, today));
		return BigDecimal.valueOf(overdueDays * 2L);
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
}
