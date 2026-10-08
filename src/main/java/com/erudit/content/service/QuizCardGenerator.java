package com.erudit.content.service;

import com.erudit.content.model.Content;
import com.erudit.content.model.QuizCard;

import java.util.List;

public interface QuizCardGenerator {
    List<QuizCard> generate(Content content);
}
