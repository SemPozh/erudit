package com.erudit.rating;
import com.erudit.openapi.api.RatingsApi;import com.erudit.openapi.model.*;import com.erudit.web.UnauthorizedException;import jakarta.servlet.http.HttpServletRequest;import org.springframework.http.*;import org.springframework.web.bind.annotation.RestController;import java.util.UUID;
@RestController public class RatingController implements RatingsApi {
 private final RatingQueryService service;private final HttpServletRequest request;public RatingController(RatingQueryService service,HttpServletRequest request){this.service=service;this.request=request;}
 public ResponseEntity<RatingListResponse> getRating(Integer page,Integer size,UUID categoryId,String period){return ResponseEntity.ok(response(service.list(user(),page,size,categoryId,period,false)));}
 public ResponseEntity<RatingListResponse> getFriendsRating(Integer page,Integer size){return ResponseEntity.ok(response(service.list(user(),page,size,null,"ALL_TIME",true)));}
 public ResponseEntity<RatingEntryResponse> getMyRating(){return ResponseEntity.ok(new RatingEntryResponse(model(service.mine(user()))));}
 private RatingListResponse response(RatingPage p){var r=new RatingListResponse(p.rows().stream().map(this::model).toList());var m=new PageMetadata();m.setPage(p.page());m.setSize(p.size());m.setTotalElements(p.total());m.setTotalPages((int)Math.ceil(p.total()/(double)p.size()));r.setPagination(m);return r;}
 private RatingEntry model(RatingRow row){var e=new RatingEntry();e.setUserId(UUID.fromString(row.userId()));e.setScore((double)row.score());e.setRank(row.rank());e.setGrade(row.grade());return e;}
 private String user(){if(request.getUserPrincipal()==null)throw new UnauthorizedException("Authentication is required");return request.getUserPrincipal().getName();}
}
