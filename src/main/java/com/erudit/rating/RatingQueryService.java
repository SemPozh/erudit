package com.erudit.rating;
import com.erudit.web.*;
import org.springframework.stereotype.Service;
import java.time.*;import java.util.*;
@Service public class RatingQueryService {
 private final RatingRepository repository; private final Clock clock;
 public RatingQueryService(RatingRepository repository,Clock clock){this.repository=repository;this.clock=clock;}
 public RatingPage list(String viewer,Integer page,Integer size,UUID category,String period,boolean friends){int p=page==null?0:page,s=size==null?20:size;if(p<0||s<1||s>50)throw new ValidationException("Invalid page");Instant since=switch(period==null?"ALL_TIME":period){case "DAY"->clock.instant().minus(Duration.ofDays(1));case "WEEK"->clock.instant().minus(Duration.ofDays(7));case "MONTH"->clock.instant().minus(Duration.ofDays(30));case "ALL_TIME"->null;default->throw new ValidationException("Unknown rating period");};var rows=repository.rating(category,since,viewer,friends,p,s);return new RatingPage(rows,repository.ratingCount(category,since,viewer,friends),p,s);}
 public RatingRow mine(String user){return repository.rating(null,null,user,false,0,Integer.MAX_VALUE).stream().filter(r->r.userId().equals(user)).findFirst().orElseThrow(()->new NotFoundException("Rating not found"));}
}
