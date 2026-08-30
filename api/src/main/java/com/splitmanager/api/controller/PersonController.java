package com.splitmanager.api.controller;

import com.splitmanager.api.config.CurrentUser;
import com.splitmanager.api.dto.CreatePersonRequest;
import com.splitmanager.api.dto.PersonDto;
import com.splitmanager.api.dto.RenamePersonRequest;
import com.splitmanager.api.service.PersonService;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The saved people directory (BRD FR13). */
@RestController
@RequestMapping("/people")
public class PersonController {

  private final PersonService personService;
  private final CurrentUser currentUser;

  public PersonController(PersonService personService, CurrentUser currentUser) {
    this.personService = personService;
    this.currentUser = currentUser;
  }

  @GetMapping
  public ResponseEntity<List<PersonDto>> list() {
    return ResponseEntity.ok(
        personService.list(currentUser.userId()).stream().map(PersonDto::from).toList());
  }

  @PostMapping
  public ResponseEntity<PersonDto> create(@RequestBody CreatePersonRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(PersonDto.from(personService.create(currentUser.userId(), request.displayName())));
  }

  @PatchMapping("/{id}")
  public ResponseEntity<PersonDto> rename(
      @PathVariable String id, @RequestBody RenamePersonRequest request) {
    return ResponseEntity.ok(
        PersonDto.from(personService.rename(currentUser.userId(), id, request.displayName())));
  }

  /** Archives rather than hard-deletes, so historical transactions still resolve names. */
  @DeleteMapping("/{id}")
  public ResponseEntity<Void> archive(@PathVariable String id) {
    personService.archive(currentUser.userId(), id);
    return ResponseEntity.noContent().build();
  }
}
