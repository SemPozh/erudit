package com.erudit.content.repository;

import com.erudit.content.model.QuizCard;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public class QuizCardRepository {
    private final JdbcTemplate jdbc;

    public QuizCardRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void replaceForContent(UUID contentId, List<QuizCard> cards) {
        jdbc.update("DELETE FROM quiz_cards WHERE content_id = ?", contentId);
        for (QuizCard card : cards) {
            jdbc.update("""
                    INSERT INTO quiz_cards (id, content_id, position, fact, question, correct_answer)
                    VALUES (?, ?, ?, ?, ?, ?)
                    """, card.id(), card.contentId(), card.position(), card.fact(),
                    card.question(), card.correctAnswer());
            for (int index = 0; index < card.answers().size(); index++) {
                jdbc.update("INSERT INTO quiz_card_answers (quiz_card_id, position, answer) VALUES (?, ?, ?)",
                        card.id(), index, card.answers().get(index));
            }
        }
    }

    public List<QuizCard> findByContentId(UUID contentId) {
        return jdbc.query("SELECT * FROM quiz_cards WHERE content_id = ? ORDER BY position", (rs, row) -> {
            UUID id = rs.getObject("id", UUID.class);
            List<String> answers = jdbc.queryForList(
                    "SELECT answer FROM quiz_card_answers WHERE quiz_card_id = ? ORDER BY position",
                    String.class, id);
            return new QuizCard(id, contentId, rs.getInt("position"), rs.getString("fact"),
                    rs.getString("question"), rs.getString("correct_answer"), answers);
        }, contentId);
    }
}
