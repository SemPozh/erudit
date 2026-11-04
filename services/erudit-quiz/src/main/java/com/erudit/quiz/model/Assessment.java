package com.erudit.quiz.model;
import java.util.List;
import java.util.UUID;
public final class Assessment {
 private Assessment() {}
 public record Question(UUID id,String prompt,List<String> options,int correctIndex) {}
 public record Session(UUID id,UUID userId,String status,Integer score,String grade) {}
 public record Answer(UUID questionId,int optionIndex) {}
 public record Program(UUID userId,String grade,int progress) {}
}
