package tn.cityvoice.personnelservice.feign;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import java.util.Map;
import java.util.UUID;

@FeignClient(name = "user-service")
public interface UserFeignClient {

    @GetMapping("/api/users/{id}/public")
    Map<String, Object> getUserById(@PathVariable("id") UUID id);
}