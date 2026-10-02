package com.erudit.content;

import java.util.List;

public interface QuizCardGenerator {
    List<QuizCard> generate(Content content);
}
