package com.splitmanager.api.controller;

import com.splitmanager.api.dto.FriendsResponse;
import com.splitmanager.api.service.SplitwiseService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class FriendsController {

  private final SplitwiseService splitwiseService;

  public FriendsController(SplitwiseService splitwiseService) {
    this.splitwiseService = splitwiseService;
  }

  @GetMapping("/friends")
  public ResponseEntity<FriendsResponse> getFriends() {
    var result = splitwiseService.getFriendsAndGroups();
    return ResponseEntity.ok(new FriendsResponse(result.friends(), result.groups()));
  }
}
