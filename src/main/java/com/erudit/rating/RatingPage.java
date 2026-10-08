package com.erudit.rating;
import java.util.List;
public record RatingPage(List<RatingRow> rows,long total,int page,int size) {}
