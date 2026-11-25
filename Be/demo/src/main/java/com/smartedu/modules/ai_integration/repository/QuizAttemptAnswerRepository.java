package com.smartedu.modules.ai_integration.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.smartedu.modules.ai_integration.entity.QuizAttemptAnswer;

public interface QuizAttemptAnswerRepository extends JpaRepository<QuizAttemptAnswer, Long> {
    List<QuizAttemptAnswer> findByQuizAttemptId(Long quizAttemptId);

    @Query("""
            SELECT a FROM QuizAttemptAnswer a
            JOIN FETCH a.quizAttempt att
            JOIN FETCH att.quizTest qt
            JOIN FETCH qt.questionSet qs
            JOIN FETCH a.quizTestQuestion qtq
            JOIN FETCH qtq.question q
            WHERE att.user.id = :userId
              AND att.attemptStatus = 'SUBMITTED'
              AND (a.isCorrect = false OR a.isCorrect IS NULL)
              AND (:quizTestId IS NULL OR qt.id = :quizTestId)
              AND (:questionSetId IS NULL OR qs.id = :questionSetId)
              AND (:attemptId IS NULL OR att.id = :attemptId)
            ORDER BY att.submittedAt DESC, qtq.displayOrder ASC
            """)
    List<QuizAttemptAnswer> findWrongAnswersByUser(
            @Param("userId") Long userId,
            @Param("quizTestId") Long quizTestId,
            @Param("questionSetId") Long questionSetId,
            @Param("attemptId") Long attemptId);
}
