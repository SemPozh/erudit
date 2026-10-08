package com.erudit.rating.dto;

import com.erudit.rating.model.RatingRow;
import java.util.List;
public record RatingPage(List<RatingRow> rows,long total,int page,int size) {}
