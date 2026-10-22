package com.erudit.assessment.service;

import lombok.RequiredArgsConstructor;
import com.erudit.assessment.model.Grade;
import com.erudit.assessment.repository.GradeRepository;

import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class GradeService {
    private final GradeRepository repository;



    public Grade resolve(int erScore) {
        if (erScore < 0 || erScore > ErScoreCalculator.MAX_SCORE) {
            throw new IllegalArgumentException("ER-score must be between 0 and 2000");
        }
        return repository.findByScore(erScore)
                .orElseThrow(() -> new IllegalStateException("Grade thresholds do not cover ER-score " + erScore));
    }
}
