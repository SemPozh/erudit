package com.erudit.quiz.application.port;

import java.util.List;

public interface QuizGeneration {
    Generated generate(String title, String prompt);
    record Generated(String question, List<String> options, int correctOption) {}
}
