package com.mysite.travelo.hyo.place;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import com.mysite.travelo.yeon.user.SiteUser;
import com.mysite.travelo.yeon.user.UserService;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/* *
 * 장소 컨트롤러
 * list : 장소 전체 리스트를 가져오는 기능
 * url 형식 : /place/list?item=&page=&keyword=&sorts=&content=&area=
 * 기본값
 * : item (페이지 당 아이템 수) = 15
 * : page (페이지 번호, 0부터 시작) = 0
 * : keyword (검색어, 장소명 한정) = ""
 * : sorts (정렬, popular만 가능) = ""
 * : content (장소 유형) = ""
 * : area (지역 코드) = ""
 *
 * distance : 거리 기준으로 장소 리스트를 가져오는 기능
 * url 형식 : /place/distance?item=&page=&keyword=&contentId=&distance=&content=
 * 기본값
 * : item (페이지 당 아이템 수) = 15
 * : page (페이지 번호, 0부터 시작) = 0
 * : keyword (검색어, 장소명 한정) = ""
 * : contentId (콘텐츠 번호, 각 장소당 하나 씩 할당) : 2733967 (가회동 성당)
 * : distance (반경 거리, km 기준. 반지름임.) : 20.0
 * : content (장소 유형) : ""
 *
 * increaseLike, decreaseLike : 좋아요 증가 감소 기능
 * : 상태 관리는 프론트에서 해야함.
 *
 * viewCount : 조회수 기능
 * : 계정 당 1회는 실패함. 새로고침하면 계속 늘어요
 *
 */

@RestController
@RequiredArgsConstructor
public class PlaceController {

	private final UserService userService;
	private final PlaceService placeService;
    private final PlaceRepository placeRepository;

    // 기본 장소 리스트를 가져오는 기능
    // url 형식 : /place/list?page=&keyword=&sorts=&content=&area=
    @GetMapping("/travelo/place/list")
    public ResponseEntity<Map<String, Object>> list(
            @RequestParam(value = "item", defaultValue = "15") int item_in_page,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "keyword", defaultValue = "") String keyword,
            @RequestParam(value = "sorts", defaultValue = "") String sort,
            @RequestParam(value = "content", defaultValue = "") String content,
            @RequestParam(value = "area", defaultValue = "") String area,
            Authentication auth
    ) {
    	
        List<String> content_list = List.of();
        List<String> area_list = List.of();

        if (!content.isEmpty() && content != null) {
            content_list = Arrays.asList(content.split(","));
        }
        if (!area.isEmpty() && area != null) {
            area_list = Arrays.asList(area.split(","));
        }

        Page<Place> paging = this.placeService.getPage(item_in_page, page, keyword, sort, content_list, area_list);

        Map<String, Object> response = new HashMap<>();

        if (auth != null) {
            SiteUser loginUser = userService.getLoginUserByUsername(auth.getName());
            response.put("loginUser", loginUser);
        }

        response.put("item_in_page", item_in_page);
        response.put("paging", paging);
        response.put("keyword", keyword);
        response.put("sort", sort);
        response.put("content", content_list);
        response.put("areas", area_list);

        return ResponseEntity.ok(response);
    }

    // 거리 기반으로 장소 리스트를 가져오는 기능 - 무한 스크롤 방식
    // url 형식 : /place/distance?keyword=&contentId=&distance=&content=
    @GetMapping("/travelo/place/distance")
    public ResponseEntity<Map<String, Object>> distance(
            @RequestParam(value = "keyword", defaultValue = "") String keyword,
            @RequestParam(value = "contentId", defaultValue = "2733967") String contentId,
            @RequestParam(value = "distance", defaultValue = "20.0") double distance,
            @RequestParam(value = "content", defaultValue = "") String content,
            Authentication auth
            )
    {

        Place place = placeService.getPlaceByContentId(contentId);

        double longitude = 126.984662;
        double latitude = 37.582086;

        Map<String, Object> response = new HashMap<>();
        if (place != null) {
            longitude = place.getLongitude();
            latitude = place.getLatitude();
        } else {
            response.put("error", "Place not found");
            return ResponseEntity.badRequest().body(response);
        }

        List<String> content_list = List.of();

        if (!content.isEmpty()) {
            content_list = Arrays.asList(content.split(","));
        }

        SiteUser loginUser = userService.getLoginUserByUsername(auth.getName());
        
        if (loginUser == null) {
        	response.put("loginUser", loginUser);
        }
        
        List<Place> unpage = this.placeService.getPage(keyword, latitude, longitude, distance, content_list);
        response.put("keyword", keyword);
        response.put("unpage", unpage);
        response.put("longitude", longitude);
        response.put("latitude", latitude);
        response.put("distance", distance);
        response.put("content", content_list);

        return ResponseEntity.ok(response);
    }

    // 거리 기반으로 장소 리스트를 가져오는 기능 - 페이지 값 존재
    // url 형식 : /place/distance?page=&keyword=&contentId=&distance=&content=
    @GetMapping("/travelo/place/distancep")
    public ResponseEntity<Map<String, Object>> distance(
            @RequestParam(value = "item", defaultValue = "15") int item_in_page,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "keyword", defaultValue = "") String keyword,
            @RequestParam(value = "contentId", defaultValue = "2733967") String contentId,
            @RequestParam(value = "distance", defaultValue = "20.0") double distance,
            @RequestParam(value = "content", defaultValue = "") String content,
            Authentication auth
    		)
    {

        Place place = placeService.getPlaceByContentId(contentId);

        double longitude = 126.984662;
        double latitude = 37.582086;

        Map<String, Object> response = new HashMap<>();
        
        SiteUser loginUser = userService.getLoginUserByUsername(auth.getName());
        if (loginUser == null) {
        	response.put("loginUser", loginUser);
        }
        
        if (place != null) {
            longitude = place.getLongitude();
            latitude = place.getLatitude();
        } else {
            response.put("error", "Place not found");
            return ResponseEntity.badRequest().body(response);
        }

        List<String> content_list = List.of();

        if (!content.isEmpty()) {
            content_list = Arrays.asList(content.split(","));
        }

        Page<Place> paging = this.placeService.getPage(item_in_page, page, keyword, latitude, longitude, distance, content_list);
        response.put("item_in_page", item_in_page);
        response.put("paging", paging);
        response.put("keyword", keyword);
        response.put("longitude", longitude);
        response.put("latitude", latitude);
        response.put("distance", distance);
        response.put("content", content_list);

        return ResponseEntity.ok(response);
    }
    
//	   좋아요 상태 전환
    @PreAuthorize("isAuthenticated()")
    @PostMapping("/user/place/like/{placeSeq}")
    public ResponseEntity<String> toggleLike(@PathVariable("placeSeq") Integer placeSeq,
                                          Authentication auth) {
	 	
	     SiteUser loginUser = userService.getLoginUserByUsername(auth.getName());
	     
	     String likeYn = placeService.togglePlaceLike(placeSeq, loginUser);
	     
	     return new ResponseEntity<>(likeYn, HttpStatus.OK);
 }

    // 조회수
    @PostMapping("/travelo/place/detail/{placeSeq}")
    public ResponseEntity<Place> viewCount(@PathVariable("placeSeq") int placeSeq) {
        Place place = placeService.increaseViewCount(placeSeq);
        return ResponseEntity.ok(place);
    }

    @PostMapping("travelo/place/contentIdCall")
    public int contentIdCall(@RequestBody Map<String, Object> placeSeq) {
        int seq = (int) placeSeq.get("placeSeq");
        int contentId = Integer.parseInt(placeRepository.findById(seq).getContentId());

        return contentId;
    }

}
