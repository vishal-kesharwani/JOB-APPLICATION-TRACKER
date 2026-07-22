package com.jobtracker.notification.controller;

import com.jobtracker.notification.dto.ReminderResponse;
import com.jobtracker.notification.dto.ReminderSummaryResponse;
import com.jobtracker.notification.service.ReminderService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/reminders")
@RequiredArgsConstructor
public class ReminderController {

    private final ReminderService reminderService;

    @GetMapping
    public List<ReminderResponse> getAllReminders() {
        return reminderService.listReminders().stream()
                .map(ReminderResponse::from)
                .toList();
    }

    @GetMapping("/summary")
    public ReminderSummaryResponse getSummary() {
        return ReminderSummaryResponse.from(reminderService.listReminders());
    }
}
