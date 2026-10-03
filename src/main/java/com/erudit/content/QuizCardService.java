package com.erudit.content;

import com.erudit.web.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class QuizCardService {
    private final ContentRepository contentRepository;
    private final QuizCardRepository cardRepository;
    private final QuizCardGenerator generator;

    public QuizCardService(ContentRepository contentRepository, QuizCardRepository cardRepository,
                           QuizCardGenerator generator) {
        this.contentRepository = contentRepository;
        this.cardRepository = cardRepository;
        this.generator = generator;
    }

    @Transactional
    public List<QuizCard> regenerate(Content content) {
        List<QuizCard> cards = generator.generate(content);
        cardRepository.replaceForContent(content.id(), cards);
        return cards;
    }

    @Transactional
    public List<QuizCard> getForContent(UUID contentId, boolean admin) {
        Content content = contentRepository.findById(contentId)
                .orElseThrow(() -> new NotFoundException("Content not found"));
        if (!admin && content.status() != ContentStatus.PUBLISHED) {
            throw new NotFoundException("Content not found");
        }

        List<QuizCard> existing = cardRepository.findByContentId(contentId);
        if (!existing.isEmpty()) {
            return existing;
        }
        return regenerate(content);
    }
}