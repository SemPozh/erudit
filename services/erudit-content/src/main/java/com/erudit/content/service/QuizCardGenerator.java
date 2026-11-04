package com.erudit.content.service;
import com.erudit.content.model.ContentItem;
import com.erudit.content.model.QuizCard;
import java.util.List;
public interface QuizCardGenerator { List<QuizCard> generate(ContentItem item); }
