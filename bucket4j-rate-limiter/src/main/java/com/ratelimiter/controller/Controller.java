package com.ratelimiter.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class Controller {
	
	/** URI /events?id=?
	 * 
	 * @param id
	 * @return
	 */
	@GetMapping("/events")
	public ResponseEntity<Object> getEvents(@RequestParam String id) {
		return ResponseEntity.ok().build();
	}	
}
