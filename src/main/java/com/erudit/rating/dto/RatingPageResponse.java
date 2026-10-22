package com.erudit.rating.dto;

import com.erudit.rating.model.RatingRow;
import java.util.List;
public record RatingPageResponse(List<RatingRow> rows,long total,int page,int size) {}
