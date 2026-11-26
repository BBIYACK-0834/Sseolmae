package com.sseolmae.experience;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/experiences")
public class ExperienceController {

    public record Experience(String id, String title, String description) {}

    @GetMapping
    public List<Experience> findAll() {
        return List.of(
            new Experience("media-art", "미디어아트", "빛과 영상, 소리가 만드는 실내 전시."),
            new Experience("snow-sled", "눈썰매장", "눈 위를 달리는 겨울 야외 체험.")
        );
    }
}
