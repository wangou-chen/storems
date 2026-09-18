package com.storems.userservice.controller;

import com.storems.userservice.dto.RegisterRequest;
import com.storems.userservice.service.AdminUserService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/user/admin/users")
public class AdminUserController {
    private final AdminUserService service;
    public AdminUserController(AdminUserService service) { this.service = service; }
    @GetMapping
    public List<Map<String, Object>> list(@RequestHeader(value="Authorization", required=false) String auth) {
        return service.list(auth);
    }
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Long> create(@RequestHeader(value="Authorization", required=false) String auth,
                                   @RequestBody RegisterRequest request) {
        return Collections.singletonMap("userId", service.create(auth, request));
    }
    @PutMapping("/{id}/enabled")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void enabled(@RequestHeader(value="Authorization", required=false) String auth,
                        @PathVariable("id") Long id, @RequestBody StatusRequest request) {
        service.setEnabled(auth, id, request.getEnabled());
    }
    @PutMapping("/{id}/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void password(@RequestHeader(value="Authorization", required=false) String auth,
                         @PathVariable("id") Long id, @RequestBody RegisterRequest request) {
        service.resetPassword(auth, id, request.getPassword());
    }
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@RequestHeader(value="Authorization", required=false) String auth,
                       @PathVariable("id") Long id) {
        service.delete(auth, id);
    }

    public static class StatusRequest {
        private Boolean enabled;
        public Boolean getEnabled() { return enabled; }
        public void setEnabled(Boolean enabled) { this.enabled = enabled; }
    }
}
